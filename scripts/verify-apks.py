"""Verify the final APK bytes, not just the Gradle flavor declaration."""
import hashlib, json, os, pathlib, subprocess, zipfile
root=pathlib.Path('app/build/outputs/apk')
tools=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
report=[]
for edition, package in [('universal','com.github.lightjunction.magicbox'),('ui','com.github.lightjunction.magicbox.ui')]:
 apk=root/edition/'debug'/f'app-{edition}-debug.apk'
 subprocess.run([str(tools/'apksigner'),'verify','--verbose',str(apk)],check=True)
 badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
 assert f"package: name='{package}'" in badging, badging.splitlines()[0]
 with zipfile.ZipFile(apk) as z:
  libs=[n for n in z.namelist() if n.startswith('lib/')]
  if edition=='ui': assert not libs, 'UI edition must not contain native runtime'
  else:
   for abi in ['arm64-v8a','x86_64']:
    for component in ['singbox','proxylink','mbprobe']:
     name=f'lib/{abi}/lib{component}.so';assert name in libs, name
     assert z.read(name)[:4]==b'\x7fELF', name
  assert 'assets/cloud/index.html' in z.namelist()
 report.append({'edition':edition,'package':package,'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'native_files':len(libs)})
pathlib.Path('evidence').mkdir(exist_ok=True)
pathlib.Path('evidence/apk-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
