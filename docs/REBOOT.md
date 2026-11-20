# MagicBox implementation and validation

## Runtime

`universal` bundles the fixed sing-box fork, a Proxylink adapter and a read-only eBPF attachment checker. `ui` controls an installed MagicNet module through its schema-1 machine interface and existing commands. It contains no proxy engines. Both target Android 8+; universal binaries cover arm64-v8a and x86_64.

[`runtime.lock.json`](../runtime.lock.json) pins MagicNet, its sing-box gitlink and the Proxylink source used by its build hook. MagicBox provides Android process, configuration and UI adapters. The core build uses MagicNet's build script.

| Mode | Readiness check | Scope |
| --- | --- | --- |
| System proxy | Local core API and listener | Universal HTTP/SOCKS5 at `127.0.0.1:2080`; the user configures Wi-Fi or a proxy-aware app |
| Root TUN | Core API and `magicbox0` interface | Exclusive root dataplane; the core owns route cleanup |
| Root eBPF | Preflight and all six required cgroup hook types, bound to this core PID's BPF descriptors | Fork's local eBPF inbound; no standalone hotspot/shared TC support |

Failed or unknown readiness checks clear ready state. Standalone root modes refuse to compete with an enabled MagicNet module. UI mode does not expose a system proxy mode unsupported by the module.

## Subscriptions

The Proxylink adapter accepts Clash/Mihomo YAML, sing-box JSON, Xray JSON, SIP008, share-URI collections and Base64. Executable adapters cover VLESS, VMess, Shadowsocks, Trojan, SOCKS, Hysteria2, AnyTLS and TUIC. Unsupported entries produce a counted rejection and preserve the previous subscription.

Inputs are limited to 8 MiB UTF-8 and 2000 nodes. Remote fetching requires HTTPS and bounds redirects. Imported content cannot replace local inbounds or global routing, or reference private local files. Profiles use atomic private storage with backups disabled. Subscription content, URLs and parser details stay out of logs. Controller imports pass through stdin into the module's private-payload flow.

## Interface

The connection page shows status, the selected node and the primary action. The other pages contain subscriptions and settings. Light and dark themes use neutral backgrounds and one blue accent. Touch targets are at least 48dp.

The small header mark and primary buttons retain the bounded token effect. Geometry is precomputed, with at most 112 glyphs and no idle animation clock. Reduced motion, system animation settings, battery saver and backgrounding suppress it. Horizontal gestures leave vertical page scrolling available. Actions execute independently of animation.

## Build and validation

The Gradle flavors are `universal` and `ui`. `app/controller/` is the UI flavor's platform adapter directory. Use JDK 21 and Android SDK 35 for the Android build. The runtime workflow records native source revisions, toolchain versions, licenses and checksums; `stage-runtime.py` verifies them before copying binaries.

The [Token Cloud workflow](../.github/workflows/token-cloud.yml) builds both flavors, runs unit tests and verifies signatures, package IDs and native-component isolation. `scripts/android_visual.sh` captures real Android screens for both flavors in both themes, checks interaction and reduced motion, and exercises native import, two-hop local forwarding and shutdown. It targets an explicitly selected emulator; physical-device acceptance is a separate procedure.

These checks establish emulator behavior for the exact tested source and APKs. Root-device acceptance still covers granted/denied root, missing or disabled modules, failed imports, core exits, stop/crash/reboot cleanup, IPv4/IPv6/DNS, actual TUN/eBPF attachments, SELinux enforcing and VPN coexistence. Throughput, frame time and battery figures require measurements on the stated device.

## Alpha promotion

The [Release workflow](../.github/workflows/release.yml) consumes a successful Token Cloud push run and an approved full source SHA. It verifies the source archive, both APK identities and signatures, theme screenshots and native routing proof, then promotes those same APKs without rebuilding or resigning. Preview packages use test signing. A source merge alone does not publish an Alpha or complete root-device acceptance.
