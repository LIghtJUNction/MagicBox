package com.github.lightjunction.magicbox

fun UiText.logsViewer(): String = if (this === UiText.zh) "日志查看" else "Log viewer"

fun UiText.logsViewerSummary(): String =
    if (this === UiText.zh) {
        "按需读取 sing-box 或 MCP 真实日志，便于复制排查。"
    } else {
        "Read real sing-box or MCP logs on demand for debugging."
    }

fun UiText.logLinesPlaceholder(): String = if (this === UiText.zh) "日志行数 20-300" else "Log lines 20-300"

fun UiText.invalidLogLines(): String =
    if (this === UiText.zh) "日志行数必须是 20 到 300 的整数。" else "Log lines must be an integer from 20 to 300."
