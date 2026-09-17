# MagicBox

Token 云集合。面向日常使用的 Android 代理界面，与 MagicNet 使用同源核心。

## 两个版本，可以同时安装

| 版本 | 包名 | 运行方式 |
| --- | --- | --- |
| MagicBox 通用版 | `com.github.lightjunction.magicbox` | APK 内置 sing-box、Proxylink 适配器与进程监督组件，不依赖 MagicNet 模块 |
| MagicBox UI | `com.github.lightjunction.magicbox.ui` | 不携带代理核心，必须授予 Root，并安装、启用兼容的 MagicNet 模块 |

通用版目前包含 `arm64-v8a` 与 `x86_64`；最低 Android 8.0。两个版本可共存，但不要同时开启全设备透明代理。

## 连接方式

**系统代理**在本机提供 HTTP / SOCKS 服务，地址为 `127.0.0.1:2080`。无 Root 时需要在 Wi-Fi 或支持代理的应用里手动填写；它不是 VPN，不保证接管所有应用。通用版可独立使用此模式。

**TUN**需要 Root 与可用的 TUN 设备。**eBPF**还需要支持该入站方式的 sing-box、设备内核及 cgroup / attachment 能力。界面只开放实际检查通过的能力，进程存在不等于数据通道就绪。UI 版只提供 MagicNet 目前公开支持的 TUN / eBPF 控制。

Rooted 实机上的 TUN / eBPF、切网、休眠恢复仍属于 Alpha 验收范围。模拟器上的 UI 和本地代理测试不能代替这些测试。

## 导入

转换在设备上完成，不把订阅交给外部转换网站。通用版的测试样例覆盖分享链接、标准及 URL-safe Base64、Clash / Mihomo YAML、sing-box JSON、单节点 JSON 和 Xray JSON；协议样例包括 SS、VMess、VLESS、Trojan、Hysteria2、AnyTLS、TUIC、SOCKS 与 HTTP。

WireGuard 单 peer 配置有适配代码；多 peer、脚本钩子以及没有经过适配的扩展不会静默降级。输入上限为 2 MiB，转换后还要通过 sing-box 配置检查。未知格式、部分转换失败和危险的本地文件引用会明确报错，不替换已有订阅。这里不声称兼容所有私有格式和所有字段扩展。

UI 版通过 MagicNet 私有暂存文件和模块事务导入，能力以实际安装的模块为准。

## Token 云集合

不使用玻璃模糊或背景折射。实体表面、留白、字符与清晰的层级构成界面。

按钮点击后离散为字符，再汇聚固化；横向拖动也可离散，松手恢复，不触发按钮操作。纵向手势保留正常滚动。粒子池最多 72 个，静止时停止逐帧绘制，支持减少动态效果与省电降级。

## 构建与验证

`Token Cloud` 工作流构建固定版本依赖，验证两种 APK 的包名、签名、组件隔离与源码一致性，再在 Android 35 模拟器里进行共存安装、UI 截图、实际 socket 转发、失败导入保留和停止测试。

```sh
# 不构建代理核心的 UI 开发检查
./gradlew :app:testUiDebugUnitTest :app:assembleUiDebug
```

通用版需要先按照 `.github/workflows/token-cloud.yml` 构建并放入原生组件；只改包名生成的空壳不算通用版。

Alpha 发布只提升指定成功验证运行中的 APK 原始字节，不重新构建、不上传 unsigned APK。测试版使用 CI 测试签名，不作为长期正式发行签名；后续不同签名的测试包可能需要先卸载旧测试版，卸载前先保存订阅。

## 同源与许可

MagicNet 固定到 `a94682e4a4d2c187b3f2ad7e0107f7d1ad4323cd`，sing-box 使用其中锁定的 LIghtJUNction 分支；eBPF 检查器直接编译 MagicNet 源文件。Proxylink 固定到 `44929c0984944870297c260dc43a4aa9262f9e1c`。构建产物包含组件许可、来源记录与校验和。

MagicBox：AGPL-3.0-or-later。保留原生高级管理界面的历史来源说明，见 `NOTICE.md` 与 `docs/ThirdParty.md`。Token Cloud 的新界面位于 `app/assets/cloud/`，Android 适配位于 `app/cloud/`。
