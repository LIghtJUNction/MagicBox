// Built inside the pinned Proxylink module; no network requests or remote conversion.
package main

import (
 "bytes"
 "encoding/base64"
 "encoding/json"
 "errors"
 "fmt"
 "io"
 "os"
 "strings"
 "proxylink/pkg/generator"
 "proxylink/pkg/model"
 "proxylink/pkg/parser"
 "gopkg.in/yaml.v3"
)
const maxInput = 2 * 1024 * 1024
var invalid = errors.New("unsupported or incomplete subscription; previous profile must be retained")

func convert(input []byte, depth int) ([]byte,error) {
 if len(input)==0 || len(input)>maxInput || depth>2 || bytes.IndexByte(input,0)>=0 { return nil,invalid }
 input=bytes.TrimSpace(bytes.TrimPrefix(input,[]byte{0xef,0xbb,0xbf}))
 var object map[string]json.RawMessage
 if json.Unmarshal(input,&object)==nil && object!=nil {
  // Native sing-box documents bypass lossy model roundtrips. Import nodes only.
  var outs []map[string]json.RawMessage
  var endpoints []map[string]json.RawMessage
  if raw,ok:=object["outbounds"];ok { if json.Unmarshal(raw,&outs)!=nil{return nil,invalid} }
  if raw,ok:=object["endpoints"];ok { if json.Unmarshal(raw,&endpoints)!=nil{return nil,invalid} }
  if len(outs)==0 && len(endpoints)==0 && object["type"]!=nil && object["server"]!=nil { outs=[]map[string]json.RawMessage{object} }
  if len(outs)+len(endpoints)>0 {
   xray:=false
   for _,node:=range outs { if node["protocol"]!=nil {xray=true} }
   if !xray { return json.Marshal(map[string]any{"outbounds":outs,"endpoints":endpoints}) }
   profiles,err:=parser.ParseXrayConfig(input);if err!=nil{return nil,invalid}
   expected:=0
   for _,node:=range outs { var protocol string;_ = json.Unmarshal(node["protocol"],&protocol);if protocol!="freedom"&&protocol!="blackhole"&&protocol!="dns"{expected++} }
   if len(profiles)!=expected{return nil,invalid};return emit(profiles)
  }
  // Clash JSON is valid YAML and is handled below. Unrecognized JSON is not a URI.
  if object["proxies"]==nil{return nil,invalid}
 }
 var clash struct { Proxies []yaml.Node `yaml:"proxies"` }
 if yaml.Unmarshal(input,&clash)==nil && len(clash.Proxies)>0 {
  profiles,err:=parser.ParseClashConfig(input)
  if err!=nil || len(profiles)!=len(clash.Proxies){return nil,invalid}
  return emit(profiles)
 }
 text:=string(input)
 if strings.HasPrefix(text,"[Interface]") {
  p,err:=parser.ParseWireGuardConf(text);if err!=nil{return nil,invalid};return emit([]*model.ProfileItem{p})
 }
 if strings.Contains(text,"://") {
  var profiles []*model.ProfileItem
  for _,line:=range strings.Split(text,"\n") {
   line=strings.TrimSpace(line);if line==""||strings.HasPrefix(line,"#"){continue}
   colon:=strings.Index(line,"://");if colon<1{return nil,invalid}
   line=strings.ToLower(line[:colon])+line[colon:]
   p,err:=parser.Parse(line);if err!=nil{return nil,invalid};profiles=append(profiles,p)
  }
  return emit(profiles)
 }
 compact:=strings.Join(strings.Fields(text),"")
 for _,codec:=range []*base64.Encoding{base64.StdEncoding,base64.RawStdEncoding,base64.URLEncoding,base64.RawURLEncoding} {
  decoded,err:=codec.DecodeString(compact)
  if err==nil && len(decoded)>0 { if result,err:=convert(decoded,depth+1);err==nil{return result,nil} }
 }
 return nil,invalid
}
func emit(profiles []*model.ProfileItem)([]byte,error){
 if len(profiles)==0||len(profiles)>5000{return nil,invalid}
 value,err:=generator.GenerateSingboxOutbounds(profiles);if err!=nil{return nil,invalid}
 var doc struct{Outbounds []json.RawMessage `json:"outbounds"`}
 if json.Unmarshal([]byte(value),&doc)!=nil||len(doc.Outbounds)!=len(profiles){return nil,invalid}
 return []byte(value),nil
}
func main(){
 data,err:=io.ReadAll(io.LimitReader(os.Stdin,maxInput+1))
 if err==nil {var out []byte;out,err=convert(data,0);if err==nil{_,err=os.Stdout.Write(out)}}
 if err!=nil{fmt.Fprintln(os.Stderr,invalid);os.Exit(1)}
}
