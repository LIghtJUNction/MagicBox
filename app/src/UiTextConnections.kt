package com.github.lightjunction.magicbox

fun UiText.connections(): String = if (this === UiText.zh) "当前连接" else "Connections"

fun UiText.closeConnection(): String = if (this === UiText.zh) "关闭" else "Close"

fun UiText.confirmCloseSingleConnection(bytes: String): String =
    if (this === UiText.zh) "将关闭这条连接，当前累计流量 $bytes。" else "Will close this connection, currently totaling $bytes."

fun UiText.connectionFilterPlaceholder(): String =
    if (this === UiText.zh) "筛选域名、IP、规则或链路" else "Filter host, IP, rule, or chain"

fun UiText.copyMatchedConnections(): String = if (this === UiText.zh) "复制匹配" else "Copy matches"

fun UiText.shareMatchedConnections(): String = if (this === UiText.zh) "分享匹配" else "Share matches"

fun UiText.closedMatchedConnections(count: Int): String =
    if (this === UiText.zh) "已关闭 $count 条匹配连接。" else "Closed $count matching connections."

fun UiText.noMatchedConnections(): String = if (this === UiText.zh) "没有匹配连接。" else "No matching connections."

fun UiText.ruleConnectionBuckets(): String = if (this === UiText.zh) "规则分布" else "Rule distribution"

fun UiText.chainConnectionBuckets(): String = if (this === UiText.zh) "链路分布" else "Path distribution"

fun UiText.matchedConnections(count: Int): String =
    if (this === UiText.zh) {
        "匹配 $count 条连接，界面显示前 12 条，导出最多 50 条。"
    } else {
        "$count matching connections; showing up to 12, exporting up to 50."
    }

fun UiText.connectionsSummary(
    count: Int,
    up: String,
    down: String,
): String =
    if (this === UiText.zh) "$count 条连接，累计上传 $up，下载 $down。" else "$count connections, uploaded $up, downloaded $down."
