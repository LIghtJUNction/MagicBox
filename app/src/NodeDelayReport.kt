package com.github.lightjunction.magicbox

data class NodeDelayEntry(
    val node: String,
    val summary: String,
    val delayMillis: Int?,
    val quality: NodeDelayQuality = nodeDelayQuality(summary),
)

data class NodeDelayStats(
    val tested: Int,
    val usable: Int,
    val fastest: NodeDelayEntry?,
    val fastestCount: Int,
    val averageMillis: Int?,
    val slowest: NodeDelayEntry?,
    val failed: Int,
    val fast: Int,
    val normal: Int,
    val slow: Int,
)

enum class NodeDelayQuality {
    Fast,
    Normal,
    Slow,
    Failed,
}

private const val FAST_NODE_DELAY_MAX_MS = 120
private const val NORMAL_NODE_DELAY_MAX_MS = 250

fun buildNodeDelayEntries(tests: Map<String, String>): List<NodeDelayEntry> =
    tests
        .map { (node, summary) ->
            NodeDelayEntry(
                node = node,
                summary = summary,
                delayMillis = parseNodeDelayMillis(summary),
                quality = nodeDelayQuality(summary),
            )
        }
        .sortedWith(compareBy<NodeDelayEntry> { it.delayMillis ?: Int.MAX_VALUE }.thenBy { it.node })

fun buildNodeDelayStats(tests: Map<String, String>): NodeDelayStats {
    val entries = buildNodeDelayEntries(tests)
    val usableEntries = entries.filter { it.delayMillis != null }
    val fastest = entries.firstOrNull { it.delayMillis != null }
    val fastestCount =
        fastest?.delayMillis?.let { best ->
            entries.count { it.delayMillis == best }
        } ?: 0
    return NodeDelayStats(
        tested = entries.size,
        usable = entries.count { it.delayMillis != null },
        fastest = fastest,
        fastestCount = fastestCount,
        averageMillis = usableEntries.takeIf { it.isNotEmpty() }?.mapNotNull { it.delayMillis }?.average()?.toInt(),
        slowest = usableEntries.lastOrNull(),
        failed = entries.count { it.delayMillis == null },
        fast = entries.count { it.quality == NodeDelayQuality.Fast },
        normal = entries.count { it.quality == NodeDelayQuality.Normal },
        slow = entries.count { it.quality == NodeDelayQuality.Slow },
    )
}

fun buildNodeDelayStatsForNodes(
    nodes: List<String>,
    tests: Map<String, String>,
): NodeDelayStats =
    buildNodeDelayStats(
        nodes
            .mapNotNull { node -> tests[node]?.let { node to it } }
            .toMap(),
    )

fun formatNodeDelayReport(tests: Map<String, String>): String {
    val entries = buildNodeDelayEntries(tests)
    val stats = buildNodeDelayStats(tests)
    return buildString {
        appendLine("MagicBox node delay report")
        appendLine("tested: ${stats.tested}")
        appendLine("usable: ${stats.usable}")
        appendLine("failed: ${stats.failed}")
        appendLine("average_ms: ${stats.averageMillis ?: "none"}")
        appendLine("fast: ${stats.fast}")
        appendLine("normal: ${stats.normal}")
        appendLine("slow: ${stats.slow}")
        appendLine("fastest: ${stats.fastest?.let { "${redactSupportText(it.node)} ${redactSupportText(it.summary)}" } ?: "none"}")
        appendLine("fastest_count: ${stats.fastestCount}")
        appendLine("slowest: ${stats.slowest?.let { "${redactSupportText(it.node)} ${redactSupportText(it.summary)}" } ?: "none"}")
        if (entries.isEmpty()) return@buildString
        appendLine()
        entries.take(50).forEachIndexed { index, entry ->
            appendLine("${index + 1}. ${redactSupportText(entry.node)}")
            appendLine("   delay: ${redactSupportText(entry.summary).ifBlank { "unknown" }}")
        }
        if (entries.size > 50) {
            appendLine()
            appendLine("... ${entries.size - 50} more nodes not shown")
        }
    }.trim()
}

fun UiText.copyNodeDelayReport(): String = if (this === UiText.zh) "复制测速摘要" else "Copy delays"

fun UiText.shareNodeDelayReport(): String = if (this === UiText.zh) "分享测速摘要" else "Share delays"

fun UiText.nodeDelayTested(): String = if (this === UiText.zh) "已测" else "Tested"

fun UiText.nodeDelayUsable(): String = if (this === UiText.zh) "可用" else "Usable"

fun UiText.nodeDelayFailed(): String = if (this === UiText.zh) "失败" else "Failed"

fun UiText.nodeDelayAverage(): String = if (this === UiText.zh) "平均" else "Average"

fun UiText.nodeDelayFastest(): String = if (this === UiText.zh) "最快" else "Fastest"

fun UiText.nodeDelaySlowest(): String = if (this === UiText.zh) "最慢" else "Slowest"

fun NodeDelayEntry.displayLabel(): String = "${redactSupportText(node)} ${redactSupportText(summary)}".trim()

fun nodeDelayQuality(summary: String?): NodeDelayQuality =
    when (val delay = parseNodeDelayMillis(summary)) {
        null -> NodeDelayQuality.Failed
        in 0..FAST_NODE_DELAY_MAX_MS -> NodeDelayQuality.Fast
        in (FAST_NODE_DELAY_MAX_MS + 1)..NORMAL_NODE_DELAY_MAX_MS -> NodeDelayQuality.Normal
        else -> NodeDelayQuality.Slow
    }

fun UiText.nodeDelayQualityLabel(quality: NodeDelayQuality): String =
    if (this === UiText.zh) {
        when (quality) {
            NodeDelayQuality.Fast -> "快"
            NodeDelayQuality.Normal -> "正常"
            NodeDelayQuality.Slow -> "慢"
            NodeDelayQuality.Failed -> "失败"
        }
    } else {
        when (quality) {
            NodeDelayQuality.Fast -> "Fast"
            NodeDelayQuality.Normal -> "OK"
            NodeDelayQuality.Slow -> "Slow"
            NodeDelayQuality.Failed -> "Failed"
        }
    }

fun NodeDelayStats.fastestDisplay(unknown: String): String =
    fastest?.let {
        val label = it.displayLabel()
        if (fastestCount > 1) "$label +${fastestCount - 1}" else label
    } ?: unknown

fun NodeDelayStats.averageDisplay(unknown: String): String = averageMillis?.let { "${it}ms" } ?: unknown

fun NodeDelayStats.slowestDisplay(unknown: String): String = slowest?.displayLabel() ?: unknown

fun NodeDelayStats.compactFastestDisplay(unknown: String): String = fastest?.delayMillis?.let { "${it}ms" } ?: unknown

fun UiText.proxyGroupDelaySummary(
    stats: NodeDelayStats,
    total: Int,
    unknown: String,
): String {
    if (stats.tested == 0) return ""
    val fastest = stats.compactFastestDisplay(unknown)
    val average = stats.averageDisplay(unknown)
    return if (this === UiText.zh) {
        "本组已测 ${stats.tested}/$total · 可用 ${stats.usable} · 快 ${stats.fast} · 慢 ${stats.slow} · 最快 $fastest · 平均 $average"
    } else {
        "Group tested ${stats.tested}/$total · usable ${stats.usable} · fast ${stats.fast} · slow ${stats.slow} · best $fastest · avg $average"
    }
}
