"""Match manually inspected PNG hashes against the same installed-app CI run."""
import hashlib
import json
from pathlib import Path

PACKAGES = {'universal': 'com.github.lightjunction.magicbox', 'ui': 'com.github.lightjunction.magicbox.ui'}


def require(value, message):
    if not value:
        raise ValueError(message)


def verify(device: Path, approval: dict):
    require(approval.get('visually_reviewed') is True, 'Explicit visual approval missing')
    expected = {(edition, f'qa-{page}-{theme}.png') for edition in PACKAGES
                for page in ('home','subscriptions','settings') for theme in ('light','dark')}
    records = approval.get('screenshots', [])
    require(len(records) == 12 and {(r['edition'], r['file']) for r in records} == expected,
            'All twelve exact foreground screenshots must be inspected')
    manifests = {}
    for edition, package in PACKAGES.items():
        files = list((device / edition).rglob('qa-manifest.json'))
        require(len(files) == 1, 'Ambiguous or missing visual manifest')
        manifest = json.loads(files[0].read_text())
        require(manifest['package'] == package, 'Wrong app window')
        require(manifest['version'] == approval['tag'].removeprefix('v'), 'Wrong app version')
        manifests[edition] = {row['file']: row for row in manifest['screenshots']}
    for row in records:
        paths = list((device / row['edition']).rglob(row['file']))
        require(len(paths) == 1, 'Missing or ambiguous screenshot')
        content = paths[0].read_bytes()
        require(content.startswith(b'\x89PNG\r\n\x1a\n'), 'Not an actual PNG')
        require(hashlib.sha256(content).hexdigest() == row['sha256'], 'Inspected image changed')
        entry = manifests[row['edition']][row['file']]
        require(entry['sha256'] == row['sha256'], 'Capture manifest changed')
        require(entry['foreground_package'] == PACKAGES[row['edition']], 'Foreign window covered app')
    require('Process: com.github.lightjunction.magicbox' not in (device / 'crashes.txt').read_text(), 'App crash recorded')
    events = device / 'events.txt'
    require(events.exists(), 'Missing ANR evidence')
    require(not any('am_anr' in line and 'com.github.lightjunction.magicbox' in line
                    for line in events.read_text().splitlines()), 'App ANR recorded')


if __name__ == '__main__':
    verify(Path('device'), json.loads(Path('.github/release-request.json').read_text()))
    print('Twelve inspected foreground screenshots match the immutable device run.')
