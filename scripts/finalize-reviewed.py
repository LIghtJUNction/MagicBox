"""Finish the approved publication by explicit ID, or accept an identical one.

The release-by-tag API excludes drafts. Never rebuild, replace assets, delete a
published release, or delete a tag. Only this attempt's own redundant draft may
be removed after its full output is backed up and the published APKs match.
"""
import importlib.util
import json
import os
from pathlib import Path
import re

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-reviewed.py'))
pub = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pub)
RELEASE_ID = 390526680
OUTPUT_RUN = 35197318445
OUTPUT_COMMIT = '145a920993907a715df600285b74002c3f99d459'


def main():
    pub.require(os.environ.get('GITHUB_REPOSITORY') == pub.REPO, 'Wrong repository')
    approval = json.loads(Path('release/approval.json').read_text())
    run = pub.api(f"actions/runs/{approval['run_id']}")
    pub.require(run['conclusion'] == 'success' and run['status'] == 'completed', 'Acceptance must remain successful')
    pub.require(run['head_sha'] == approval['head_sha'] and run['head_repository']['full_name'] == pub.REPO, 'Acceptance source mismatch')
    output_run = pub.api(f'actions/runs/{OUTPUT_RUN}')
    pub.require(output_run['head_sha'] == OUTPUT_COMMIT and output_run['head_branch'] == 'release/token-cloud-verified', 'Wrong publication output')
    for name, dest, run_id in (
        ('android-validation', 'reviewed-validation', approval['run_id']),
        ('android-device-evidence', 'reviewed-device', approval['run_id']),
        ('reviewed-release-record', 'release-output', OUTPUT_RUN),
    ):
        pub.require(not Path(dest).exists(), 'Refuse mixed output')
        pub.gh('run', 'download', str(run_id), '--repo', pub.REPO, '--name', name, '--dir', dest)
    rows = pub.validate(approval, Path('reviewed-validation'), Path('reviewed-device'))
    output = Path('release-output')
    pub.require(json.loads((output / 'approval.json').read_text()) == approval, 'Output approval mismatch')
    pub.require(json.loads((output / 'apk-verification.json').read_text()) == rows, 'Output APK report mismatch')
    checked = set()
    for line in (output / 'SHA256SUMS').read_text().splitlines():
        expected, name = line.split(maxsplit=1)
        name = name.strip()
        pub.require(Path(name).name == name and re.fullmatch(r'[0-9a-f]{64}', expected), 'Unsafe checksum entry')
        pub.require(name not in checked and pub.digest(output / name) == expected, 'Publication bytes changed')
        checked.add(name)
    pub.require(checked == {p.name for p in output.iterdir() if p.is_file()} - {'SHA256SUMS'}, 'Incomplete checksum inventory')
    critical = [output / f"MagicBox-{approval['tag']}-source.zip"]
    for row in rows:
        path = output / f"MagicBox-{approval['tag']}-{row['edition']}-test.apk"
        pub.require(pub.digest(path) == row['sha256'] and path.stat().st_size == row['bytes'], 'Publication APK differs from tested APK')
        critical.append(path)
    release = pub.api(f'releases/{RELEASE_ID}')
    pub.require(release['tag_name'] == approval['tag'] and release['target_commitish'] == approval['source_sha'], 'Wrong release identity')
    pub.require(release['prerelease'] is True, 'Stable publication was not approved')
    remote = {a['name']: a for a in release['assets']}
    pub.require(set(remote) == {p.name for p in output.iterdir() if p.is_file()}, 'Remote asset inventory differs')
    for path in output.iterdir():
        asset = remote[path.name]
        pub.require(asset['digest'] == 'sha256:' + pub.digest(path) and asset['size'] == path.stat().st_size, 'Uploaded asset changed')
    if release['draft']:
        published = [r for r in pub.api('releases?per_page=100') if r['tag_name'] == approval['tag'] and not r['draft']]
        if published:
            pub.require(len(published) == 1, 'Ambiguous published releases')
            existing = published[0]
            pub.require(existing['id'] != RELEASE_ID and existing['prerelease'] is True and existing['target_commitish'] == approval['source_sha'], 'Different release already published')
            existing_assets = {a['name']: a for a in existing['assets']}
            for path in critical:
                asset = existing_assets.get(path.name, {})
                pub.require(asset.get('digest') == 'sha256:' + pub.digest(path) and asset.get('size') == path.stat().st_size, 'Published APK/source differs from inspected bytes')
            # All own draft assets have just been verified against OUTPUT_RUN's
            # immutable backup. Remove only the temporary draft this attempt made.
            current = pub.api(f'releases/{RELEASE_ID}')
            pub.require(current['draft'] is True and current['target_commitish'] == approval['source_sha'], 'Draft changed during verification')
            pub.gh('api', '--method', 'DELETE', f'repos/{pub.REPO}/releases/{RELEASE_ID}')
            print('Identical reviewed Alpha already published:', existing['html_url'])
            print('Removed only this attempt\'s backed-up duplicate draft:', RELEASE_ID)
            return
        pub.gh('api', '--method', 'PATCH', f'repos/{pub.REPO}/releases/{RELEASE_ID}',
               '-F', 'draft=false', '-F', 'prerelease=true', '-f', 'make_latest=false')
    result = pub.api(f'releases/{RELEASE_ID}')
    pub.require(result['draft'] is False and result['prerelease'] is True, 'Publication not confirmed')
    print('Published and hash-verified:', result['html_url'])


if __name__ == '__main__':
    main()
