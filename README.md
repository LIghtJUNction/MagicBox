# MagicBox

Android 代理客户端，与 [MagicNet](https://github.com/LIghtJUNction/MagicNet) 使用相同的固定版本内核和订阅解析器。支持 Android 8.0 及以上。

## 选择版本

| 版本 | 包名 | 使用条件 |
| --- | --- | --- |
| 通用版 `universal` | `com.github.lightjunction.magicbox` | 内置内核，支持 ARM64、x86_64 |
| UI 版 `ui` | `com.github.lightjunction.magicbox.ui` | 需要 root 和已启用的兼容 MagicNet 模块；不含内核 |

两个版本可以同时安装。运行 root 代理时只启用一套：通用版会拒绝与已启用的 MagicNet 争用路由。

## 使用

1. 在「订阅」粘贴 HTTPS 链接或订阅内容，也可以导入文件。
2. 选择节点，在「连接」选择模式并连接。UI 版首次使用需要授予模块权限。
3. 使用本地系统代理时，在 Wi-Fi 或应用中填写 `127.0.0.1:2080`。

| 模式 | 条件与范围 |
| --- | --- |
| 系统代理 | 通用版，无需 root；提供本地 HTTP/SOCKS5，需要手动配置，不自动接管全部流量 |
| TUN | 需要 root；内核 API 与 TUN 接口均就绪后才显示已连接 |
| eBPF | 需要 root 和兼容内核；检查本进程的实际挂载，失败时停止，不回退到 TUN |

通用版 eBPF 使用 local 模式。UI 版通过 MagicNet 现有接口控制 TUN/eBPF。

订阅入口支持 Clash/Mihomo YAML、sing-box JSON、Xray JSON、SIP008、节点 URI 和 Base64；可用协议包括 VLESS、VMess、Shadowsocks、Trojan、SOCKS、Hysteria2、AnyTLS、TUIC。未知格式或不能转换的节点会报错，并保留原订阅。文件上限为 8 MiB UTF-8、2000 个节点；解析在本机完成。

界面跟随系统明暗主题。「设置」可减少动态效果；系统关闭动画、节电或应用进入后台时也会停用动效。

## 构建

需要 JDK 21、Android SDK 35。原生组件版本见 [`runtime.lock.json`](runtime.lock.json)，构建步骤见 [runtime workflow](.github/workflows/runtime.yml)。先构建固定依赖，再校验并放置组件：

```bash
python3 scripts/stage-runtime.py --input runtime-input
./gradlew :app:testUniversalDebugUnitTest :app:testUiDebugUnitTest \
  :app:assembleUniversalDebug :app:assembleUiDebug
```

仅构建 UI 版可以省略原生组件步骤，执行 `./gradlew :app:testUiDebugUnitTest :app:assembleUiDebug`。

预览 APK 使用测试签名。与旧包签名不同时无法覆盖安装，卸载前请备份订阅。Alpha 通过 [Release workflow](.github/workflows/release.yml) 提升已验收的同一份 APK，发布时不重新编译或签名。源码合并不代表已发布。

实现、验收步骤及 root 实机待测项见 [`docs/REBOOT.md`](docs/REBOOT.md)。

## 许可

MagicBox 使用 AGPL-3.0-or-later。内核和解析器的固定版本、源码与许可证随构建产物提供；来源说明见 [`NOTICE.md`](NOTICE.md) 和 [`docs/ThirdParty.md`](docs/ThirdParty.md)。
