"""Resume the exact uploaded Alpha draft; draft releases are addressed by ID.

No rebuild, re-sign, overwrite, replacement release, or omitted acceptance gate.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import tarfile
import zipfile

REPO = 'LIghtJUNction/MagicBox'
RELEASE_ID = 390526726
RUN_ID = 35196386148
SOURCE = 'f882d7b9fa6fd2d2eda497656289456d9f10b216'
HEAD = 'dadd3463749f7b04a212916c30b743ae0a03a537'
TAG = 'v0.2.0-alpha.1'


def need(value, reason):
    if not value:
        raise RuntimeError(reason)


def api(path):
    return json.loads(subprocess.check_output(['gh','api',f'repos/{REPO}/{path}'],text=True))


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def one(root, name):
    paths=list(root.rglob(name))
    need(len(paths)==1, f'Missing or ambiguous {name}')
    return paths[0]


def main():
    need(os.environ.get('GITHUB_REPOSITORY')==REPO, 'Wrong repository')
    approval=json.loads(Path('.github/release-request.json').read_text())
    need(approval['validated_run']==RUN_ID and approval['reviewed_sha']==SOURCE and approval['head_sha']==HEAD and approval['tag']==TAG, 'Different approval')
    run=api(f'actions/runs/{RUN_ID}')
    need(run['status']=='completed' and run['conclusion']=='success' and run['head_sha']==HEAD and run['name']=='Token Cloud', 'Validation not successful')
    need(run['head_repository']['full_name']==REPO, 'Foreign run')
    need(api(f'git/commits/{SOURCE}')['tree']['sha']==api(f'git/commits/{HEAD}')['tree']['sha'], 'Source tree mismatch')
    release=api(f'releases/{RELEASE_ID}')
    need(release['draft'] is True and release['prerelease'] is True and release['tag_name']==TAG and release['target_commitish']==SOURCE, 'Not the expected unpublished draft')
    need(release['author']['login']=='github-actions[bot]', 'Unexpected release creator')
    assets=release['assets']
    expected={f'MagicBox-{TAG}-{edition}-test.apk' for edition in ('universal','ui')} | {
        f'MagicBox-{TAG}-source.zip',f'MagicBox-{TAG}-dependencies-source.tar.gz',
        f'MagicBox-{TAG}-evidence.tar.gz','SHA256SUMS','ui-tests.txt','universal-tests.txt',
        'validation.json','visual-approval.json'}
    need({a['name'] for a in assets}==expected and len(assets)==len(expected), 'Incomplete or unexpected uploaded assets')
    downloads=Path('uploaded-assets');downloads.mkdir()
    for asset in assets:
        need(asset['state']=='uploaded' and 0<asset['size']<200*1024*1024, 'Invalid asset')
        path=downloads/asset['name']
        with path.open('wb') as output:
            subprocess.run(['gh','api','-H','Accept: application/octet-stream',
                f"repos/{REPO}/releases/assets/{asset['id']}"],stdout=output,check=True)
        need(path.stat().st_size==asset['size'] and 'sha256:'+digest(path)==asset['digest'], 'Uploaded asset digest mismatch')
    sums={}
    for line in (downloads/'SHA256SUMS').read_text().splitlines():
        hash_value,name=line.split('  ',1)
        need(re.fullmatch('[0-9a-f]{64}',hash_value) and name in expected and name!='SHA256SUMS', 'Invalid checksum manifest')
        sums[name]=hash_value
    need(set(sums)==expected-{'SHA256SUMS'}, 'Incomplete checksum manifest')
    for name,value in sums.items():
        need(digest(downloads/name)==value, 'Checksum manifest does not match uploads')
    need(json.loads((downloads/'visual-approval.json').read_text())==approval, 'Visual approval changed')
    subprocess.run(['gh','run','download',str(RUN_ID),'--repo',REPO,'--name','android-validation','--dir','validated'],check=True)
    subprocess.run(['gh','run','download',str(RUN_ID),'--repo',REPO,'--name','android-device-evidence','--dir','device'],check=True)
    validated=Path('validated');device=Path('device')
    rows=json.loads(one(validated,'apk-verification.json').read_text())
    need({r['edition'] for r in rows}=={'universal','ui'} and len(rows)==2, 'Missing verified editions')
    for row in rows:
        edition=row['edition'];original=one(validated,f'app-{edition}-debug.apk')
        need(row['package']=='com.github.lightjunction.magicbox'+('.ui' if edition=='ui' else ''), 'Unexpected package')
        need(digest(original)==row['sha256']==digest(downloads/f'MagicBox-{TAG}-{edition}-test.apk'), 'Published APK differs from tested bytes')
        need(original.stat().st_size==row['bytes'], 'Verified APK size changed')
        test=one(device,f'{edition}-tests.txt')
        need(test.read_bytes()==(downloads/f'{edition}-tests.txt').read_bytes(), 'Uploaded test results changed')
        text=test.read_text()
        need(re.search(r'OK \([1-9][0-9]* tests?\)',text) and not re.search('FAILURES|INSTRUMENTATION_FAILED|Process crashed',text), 'Device test failed')
    original_source=one(validated,'MagicBox-source.zip')
    need(digest(original_source)==digest(downloads/f'MagicBox-{TAG}-source.zip'), 'Source snapshot changed')
    with zipfile.ZipFile(original_source) as archive:
        need(archive.comment.decode().strip()==SOURCE, 'Source commit mismatch')
    report=json.loads((downloads/'validation.json').read_text())
    need(report['commit']==SOURCE and report['tag']==TAG and report['editions']==rows, 'Published validation mismatch')
    need(report['rooted_physical_device_verified'] is False, 'Unsupported physical-device claim')
    # Compare every uploaded device-evidence file against the successful run,
    # ignoring only tar container timestamps, never content bytes.
    with tarfile.open(downloads/f'MagicBox-{TAG}-evidence.tar.gz') as archive:
        members={m.name:m for m in archive.getmembers() if m.isfile()}
        for path in device.rglob('*'):
            if path.is_file():
                name=path.as_posix()
                need(name in members, 'Missing archived device evidence')
                need(members[name].size==path.stat().st_size, 'Evidence archive size mismatch')
                need(hashlib.sha256(archive.extractfile(members[name]).read()).hexdigest()==digest(path), 'Evidence archive contents changed')
    spec=importlib.util.spec_from_file_location('visual','scripts/verify-visual-approval.py')
    visual=importlib.util.module_from_spec(spec);spec.loader.exec_module(visual)
    visual.verify(device,approval)
    # Address the draft by immutable release ID. The /releases/tags endpoint
    # does not resolve an unpublished draft whose tag has not been created.
    result=json.loads(subprocess.check_output(['gh','api','--method','PATCH',
        f'repos/{REPO}/releases/{RELEASE_ID}','-F','draft=false','-F','prerelease=true',
        '-f','make_latest=false'],text=True))
    need(result['draft'] is False and result['prerelease'] is True and result['tag_name']==TAG, 'Publication not confirmed')
    print('Published exact verified Alpha:',result['html_url'])


if __name__=='__main__':
    main()
