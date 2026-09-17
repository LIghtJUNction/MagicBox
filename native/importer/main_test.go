package main
import("bytes";"encoding/base64";"encoding/json";"testing")
func TestFormats(t *testing.T){
 uri:="trojan://secret@example.com:443?sni=example.com#test"
 cases:=map[string]string{
  "share-link":uri,
  "base64":base64.StdEncoding.EncodeToString([]byte(uri)),
  "base64url":base64.RawURLEncoding.EncodeToString([]byte(uri)),
  "clash":"proxies:\n  - {name: test, type: trojan, server: example.com, port: 443, password: secret}",
  "singbox":`{"outbounds":[{"type":"trojan","tag":"test","server":"example.com","server_port":443,"password":"secret","tls":{"enabled":true}}]}`,
  "native-node":`{"type":"socks","tag":"test","server":"example.com","server_port":1080}`,
  "socks":"socks5://user:pass@example.com:1080#test",
  "ss":"ss://YWVzLTI1Ni1nY206c2VjcmV0@example.com:443#test",
  "vmess":"vmess://"+base64.StdEncoding.EncodeToString([]byte(`{"v":"2","ps":"test","add":"example.com","port":"443","id":"00000000-0000-4000-8000-000000000001","aid":"0","net":"ws","path":"/ws","tls":"tls"}`)),
  "vless":"vless://00000000-0000-4000-8000-000000000001@example.com:443?security=tls&type=ws&path=%2Fws#test",
  "hy2":"hysteria2://secret@example.com:443?sni=example.com#test",
  "anytls":"anytls://secret@example.com:443?sni=example.com#test",
  "tuic":"tuic://00000000-0000-4000-8000-000000000001:secret@example.com:443#test",
  "http":"http://user:pass@example.com:8080#test",
  "xray":`{"outbounds":[{"protocol":"trojan","tag":"test","settings":{"servers":[{"address":"example.com","port":443,"password":"secret"}]}}]}`,
 }
 for name,input:=range cases{t.Run(name,func(t *testing.T){out,err:=convert([]byte(input),0);if err!=nil{t.Fatal(err)};var doc map[string]json.RawMessage;if json.Unmarshal(out,&doc)!=nil||doc["outbounds"]==nil{t.Fatal("not a node document")}})}
}
func TestRejectsPartialAndSecretErrors(t *testing.T){
 for _,text:=range []string{"",`<html>login</html>`,`{broken`,"trojan://SECRET@example.com:443\nunknown://PRIVATE", "proxies:\n - {type: unsupported, name: PRIVATE}",string(bytes.Repeat([]byte("x"),maxInput+1)),"a\x00b"}{
  _,err:=convert([]byte(text),0);if err==nil{t.Fatal("accepted invalid input")};if bytes.Contains([]byte(err.Error()),[]byte("PRIVATE"))||bytes.Contains([]byte(err.Error()),[]byte("SECRET")){t.Fatal("credential leak")}
 }
}
func TestNativeNodeFieldsAreNotDropped(t *testing.T){
 input:=`{"inbounds":[{"type":"mixed","listen":"0.0.0.0"}],"outbounds":[{"type":"trojan","tag":"x","server":"example.com","server_port":443,"password":"s","tls":{"enabled":true,"certificate":["opaque"]}}]}`
 out,err:=convert([]byte(input),0);if err!=nil{t.Fatal(err)};if bytes.Contains(out,[]byte("inbounds"))||!bytes.Contains(out,[]byte("certificate")){t.Fatal("node-only preservation failed")}
}
