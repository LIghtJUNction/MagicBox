// MagicBox adapter around the exact Proxylink revision used by MagicNet.
// Input and output are private files; no subscription content reaches logs/argv.
package main

import (
 "encoding/json"
 "fmt"
 "io"
 "os"
 "strings"
 "unicode/utf8"
 "proxylink/pkg/generator"
 "proxylink/pkg/parser"
 "proxylink/pkg/subscription"
)

const maxBytes = 8 << 20

type report struct {
 Schema int `json:"schema"`
 Total int `json:"total"`
 Failed int `json:"failed"`
 Outbounds []json.RawMessage `json:"outbounds"`
}

func convert(content []byte) (report, error) {
 result, err := subscription.NewConverterFull(false, false).ConvertContent(string(content))
 var document struct { Outbounds []struct { Protocol string `json:"protocol"` } `json:"outbounds"` }
 if json.Unmarshal(content, &document) == nil && len(document.Outbounds) > 0 && document.Outbounds[0].Protocol != "" {
  profiles, parseErr := parser.ParseXrayConfig(content)
  if parseErr != nil { return report{}, fmt.Errorf("unsupported xray input") }
  total := 0
  for _, node := range document.Outbounds {
   if node.Protocol != "freedom" && node.Protocol != "blackhole" && node.Protocol != "dns" { total++ }
  }
  result = &subscription.ConvertResult{Profiles: profiles, Total: total, Success: len(profiles), Failed: total-len(profiles)}
  err = nil
 }
 if err != nil || result == nil || result.Success == 0 { return report{}, fmt.Errorf("no supported nodes") }
 output, err := generator.GenerateSingboxOutbounds(result.Profiles)
 if err != nil { return report{}, fmt.Errorf("conversion failed") }
 var payload struct { Outbounds []json.RawMessage `json:"outbounds"` }
 if json.Unmarshal([]byte(output), &payload) != nil || len(payload.Outbounds) == 0 { return report{}, fmt.Errorf("no executable nodes") }
 failed := result.Total - len(payload.Outbounds)
 if failed < result.Failed { failed = result.Failed }
 if failed < 0 { failed = 0 }
 return report{1, result.Total, failed, payload.Outbounds}, nil
}

func run() error {
 if len(os.Args) != 3 { return fmt.Errorf("usage: converter <private-input-file> <private-output-file>") }
 file, err := os.Open(os.Args[1]); if err != nil { return fmt.Errorf("input unavailable") }; defer file.Close()
 raw, err := io.ReadAll(io.LimitReader(file, maxBytes+1))
 if err != nil || len(raw) > maxBytes || !utf8.Valid(raw) || strings.ContainsRune(string(raw), 0) { return fmt.Errorf("invalid or oversized input") }
 result, err := convert(raw); if err != nil { return err }
 out, err := os.OpenFile(os.Args[2], os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
 if err != nil { return fmt.Errorf("output unavailable") }
 defer out.Close()
 return json.NewEncoder(out).Encode(result)
}

func main() {
 // Third-party parsers must not dump a stack or an input-containing panic.
 defer func() { if recover() != nil { fmt.Fprintln(os.Stderr, "invalid subscription"); os.Exit(2) } }()
 if err := run(); err != nil { fmt.Fprintln(os.Stderr, err); os.Exit(2) }
}
