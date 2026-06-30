package com.github.lightjunction.magicbox

fun UiText.webUiStatus(): String = if (this === UiText.zh) "WebUI 状态" else "WebUI status"

fun UiText.webUiVerify(): String = if (this === UiText.zh) "校验 WebUI" else "Verify WebUI"

fun UiText.webUiInstallLocal(): String = if (this === UiText.zh) "安装本地面板" else "Install panel"

fun UiText.webUiInstallUrlPlaceholder(): String =
    if (this === UiText.zh) "面板 dist.zip 下载 URL" else "Panel dist.zip download URL"

fun UiText.webUiInstallInvalidUrl(): String =
    if (this === UiText.zh) "WebUI 下载 URL 必须以 http:// 或 https:// 开头。" else "WebUI download URL must start with http:// or https://."

fun UiText.mcpControl(): String = if (this === UiText.zh) "MCP 控制" else "MCP control"

fun UiText.enableMcp(): String = if (this === UiText.zh) "启用 MCP" else "Enable MCP"

fun UiText.disableMcp(): String = if (this === UiText.zh) "禁用 MCP" else "Disable MCP"

fun UiText.rotateMcpSecret(): String = if (this === UiText.zh) "换密钥" else "Rotate secret"

fun UiText.mcpBindPlaceholder(): String = if (this === UiText.zh) "绑定地址" else "Bind address"

fun UiText.mcpPortPlaceholder(): String = if (this === UiText.zh) "端口" else "Port"

fun UiText.setMcpEndpoint(): String = if (this === UiText.zh) "保存端点" else "Save endpoint"

fun UiText.mcpEndpointInvalid(): String =
    if (this === UiText.zh) "请输入有效的绑定地址和 1-65535 端口。" else "Enter a valid bind address and a 1-65535 port."

fun UiText.supervisorControl(): String = if (this === UiText.zh) "守护进程" else "Supervisor"

fun UiText.fswatchStatus(pid: String): String =
    if (this === UiText.zh) "配置监听：${pid.ifBlank { "未知" }}" else "Config watcher: ${pid.ifBlank { "unknown" }}"

fun UiText.coreLogs(): String = if (this === UiText.zh) "核心日志" else "Core logs"

fun UiText.webUiReady(): String = if (this === UiText.zh) "本地 WebUI 已就绪。" else "Local WebUI is ready."

fun UiText.webUiMissing(): String = if (this === UiText.zh) "本地 WebUI 缺少入口文件。" else "Local WebUI entry is missing."
