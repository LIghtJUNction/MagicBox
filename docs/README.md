# MagicBox

**English** | [简体中文](README_ZH_HANS.md)

MagicBox is maintained by LightJunction. This APK is the Android frontend and
control surface for the separate MagicNet Magisk module backend.

The app does not embed MagicNet backend binaries, root logic, sing-box, mihomo,
proxy services, native networking code, or the original YumeBox package
namespace. The backend source of truth remains the Magisk module under
`/data/adb/modules/MagicNet`.

## Scope

- View MagicNet runtime statistics and health snapshots.
- Edit MagicNet routing rules for proxy/direct/block domain suffixes.
- Create config/rule/blocklist diff issue drafts with diagnostic context.
- Prepare a future bridge to MagicNet CLI/MCP without putting backend code in
  the APK.

## Backend Contract

The intended backend commands are provided by MagicNet:

```text
/data/adb/modules/MagicNet/cli api stats
/data/adb/modules/MagicNet/cli health
/data/adb/modules/MagicNet/cli route list
/data/adb/modules/MagicNet/cli route add-domain <proxy|direct|block> <domain>
/data/adb/modules/MagicNet/cli route apply
/data/adb/modules/MagicNet/cli support bundle
/data/adb/modules/MagicNet/cli mcp status
```

MagicBox currently provides the frontend shell and product layout for these
flows. Backend execution belongs to MagicNet.

## Attribution

MagicBox's Android UI direction is inspired by the original
[YumeBox](https://github.com/YumeYucca/YumeBox) project by YumeYucca. MagicBox
does not use the YumeBox project name for releases, does not ship the original
YumeBox icon, and does not direct users to YumeBox issue channels for MagicBox
support.

See [NOTICE](../NOTICE.md) for attribution details.

## License

MagicBox is distributed under the GNU Affero General Public License v3.0 or
later. See [LICENSE](../LICENSE).

## Release Workflow

Use the GitHub Actions `Release` workflow. For manual releases, choose a
`patch`, `minor`, or `major` bump, or enter an exact version. The workflow
increments `project.version.name` and `project.version.code` in
`gradle.properties`, commits the bump, builds the debug and release APKs, and
publishes a release.

To publish a signed release APK, configure these repository secrets:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
and `ANDROID_KEY_PASSWORD`. Without them, CI still uploads the debug APK and the
unsigned release APK.
