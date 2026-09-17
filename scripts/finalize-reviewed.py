"""Finish the already-uploaded approved draft; never recreate or overwrite assets.

GitHub's release-by-tag endpoint only returns published releases. Drafts must
be verified and published by their explicit numeric release ID.
"""
import importlib.util
import json
import os
from pathlib import Path
import re

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).with_name('publish-reviewed.py'))
pub = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pub)

# These IDs identify this publication attempt, not an arbitrary draft/tag.
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
    for row in rows:
        path = output / f"MagicBox-{approval['tag']}-{row['edition']}-test.apk"
        pub.require(pub.digest(path) == row['sha256'] and path.stat().st_size == row['bytes'], 'Publication APK differs from tested APK')
    release = pub.api(f'releases/{RELEASE_ID}')
    pub.require(release['tag_name'] == approval['tag'] and release['target_commitish'] == approval['source_sha'], 'Wrong release identity')
    pub.require(release['prerelease'] is True, 'Stable publication was not approved')
    remote = {a['name']: a for a in release['assets']}
    pub.require(set(remote) == {p.name for p in output.iterdir() if p.is_file()}, 'Remote asset inventory differs')
    for path in output.iterdir():
        asset = remote[path.name]
        pub.require(asset['digest'] == 'sha256:' + pub.digest(path) and asset['size'] == path.stat().st_size, 'Uploaded asset changed')
    if release['draft']:
        # Never select a second concurrent draft using an ambiguous tag name.
        published = [r for r in pub.api('releases?per_page=100') if r['tag_name'] == approval['tag'] and not r['draft']]
        pub.require(not published, 'Another release is already published; inspect it instead of overwriting')
        pub.gh('api', '--method', 'PATCH', f'repos/{pub.REPO}/releases/{RELEASE_ID}',
               '-F', 'draft=false', '-F', 'prerelease=true', '-f', 'make_latest=false')
    result = pub.api(f'releases/{RELEASE_ID}')
    pub.require(result['draft'] is False and result['prerelease'] is True, 'Publication not confirmed')
    print('Published and hash-verified:', result['html_url'])


if __name__ == '__main__':
    main()
