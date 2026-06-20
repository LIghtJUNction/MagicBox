# MagicBox

MagicBox is the Android frontend for the MagicNet Magisk module backend.

Core frontend flows:

- View MagicNet statistics and health snapshots.
- Edit proxy/direct/block routing rules.
- Create config/rule/blocklist diff issue drafts.
- Prepare a clean bridge to MagicNet CLI/MCP while keeping backend code out of
  the APK.

Backend execution belongs to the MagicNet module under
`/data/adb/modules/MagicNet`.

See [docs/README.md](docs/README.md) and [NOTICE.md](NOTICE.md).

## Release

Use **Actions → Release → Run workflow**. Pick `patch`, `minor`, or `major`.
The workflow updates `gradle.properties`, commits the version bump, builds the
APK, tags the commit, and publishes a GitHub Release.

You can also provide an exact version such as `0.2.0`.

License: AGPL-3.0-or-later. See [LICENSE](LICENSE).
