# MagicBox · Token 云集合 / 0.2.0-alpha.1

This is a restart preview, not a claim of completed physical-device acceptance.

## Two distributions

| Flavor | Application ID | Runtime |
| --- | --- | --- |
| universal | com.github.lightjunction.magicbox | bundled pinned sing-box fork, Proxylink adapter, read-only eBPF attachment checker |
| controller | com.github.lightjunction.magicbox.ui | mandatory root and an enabled compatible MagicNet module; no native engines |

Both packages can be installed at once. Do not run two root dataplanes simultaneously. Standalone root modes refuse to compete with an enabled MagicNet module. The UI distribution does not claim a system-only mode unsupported by MagicNet; use the universal local proxy for that mode.

Universal is an independent distribution, not a promise of support for every CPU. This preview bundles Android arm64-v8a and x86_64. Android 8+ is required.

## Shared source, distinct platform adapters

`runtime.lock.json` pins MagicNet, its sing-box gitlink, and the exact Proxylink revision in MagicNet's build hook. The build reuses MagicNet's core build script. MagicBox does not carry a separate proxy protocol implementation. The controller validates the module's schema-1 machine interface and uses its existing private-payload subscription pipeline. Standalone process lifecycle and configuration assembly are Android host adapters, not a second network core.

## Modes and honest status

- System proxy: independent local HTTP/SOCKS5 on 127.0.0.1:2080. No root is required. The user must configure Wi-Fi or a proxy-aware app; this does NOT automatically cover cellular traffic or apps ignoring system proxy settings.
- Root TUN: API plus magicbox0 interface proof is required before readiness is shown. Routing is exclusive to this runtime and the core owns cleanup.
- Root eBPF: uses the actual fork's local eBPF inbound, not nftables renamed as eBPF. The preflight probe must succeed. A read-only helper checks all six required cgroup hook types against BPF descriptors held by this core PID. No hotspot/shared TC claim is made for the standalone adapter.
- A running process is never sufficient evidence for a ready dataplane. Failed or unknown checks clear ready state.

## Subscription support and boundaries

The local adapter uses Proxylink ConvertContent and generators: Clash/Mihomo YAML, sing-box JSON, Xray JSON, SIP008, share-URI collections and Base64. Executable node adapters currently include VLESS, VMess, Shadowsocks, Trojan, SOCKS, Hysteria2, AnyTLS and TUIC. Parse success is distinct from generator and core compatibility. Unsupported entries cause an explicit counted rejection and leave the previous subscription intact. Unknown/proprietary formats, Surge, Quantumult X and unverified protocol options are NOT advertised as universally supported.

Files are bounded to 8 MiB UTF-8 and 2000 nodes. Remote fetching is HTTPS-only with bounded redirects. Imports never adopt subscription-supplied inbounds, global routing or local file references. Profiles use private atomic storage; backups are disabled. Files, subscription URLs and parser errors are not placed in logs. The native converter prints only safe diagnostic categories. Controller payload content enters the privileged wrapper through stdin and is removed by the module's private-payload flow.

## Token cloud design

Warm paper, restrained ink and one terracotta accent; native scalable text, generous whitespace, no glass blur or continuous ambient animation. Tap dissolves the entire filled surface into a bounded field of token glyphs and settles it again. Horizontal drag scatters it without stealing normal vertical scrolling. There are at most 112 glyphs, precomputed geometry and one reused Paint per surface. Animation values are read during drawing rather than rebuilding the page every frame. Actions execute once independently of animation. System disabled animations, battery saver, backgrounding and the reduced-motion preference suppress the effect. Main controls have at least 48dp touch targets.

## Release gate

CI must build both flavors, run unit tests, verify package IDs and absence/presence of native proxy components, and capture actual Android screens. A source/browser illustration is not a native screenshot. The release must clearly identify debug-signed test packages; unsigned files must never be presented as installable. A stable release additionally needs real Android root tests: denied/granted root, missing/disabled module, subscription failures, core exit, stop/crash/reboot cleanup, IPv4/IPv6/DNS routing, TUN and eBPF attachments, SELinux enforcing, and coexistence with other VPNs.

No physical-device throughput, frame-time, battery or memory figures should be claimed without measurement. This design removes idle animation work; it does not itself prove 60/120fps on every phone.
