# MagicBox · Token 云集合

面向日常使用的原生 Android 代理界面，与 [MagicNet](https://github.com/LIghtJUNction/MagicNet) 共用固定版本的 sing-box 内核和订阅解析器。

## 两个可以同时安装的版本

| 版本 | 包名 | 依赖 |
| --- | --- | --- |
| 通用版 `universal` | `com.github.lightjunction.magicbox` | 内置代理内核、订阅解析器与 eBPF 检查组件；不依赖 MagicNet |
| 仅 UI 版 `controller` | `com.github.lightjunction.magicbox.ui` | 必须授予 root，并安装启用兼容的 MagicNet；不包含代理内核 |

Android 8.0 及以上。通用预览包包含 ARM64 与 x86_64 两种架构。两个包可共存，但不要同时运行两套 root 透明代理；通用版会拒绝与已启用的 MagicNet 争抢路由。

## 连接方式

**系统代理**：通用版在 `127.0.0.1:2080` 提供 HTTP/SOCKS5。无需 root，但需要在 Wi-Fi 或支持代理的应用中手动配置。它不等于全局 VPN，不会自动接管蜂窝网络或忽略系统代理的应用。

**TUN**：需要 root。只有内核 API 与 TUN 接口都就绪，才显示连接成功。

**eBPF**：使用 MagicNet 的 sing-box 分支提供的真实 eBPF 入站。需要 root 与兼容内核；能力检查和本进程挂载检查失败时不会伪装成功，也不会暗中回退成 TUN。通用版目前采用 local 模式，不宣称支持热点共享接管。

仅 UI 版通过 MagicNet 的机器接口读取状态，通过模块现有命令控制 TUN/eBPF，不另行启动一套内核。

## Token 云集合

暖纸色、深墨色与少量陶土色；支持随系统切换明暗主题。按钮点击后整体消解成字符，再汇聚固化。横向拖动会打散 token，松手后归位，不抢占页面的纵向滚动。

不使用玻璃模糊或常驻粒子循环。几何参数预计算，单个表面最多 112 个字符；静止时没有动画帧时钟。支持减少动态效果，并响应系统动画关闭、节电与应用退到后台。

## 订阅

支持 HTTPS 地址、文本粘贴与本地文件。当前格式入口：Clash/Mihomo YAML、sing-box JSON、Xray JSON、SIP008、节点 URI 集合及 Base64。

可执行节点适配包括 VLESS、VMess、Shadowsocks、Trojan、SOCKS、Hysteria2、AnyTLS、TUIC。**不声称任意私有格式都可解析**：未知协议或不能转换的节点会明确报错，旧订阅保持不变。Surge、Quantumult X 等未验证格式不列入支持清单。

最多 8 MiB UTF-8 文本、2000 个节点。配置在本机转换和验证，不交给第三方转换网站；订阅不能覆盖本地入站、全局路由或引用本地私密文件。

## 构建与验收

依赖版本记录在 [`runtime.lock.json`](runtime.lock.json)，通过 MagicNet 自身的构建脚本编译。运行 **Actions → MagicBox reboot verification**：原生组件 → 双包构建与单元测试 → 签名/包名/组件隔离检查 → Android 模拟器截图与实际本地转发测试。

已有构建产物时，将两个 Android runtime 产物分别放到 `app/runtime/jniLibs/<abi>/`，再运行：

```bash
./gradlew :app:assembleUniversalDebug :app:assembleControllerDebug
```

预览 APK 使用测试签名，不等于生产发布签名。若与已安装旧包的签名不同，不能直接覆盖安装；请先确认订阅已有备份。不要分发 keystore 或把未签名 APK 当作可安装版本。

完整边界与实机验收清单见 [`docs/REBOOT.md`](docs/REBOOT.md)。模拟器测试不替代 KernelSU/Magisk/APatch 实机的 root、IPv6/DNS、SELinux、耗电和路由恢复测试。

## 许可

MagicBox：AGPL-3.0-or-later。内核、解析器的版本、源码和许可证随原生产物提供。旧界面来源说明保留在 [`NOTICE.md`](NOTICE.md) 与 [`docs/ThirdParty.md`](docs/ThirdParty.md)。
