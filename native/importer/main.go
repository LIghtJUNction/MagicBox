// Built inside the pinned Proxylink module. All conversion stays on-device.
package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"gopkg.in/yaml.v3"
	"io"
	"os"
	"proxylink/pkg/generator"
	"proxylink/pkg/model"
	"proxylink/pkg/parser"
	"strconv"
	"strings"
)

const maxInput = 2 * 1024 * 1024

var invalid = errors.New("unsupported or incomplete subscription; previous profile must be retained")

func convert(input []byte, depth int) ([]byte, error) {
	if len(input) == 0 || len(input) > maxInput || depth > 2 || bytes.IndexByte(input, 0) >= 0 {
		return nil, invalid
	}
	input = bytes.TrimSpace(bytes.TrimPrefix(input, []byte{0xef, 0xbb, 0xbf}))
	var object map[string]json.RawMessage
	if json.Unmarshal(input, &object) == nil && object != nil {
		var outs []map[string]json.RawMessage
		var endpoints []map[string]json.RawMessage
		if raw, ok := object["outbounds"]; ok {
			if json.Unmarshal(raw, &outs) != nil {
				return nil, invalid
			}
		}
		if raw, ok := object["endpoints"]; ok {
			if json.Unmarshal(raw, &endpoints) != nil {
				return nil, invalid
			}
		}
		if len(outs) == 0 && len(endpoints) == 0 && object["type"] != nil && object["server"] != nil {
			outs = []map[string]json.RawMessage{object}
		}
		if len(outs)+len(endpoints) > 0 {
			xray := false
			for _, node := range outs {
				if node["protocol"] != nil {
					xray = true
				}
			}
			if !xray {
				return json.Marshal(map[string]any{"outbounds": outs, "endpoints": endpoints})
			}
			profiles, err := parser.ParseXrayConfig(input)
			if err != nil {
				return nil, invalid
			}
			expected := 0
			for _, node := range outs {
				var protocol string
				_ = json.Unmarshal(node["protocol"], &protocol)
				if protocol != "freedom" && protocol != "blackhole" && protocol != "dns" {
					expected++
				}
			}
			if len(profiles) == 0 || len(profiles) > expected {
				return nil, invalid
			}
			return emitWithSkipped(profiles, expected-len(profiles))
		}
		if object["proxies"] == nil {
			return nil, invalid
		}
	}
	var clash struct {
		Proxies []yaml.Node `yaml:"proxies"`
	}
	decoder := yaml.NewDecoder(bytes.NewReader(input))
	if decoder.Decode(&clash) == nil && len(clash.Proxies) > 0 {
		var extra any
		if decoder.Decode(&extra) != io.EOF {
			return nil, invalid
		}
		profiles, err := parser.ParseClashConfig(input)
		if err != nil {
			return nil, invalid
		}
		// Proxylink intentionally skips protocols it does not model. Fill the
		// common Clash HTTP and WireGuard shapes locally instead of rejecting an
		// otherwise valid subscription. Remaining unknown nodes are counted and
		// surfaced by the Android layer; they are never silently claimed as parsed.
		for i := range clash.Proxies {
			var cp struct {
				Name           string `yaml:"name"`
				Type           string `yaml:"type"`
				Server         string `yaml:"server"`
				Port           int    `yaml:"port"`
				Username       string `yaml:"username"`
				Password       string `yaml:"password"`
				TLS            bool   `yaml:"tls"`
				SNI            string `yaml:"sni"`
				Servername     string `yaml:"servername"`
				SkipCertVerify bool   `yaml:"skip-cert-verify"`
				IP             string `yaml:"ip"`
				IPv6           string `yaml:"ipv6"`
				PrivateKey     string `yaml:"private-key"`
				PublicKey      string `yaml:"public-key"`
				PreSharedKey   string `yaml:"pre-shared-key"`
				MTU            int    `yaml:"mtu"`
				Reserved       []int  `yaml:"reserved"`
			}
			if clash.Proxies[i].Decode(&cp) != nil {
				continue
			}
			typ := strings.ToLower(strings.TrimSpace(cp.Type))
			switch typ {
			case "http", "https":
				p := model.NewProfileItem(model.HTTP)
				p.Remarks, p.Server, p.ServerPort = cp.Name, cp.Server, strconv.Itoa(cp.Port)
				p.Username, p.Password, p.Insecure = cp.Username, cp.Password, cp.SkipCertVerify
				if typ == "https" || cp.TLS {
					p.Security = "tls"
					p.SNI = cp.Servername
					if p.SNI == "" {
						p.SNI = cp.SNI
					}
				}
				profiles = append(profiles, p)
			case "wireguard":
				p := model.NewProfileItem(model.WIREGUARD)
				p.Remarks, p.Server, p.ServerPort = cp.Name, cp.Server, strconv.Itoa(cp.Port)
				p.SecretKey, p.PublicKey, p.PreSharedKey, p.MTU = cp.PrivateKey, cp.PublicKey, cp.PreSharedKey, cp.MTU
				p.LocalAddress = strings.Join(split(cp.IP+","+cp.IPv6), ",")
				if len(cp.Reserved) > 0 {
					parts := make([]string, 0, len(cp.Reserved))
					for _, value := range cp.Reserved {
						parts = append(parts, strconv.Itoa(value))
					}
					p.Reserved = strings.Join(parts, ",")
				}
				profiles = append(profiles, p)
			}
		}
		if len(profiles) == 0 || len(profiles) > len(clash.Proxies) {
			return nil, invalid
		}
		return emitWithSkipped(profiles, len(clash.Proxies)-len(profiles))
	}
	text := string(input)
	if strings.HasPrefix(strings.ToLower(text), "[interface]") {
		// A multi-peer config cannot be collapsed to one peer by the upstream parser.
		if strings.Count(strings.ToLower(text), "[peer]") != 1 {
			return nil, invalid
		}
		p, err := parser.ParseWireGuardConf(text)
		if err != nil {
			return nil, invalid
		}
		p.Remarks = "WireGuard"
		result, err := emit([]*model.ProfileItem{p})
		if err != nil {
			return nil, err
		}
		var document map[string]any
		_ = json.Unmarshal(result, &document)
		peer := document["endpoints"].([]any)[0].(map[string]any)["peers"].([]any)[0].(map[string]any)
		for _, line := range strings.Split(text, "\n") {
			key, value, ok := strings.Cut(line, "=")
			if !ok {
				continue
			}
			switch strings.ToLower(strings.TrimSpace(key)) {
			case "allowedips":
				peer["allowed_ips"] = split(value)
			case "persistentkeepalive":
				n, e := strconv.Atoi(strings.TrimSpace(value))
				if e != nil || n < 0 || n > 65535 {
					return nil, invalid
				}
				peer["persistent_keepalive_interval"] = n
			case "postup", "postdown", "preup", "predown":
				return nil, invalid
			}
		}
		return json.Marshal(document)
	}
	if strings.Contains(text, "://") {
		var profiles []*model.ProfileItem
		skipped := 0
		for _, line := range strings.Split(text, "\n") {
			line = strings.TrimSpace(line)
			if line == "" || strings.HasPrefix(line, "#") {
				continue
			}
			colon := strings.Index(line, "://")
			if colon < 1 {
				skipped++
				continue
			}
			line = strings.ToLower(line[:colon]) + line[colon:]
			p, err := parser.Parse(line)
			if err != nil {
				skipped++
				continue
			}
			profiles = append(profiles, p)
		}
		return emitWithSkipped(profiles, skipped)
	}
	compact := strings.Join(strings.Fields(text), "")
	for _, codec := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding, base64.URLEncoding, base64.RawURLEncoding} {
		decoded, err := codec.DecodeString(compact)
		if err == nil && len(decoded) > 0 {
			if result, err := convert(decoded, depth+1); err == nil {
				return result, nil
			}
		}
	}
	return nil, invalid
}
func split(value string) []string {
	var values []string
	for _, item := range strings.Split(value, ",") {
		if item = strings.TrimSpace(item); item != "" {
			values = append(values, item)
		}
	}
	return values
}
func emit(profiles []*model.ProfileItem) ([]byte, error) { return emitWithSkipped(profiles, 0) }
func emitWithSkipped(profiles []*model.ProfileItem, skipped int) ([]byte, error) {
	if len(profiles) == 0 || len(profiles) > 5000 || skipped < 0 {
		return nil, invalid
	}
	outs := make([]map[string]any, 0, len(profiles))
	endpoints := make([]map[string]any, 0)
	used := map[string]bool{}
	for index, p := range profiles {
		port, err := strconv.Atoi(p.ServerPort)
		if err != nil || port < 1 || port > 65535 || p.Server == "" {
			return nil, invalid
		}
		if p.Mldsa65Verify != "" || p.PinnedCA256 != "" || p.FinalMask != "" {
			return nil, invalid
		}
		switch p.Network {
		case "", "tcp", "udp", "ws", "grpc", "httpupgrade", "h2", "http":
		default:
			return nil, invalid
		}
		var node map[string]any
		switch p.ConfigType {
		case model.HTTP:
			node = map[string]any{"type": "http", "server": p.Server, "server_port": port}
			if p.Username != "" {
				node["username"] = p.Username
				node["password"] = p.Password
			}
			if p.Security == "tls" {
				node["tls"] = map[string]any{"enabled": true, "server_name": p.SNI, "insecure": p.Insecure}
			}
		case model.WIREGUARD:
			if p.SecretKey == "" || p.PublicKey == "" {
				return nil, invalid
			}
			peer := map[string]any{"address": strings.Trim(p.Server, "[]"), "port": port, "public_key": p.PublicKey, "allowed_ips": []string{"0.0.0.0/0", "::/0"}}
			if p.PreSharedKey != "" {
				peer["pre_shared_key"] = p.PreSharedKey
			}
			reserved := []int{}
			for _, part := range split(p.Reserved) {
				n, e := strconv.Atoi(part)
				if e != nil || n < 0 || n > 255 {
					return nil, invalid
				}
				reserved = append(reserved, n)
			}
			if len(reserved) > 0 {
				if len(reserved) != 3 {
					return nil, invalid
				}
				peer["reserved"] = reserved
			}
			node = map[string]any{"type": "wireguard", "system": false, "address": split(p.LocalAddress), "private_key": p.SecretKey, "peers": []any{peer}}
			if p.MTU > 0 {
				node["mtu"] = p.MTU
			}
		default:
			value, e := generator.GenerateSingboxOutbounds([]*model.ProfileItem{p})
			if e != nil {
				return nil, invalid
			}
			var document struct {
				Outbounds []map[string]any `json:"outbounds"`
			}
			if json.Unmarshal([]byte(value), &document) != nil || len(document.Outbounds) != 1 {
				return nil, invalid
			}
			node = document.Outbounds[0]
		}
		base := p.Remarks
		if base == "" {
			base = fmt.Sprintf("node_%d", index+1)
		}
		tag := base
		for suffix := 2; used[tag]; suffix++ {
			tag = fmt.Sprintf("%s_%d", base, suffix)
		}
		used[tag] = true
		node["tag"] = tag
		if p.ConfigType == model.WIREGUARD {
			endpoints = append(endpoints, node)
		} else {
			outs = append(outs, node)
		}
	}
	result := map[string]any{"outbounds": outs, "endpoints": endpoints}
	if skipped > 0 {
		result["_magicbox"] = map[string]any{"skipped": skipped}
	}
	return json.Marshal(result)
}
func main() {
	data, err := io.ReadAll(io.LimitReader(os.Stdin, maxInput+1))
	if err == nil {
		var out []byte
		out, err = convert(data, 0)
		if err == nil {
			_, err = os.Stdout.Write(out)
		}
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, invalid)
		os.Exit(1)
	}
}
