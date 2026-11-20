// Read-only cgroup attachment proof. Mirrors MagicNet's owned-program check:
// a globally visible BPF program is not evidence that OUR core is attached.
package main

import (
 "fmt"
 "os"
 "path/filepath"
 "strconv"
 "strings"
 ebpf "github.com/cilium/ebpf"
 "github.com/cilium/ebpf/link"
)

func verify(pid int) error {
 held := map[ebpf.ProgramID]bool{}
 entries, err := os.ReadDir(fmt.Sprintf("/proc/%d/fdinfo", pid)); if err != nil { return err }
 for _, entry := range entries {
  text, err := os.ReadFile(filepath.Join(fmt.Sprintf("/proc/%d/fdinfo", pid), entry.Name())); if err != nil { continue }
  for _, line := range strings.Split(string(text), "\n") {
   fields := strings.Fields(line)
   if len(fields) == 2 && fields[0] == "prog_id:" {
    id, err := strconv.ParseUint(fields[1], 10, 32); if err == nil { held[ebpf.ProgramID(id)] = true }
   }
  }
 }
 if len(held) == 0 { return fmt.Errorf("core has no BPF descriptors") }
 cgroup, err := os.Open("/sys/fs/cgroup"); if err != nil { return err }; defer cgroup.Close()
 for _, attach := range []ebpf.AttachType{
  ebpf.AttachCGroupInet4Connect, ebpf.AttachCGroupInet6Connect,
  ebpf.AttachCGroupUDP4Sendmsg, ebpf.AttachCGroupUDP6Sendmsg,
  ebpf.AttachCGroupUDP4Recvmsg, ebpf.AttachCGroupUDP6Recvmsg,
 } {
  query, err := link.QueryPrograms(link.QueryOptions{Target: int(cgroup.Fd()), Attach: attach}); if err != nil { return err }
  found := false
  for _, item := range query.Programs {
   if !held[item.ID] { continue }
   program, err := ebpf.NewProgramFromID(item.ID); if err != nil { continue }
   info, err := program.Info(); program.Close()
   if err == nil && strings.HasPrefix(info.Name, "sb_ebpf_") { found = true }
  }
  if !found { return fmt.Errorf("required owned cgroup hook missing") }
 }
 return nil
}
func main() {
 if len(os.Args) != 2 { os.Exit(2) }
 pid, err := strconv.Atoi(os.Args[1]); if err != nil || pid <= 1 { os.Exit(2) }
 if verify(pid) != nil { fmt.Println(`{"schema":1,"attached":false}`); os.Exit(1) }
 fmt.Println(`{"schema":1,"attached":true}`)
}
