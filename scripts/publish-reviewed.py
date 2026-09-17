"""Publish approved, exact CI artifacts. No rebuild, re-signing, or overwriting."""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import zipfile

REPO = 'LIghtJUNction/MagicBox'
PACKAGES = {'universal': 'com.github.lightjunction.magicbox', 'ui': 'com.github.lightjunction.magicbox.ui'}


def require(ok: bool, message: str) -> None:
    if not ok:
        raise RuntimeError(message)


def gh(*args: str) -> str:
    return subprocess.check_output(['gh', *args], text=True)


def api(path: str):
    return json.loads(gh('api', f'repos/{REPO}/{path}'))


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def unique(root: Path, name: str) -> Path:
    matches = list(root.rglob(name))
    require(len(matches) == 1, f'Expected one {name}; found {len(matches)}')
    return matches[0]


def validate(approval: dict, validated: Path, device: Path) -> list:
    require(approval.get('schema') == 1 and approval.get('visually_reviewed') is True, 'Explicit visual approval missing')
    require(re.fullmatch(r'v\d+\.\d+\.\d+-alpha\.\d+', approval['tag']) is not None, 'Alpha tag required')
    for field in ('head_sha', 'source_sha'):
        require(re.fullmatch(r'[0-9a-f]{40}', approval[field]) is not None, f'Invalid {field}')
    with zipfile.ZipFile(unique(validated, 'MagicBox-source.zip')) as source:
        require(source.comment.decode().strip() == approval['source_sha'], 'Compiled source snapshot mismatch')
        require('project.version.name=' + approval['tag'][1:] in source.read('gradle.properties').decode().splitlines(), 'APK source version does not match tag')
    rows = json.loads(unique(validated, 'apk-verification.json').read_text())
    require(len(rows) == 2 and {r['edition'] for r in rows} == set(PACKAGES), 'Two distinct verified editions required')
    for row in rows:
        edition = row['edition']
        require(row['package'] == PACKAGES[edition], 'Wrong package ID')
        apk = unique(validated, f'app-{edition}-debug.apk')
        require(apk.stat().st_size == row['bytes'] and digest(apk) == row['sha256'], 'APK changed after validation')
        with zipfile.ZipFile(apk) as archive:
            if edition == 'universal':
                require(f"MagicBox={approval['source_sha']}\n" in archive.read('assets/components/provenance.txt').decode(), 'Native build provenance mismatch')
            else:
                require(not any(n.rsplit('/', 1)[-1] in ('libsingbox.so', 'libproxylink.so', 'libmbprobe.so') for n in archive.namelist()), 'UI APK contains a proxy core')
        tests = unique(device, f'{edition}-tests.txt').read_text()
        require(re.search(r'OK \([1-9][0-9]* tests?\)', tests) is not None and not re.search(r'FAILURES|INSTRUMENTATION_FAILED|Process crashed', tests), 'Device acceptance failed')
    require('Process: com.github.lightjunction.magicbox' not in unique(device, 'crashes.txt').read_text(), 'App crash found')
    require(not re.search(r'am_anr.*com.github.lightjunction.magicbox', unique(device, 'events.txt').read_text()), 'App ANR found')
    expected = {(e, f'qa-{p}-{t}.png') for e in PACKAGES for p in ('home','subscriptions','settings') for t in ('light','dark')}
    images = approval.get('screenshots', [])
    require(len(images) == 12 and {(r['edition'],r['file']) for r in images} == expected, 'Twelve screenshots must be reviewed')
    for row in images:
        require(re.fullmatch(r'[0-9a-f]{64}', row['sha256']) is not None, 'Invalid screenshot hash')
        path = unique(device / row['edition'], row['file'])
        require(digest(path) == row['sha256'], 'Screenshot differs from reviewed bytes')
        manifest = json.loads(unique(device / row['edition'], 'qa-manifest.json').read_text())
        require(manifest['package'] == PACKAGES[row['edition']], 'Wrong screenshot app')
        entry = [r for r in manifest['screenshots'] if r['file'] == row['file']]
        require(len(entry) == 1 and entry[0]['sha256'] == row['sha256'] and entry[0]['foreground_package'] == PACKAGES[row['edition']], 'Screenshot lacks app foreground evidence')
    return rows


def archive_dependency(repository: str, revision: str, destination: Path) -> None:
    require(re.fullmatch(r'[0-9a-f]{40}', revision) is not None, 'Unpinned dependency')
    with destination.open('wb') as stream:
        subprocess.run(['gh','api',f'repos/{repository}/zipball/{revision}'], stdout=stream, check=True)
    with zipfile.ZipFile(destination) as archive:
        require(archive.testzip() is None, 'Invalid dependency source archive')


def main() -> None:
    require(os.environ.get('GITHUB_REPOSITORY') == REPO, 'Wrong repository')
    approval = json.loads(Path('release/approval.json').read_text())
    run_id = approval['run_id']
    require(type(run_id) is int and run_id > 0, 'Invalid validation run')
    run = api(f'actions/runs/{run_id}')
    require(run['status'] == 'completed' and run['conclusion'] == 'success' and run['path'] == '.github/workflows/token-cloud.yml', 'Selected validation run did not pass')
    require(run['head_sha'] == approval['head_sha'] and run['head_repository']['full_name'] == REPO, 'Unexpected validated head')
    jobs = api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs']
    require(all(any(j['name'] == name and j['conclusion'] == 'success' for j in jobs) for name in ('native','validate','device')), 'Acceptance job missing')
    api(f"git/commits/{approval['source_sha']}")
    validated, device, output = Path('reviewed-validation'), Path('reviewed-device'), Path('release-output')
    for name, dest in [('android-validation',validated),('android-device-evidence',device)]:
        require(not dest.exists(), 'Refuse mixed-run evidence')
        gh('run','download',str(run_id),'--repo',REPO,'--name',name,'--dir',str(dest))
    rows = validate(approval, validated, device)
    output.mkdir(exist_ok=False)
    tag = approval['tag']
    for row in rows:
        edition = row['edition']
        shutil.copyfile(unique(validated, f'app-{edition}-debug.apk'), output / f'MagicBox-{tag}-{edition}-test.apk')
    shutil.copyfile(unique(validated,'MagicBox-source.zip'), output / f'MagicBox-{tag}-source.zip')
    dependencies = Path('dependency-sources'); dependencies.mkdir()
    revision = 'a94682e4a4d2c187b3f2ad7e0107f7d1ad4323cd'
    core = json.loads(gh('api',f'repos/LIghtJUNction/MagicNet/contents/sing-box?ref={revision}'))['sha']
    for repo, rev, name in [('LIghtJUNction/MagicNet',revision,'MagicNet'),('LIghtJUNction/sing-box',core,'sing-box'),('Fanju6/Proxylink','44929c0984944870297c260dc43a4aa9262f9e1c','Proxylink')]:
        archive_dependency(repo, rev, dependencies / f'{name}.zip')
    with tarfile.open(output / f'MagicBox-{tag}-dependency-sources.tar.gz','w:gz') as archive:
        archive.add(dependencies,arcname='sources')
    with tarfile.open(output / f'MagicBox-{tag}-device-evidence.tar.gz','w:gz') as archive:
        archive.add(device,arcname='evidence')
    (output/'approval.json').write_text(json.dumps(approval,indent=2)+'\n')
    (output/'apk-verification.json').write_text(json.dumps(rows,indent=2)+'\n')
    notes = f'''# MagicBox {tag} · Token 云集合

两个可共存安装的 Alpha 测试版本：

- 通用版 `universal`：`com.github.lightjunction.magicbox`，内置 sing-box、订阅转换器和运行监督组件，arm64-v8a / x86_64。
- UI 版 `ui`：`com.github.lightjunction.magicbox.ui`，不含代理内核，必须授予 Root，并安装启用兼容的 MagicNet。

点击分裂为字符再聚合；横向拖动散开，松手复原，拖动不会触发按钮操作。粒子数量最多 72 个，支持省电、减少动态效果，空闲停止绘制。节点分页与搜索不丢弃第 500 项以后的内容。

## 验证

两版编译、单元测试、包名/签名/原生组件哈希校验，Android 35 模拟器共存安装、通用版真实套接字转发、停止、失败导入保留、真实触摸拖动与十二张浅色/深色界面截图已通过；截图已逐张检查。

系统代理提供 `127.0.0.1:2080` HTTP/SOCKS，需在 Wi-Fi 或支持代理的应用中手动配置，不是全设备 VPN。

**Root 真机 TUN/eBPF、实际 MagicNet 模块联调、切网/休眠恢复与物理设备功耗仍未完成验收。** eBPF 只在内核与设备能力检查通过时开放。订阅支持常见分享链接、Base64、Clash/Mihomo、sing-box、Xray 等，未知格式会报错，不声称兼容所有私有格式。

这是 CI 测试签名的 debug APK，不是长期稳定签名。旧版本证书不同时不能直接覆盖安装；卸载前先保留订阅和设置。同装两个版本不代表可以同时接管透明代理流量。

源码提交：`{approval['source_sha']}`
验证运行：https://github.com/{REPO}/actions/runs/{run_id}

附件包含精确测试 APK、源码和依赖源码、测试记录与截图、SHA-256。没有重新构建或重新签名。
'''
    (output/'RELEASE_NOTES.md').write_text(notes)
    (output/'SHA256SUMS').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in sorted(output.iterdir()) if p.is_file()))
    existing = subprocess.run(['gh','release','view',tag,'--repo',REPO],capture_output=True)
    require(existing.returncode != 0, 'Existing release will not be overwritten')
    gh('release','create',tag,'--repo',REPO,'--target',approval['source_sha'],'--title',f'MagicBox {tag} · Token Cloud','--notes-file',str(output/'RELEASE_NOTES.md'),'--draft','--prerelease','--latest=false')
    gh('release','upload',tag,'--repo',REPO,*(str(p) for p in sorted(output.iterdir()) if p.is_file()))
    assets = {a['name']:a for a in api(f'releases/tags/{tag}')['assets']}
    for path in output.iterdir():
        require(assets.get(path.name,{}).get('digest') == 'sha256:' + digest(path), 'Uploaded bytes do not match approved release')
    gh('release','edit',tag,'--repo',REPO,'--draft=false','--prerelease','--latest=false')
    print(f'Published exact reviewed bytes: {tag}')


if __name__ == '__main__':
    main()
