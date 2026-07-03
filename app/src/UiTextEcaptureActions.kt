package com.github.lightjunction.magicbox

fun UiText.ecaptureControl(): String = if (this === UiText.zh) "eCapture 控制" else "eCapture control"

fun UiText.ecaptureSummary(): String =
    if (this === UiText.zh) {
        "检查 eCapture 二进制，或短时抓取 TLS/PCAP 输出到模块日志目录。"
    } else {
        "Check the eCapture binary, or run short TLS/PCAP captures into the module log directory."
    }

fun UiText.ecaptureStatus(): String = if (this === UiText.zh) "状态" else "Status"

fun UiText.ecaptureVersion(): String = if (this === UiText.zh) "版本" else "Version"

fun UiText.ecaptureHelp(): String = if (this === UiText.zh) "帮助" else "Help"

fun UiText.captureSeconds(): String = if (this === UiText.zh) "秒数 1-30" else "Seconds 1-30"

fun UiText.capturePid(): String = if (this === UiText.zh) "PID/all" else "PID/all"

fun UiText.captureUid(): String = if (this === UiText.zh) "UID/all" else "UID/all"

fun UiText.captureInterface(): String = if (this === UiText.zh) "网卡" else "Interface"

fun UiText.captureFilter(): String = if (this === UiText.zh) "pcap 过滤器" else "pcap filter"

fun UiText.capturePcap(): String = if (this === UiText.zh) "抓取 PCAP" else "Capture PCAP"

fun UiText.invalidCaptureSeconds(): String =
    if (this === UiText.zh) "抓取秒数必须是 1 到 30 的整数。" else "Capture seconds must be an integer from 1 to 30."

fun UiText.invalidCapturePid(): String = if (this === UiText.zh) "PID 必须是数字或 all。" else "PID must be numeric or all."

fun UiText.invalidCaptureUid(): String = if (this === UiText.zh) "UID 必须是数字或 all。" else "UID must be numeric or all."

fun UiText.invalidCaptureInterface(): String =
    if (this === UiText.zh) "网卡名只能包含字母、数字、下划线、点、冒号和横线。" else "Interface may only contain letters, numbers, underscore, dot, colon, and dash."
