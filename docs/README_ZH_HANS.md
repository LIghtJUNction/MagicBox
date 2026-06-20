# MagicBox

[English](README.md) | **简体中文**

MagicBox 由 LightJunction 维护。这个 APK 是独立 MagicNet Magisk 模块后端的
Android 前端和控制面板。

本 App 不内置 MagicNet 后端二进制、root 逻辑、sing-box、mihomo、代理服务、
native 网络代码，也不沿用原 YumeBox 包名。后端事实来源仍然是设备上的 Magisk
模块：`/data/adb/modules/MagicNet`。

## 范围

- 查看 MagicNet 运行统计和健康快照。
- 编辑 MagicNet 代理/直连/阻断域名后缀规则。
- 基于配置、规则、黑名单变更创建 diff issue 草稿，并附带诊断上下文。
- 为未来对接 MagicNet CLI/MCP 预留前端桥接，但不把后端代码打进 APK。

## 后端契约

预期后端命令由 MagicNet 提供：

```text
/data/adb/modules/MagicNet/cli api stats
/data/adb/modules/MagicNet/cli health
/data/adb/modules/MagicNet/cli route list
/data/adb/modules/MagicNet/cli route add-domain <proxy|direct|block> <domain>
/data/adb/modules/MagicNet/cli route apply
/data/adb/modules/MagicNet/cli support bundle
/data/adb/modules/MagicNet/cli mcp status
```

MagicBox 当前提供这些流程的前端壳和产品布局。后端执行属于 MagicNet。

## 鸣谢

MagicBox 的 Android UI 方向借鉴了 YumeYucca 的原始
[YumeBox](https://github.com/YumeYucca/YumeBox) 项目。MagicBox 发布时不使用
YumeBox 项目名，不打包原始 YumeBox 图标，也不会把 MagicBox 支持请求引导到
YumeBox issue 渠道。

详见 [NOTICE](../NOTICE.md)。

## 许可证

MagicBox 以 GNU Affero General Public License v3.0 or later 发布。见
[LICENSE](../LICENSE)。
