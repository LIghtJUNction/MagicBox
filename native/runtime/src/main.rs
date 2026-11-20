//! Android process lifetime adapter. eBPF ownership/attachment logic is compiled
//! directly from the SAME pinned MagicNet file, rather than forked in the app.
use std::{fs, io::{self,Read,Write}, path::{Path,PathBuf}, process::{Command,Stdio,ExitStatus}, time::{Instant,Duration}};
use std::os::unix::process::CommandExt;
use std::os::fd::AsRawFd;
#[path = "../../../dependencies/MagicNet/crates/magicnet-cli/src/ebpf_runtime.rs"]
mod ebpf_runtime;
struct App { pid: String, executable: PathBuf }
fn owned_singbox_pids(app:&App)->Result<Vec<String>,String>{
 let pid=app.pid.parse::<u32>().map_err(|_|"invalid-pid")?;
 if pid<2{return Err("invalid-pid".into())}
 let exe=fs::read_link(format!("/proc/{pid}/exe")).map_err(|_|"core-unreadable")?;
 if exe!=app.executable{return Err("core-owner-mismatch".into())}
 Ok(vec![app.pid.clone()])
}
fn read_proc_text_bounded(path:&Path,limit:usize)->Result<String,String>{
 let mut out=Vec::new();fs::File::open(path).map_err(|_|"read-failed")?.take((limit+1)as u64).read_to_end(&mut out).map_err(|_|"read-failed")?;
 if out.len()>limit{return Err("too-large".into())} String::from_utf8(out).map_err(|_|"invalid-utf8".into())
}
struct BoundedCommandOutput {status:Option<ExitStatus>,stdout:Vec<u8>,timed_out:bool,truncated:bool}
fn run_bounded_command(mut command:Command,timeout:Duration,limit:usize)->Result<BoundedCommandOutput,String>{
 let mut child=command.stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::null()).spawn().map_err(|_|"spawn-failed")?;
 let mut stream=child.stdout.take().ok_or("stdout-missing")?;
 let fd=stream.as_raw_fd();unsafe{libc::fcntl(fd,libc::F_SETFL,libc::fcntl(fd,libc::F_GETFL)|libc::O_NONBLOCK);}
 let deadline=Instant::now()+timeout;let mut bytes=Vec::new();let mut buf=[0u8;4096];let mut truncated=false;let mut timed_out=false;
 let status=loop{
  for _ in 0..8 {match stream.read(&mut buf){Ok(0)=>break,Ok(n)=>{let keep=n.min(limit.saturating_sub(bytes.len()));bytes.extend_from_slice(&buf[..keep]);if keep<n{truncated=true}},Err(e)if e.kind()==io::ErrorKind::WouldBlock=>break,Err(_)=>break}}
  if let Some(status)=child.try_wait().map_err(|_|"wait-failed")?{break Some(status)}
  if Instant::now()>=deadline||truncated{timed_out=Instant::now()>=deadline;let _=child.kill();break child.wait().ok()}
  std::thread::sleep(Duration::from_millis(10));
 };
 // Drain bytes already emitted before process exit, still respecting the bound.
 for _ in 0..256{match stream.read(&mut buf){Ok(0)|Err(_)=>break,Ok(n)=>{let keep=n.min(limit.saturating_sub(bytes.len()));bytes.extend_from_slice(&buf[..keep]);truncated|=keep<n;}}}
 Ok(BoundedCommandOutput{status,stdout:bytes,timed_out,truncated})
}
fn supervise(args:&[String])->Result<(),String>{
 if args.len()!=3{return Err("usage".into())}
 let core=fs::canonicalize(&args[0]).map_err(|_|"core-missing")?;
 let config=fs::canonicalize(&args[1]).map_err(|_|"config-missing")?;
 let work=fs::canonicalize(&args[2]).map_err(|_|"work-missing")?;
 if config.parent()!=Some(work.as_path()){return Err("config-boundary".into())}
 let mut cmd=Command::new(core);cmd.args(["run","-c"]).arg(config).arg("-D").arg(work).stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
 let parent=unsafe{libc::getpid()};
 unsafe{cmd.pre_exec(move||{if libc::prctl(libc::PR_SET_PDEATHSIG,libc::SIGKILL)!=0||libc::getppid()!=parent{return Err(io::Error::last_os_error())}Ok(())});}
 let mut child=cmd.spawn().map_err(|_|"core-spawn-failed")?;
 // Lifetime is the app-owned stdin pipe. EOF also stops a root process after
 // Android force-stop/LMK; no unowned PID or global process-name kill is used.
 writeln!(io::stdout(),"{}",child.id()).map_err(|_|"owner-pipe-failed")?;io::stdout().flush().map_err(|_|"owner-pipe-failed")?;
 let mut pollfd=libc::pollfd{fd:0,events:libc::POLLIN|libc::POLLHUP|libc::POLLERR,revents:0};
 loop{
  if child.try_wait().map_err(|_|"wait-failed")?.is_some(){return Err("core-exited".into())}
  let result=unsafe{libc::poll(&mut pollfd,1,200)};
  if result>0 {break} if result<0&&io::Error::last_os_error().kind()!=io::ErrorKind::Interrupted{break}
 }
 unsafe{libc::kill(child.id() as i32,libc::SIGTERM);}
 let end=Instant::now()+Duration::from_secs(3);
 loop{if child.try_wait().map_err(|_|"wait-failed")?.is_some(){return Ok(())}if Instant::now()>=end{let _=child.kill();let _=child.wait();return Ok(())}std::thread::sleep(Duration::from_millis(30));}
}
fn inspect(args:&[String])->Result<(),String>{
 if args.len()!=3{return Err("usage".into())}
 let app=App{pid:args[0].clone(),executable:fs::canonicalize(&args[1]).map_err(|_|"core-missing")?};owned_singbox_pids(&app)?;
 let mut cmd=Command::new(&app.executable);cmd.args(["tools","ebpf","status","--mode","local","--network","tcp,udp","--cgroup",&args[2],"--json"]);
 let report=run_bounded_command(cmd,Duration::from_secs(6),512*1024)?;
 if report.timed_out||report.truncated||!report.status.is_some_and(|s|s.success()){return Err("capability-failed".into())}
 let report=serde_json::from_slice(&report.stdout).map_err(|_|"report-invalid")?;
 let evidence=ebpf_runtime::inspect_ebpf_attachments(&app,&report,true,&args[2],&["tcp".into(),"udp".into()],&[]);
 if !evidence.local_attached{return Err("attachment-unverified".into())}owned_singbox_pids(&app)?;Ok(())
}
fn main(){let args=std::env::args().skip(1).collect::<Vec<_>>();let result=if args.first().is_some_and(|v|v=="supervise"){supervise(&args[1..])}else{inspect(&args)};if let Err(error)=result{eprintln!("{error}");std::process::exit(1)}}
#[cfg(test)]mod tests{use super::*;#[test]fn rejects_foreign_pid(){assert!(owned_singbox_pids(&App{pid:std::process::id().to_string(),executable:PathBuf::from("/not-our-core")}).is_err());}#[test]fn bounded_child_timeout(){let mut c=Command::new("sleep");c.arg("5");let r=run_bounded_command(c,Duration::from_millis(20),1024).unwrap();assert!(r.timed_out);}#[test]fn rejects_missing_input(){assert!(supervise(&[]).is_err());assert!(inspect(&[]).is_err());}}
