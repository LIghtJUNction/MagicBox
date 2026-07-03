package com.github.lightjunction.magicbox

private const val CONNECTION_MATCH_EXPORT_LIMIT = 50

fun formatConnectionMatches(
    query: String,
    matches: List<ConnectionTarget>,
): String {
    val visibleMatches = matches.take(CONNECTION_MATCH_EXPORT_LIMIT)
    return buildString {
        appendLine("MagicBox connection matches")
        appendLine("query: ${redactConnectionExportValue(query.ifBlank { "(all)" })}")
        appendLine("count: ${matches.size}")
        appendLine("showing: ${visibleMatches.size}")
        appendLine("total_bytes: ${formatBytes(matches.sumOf { it.totalBytes })}")
        appendLine()
        visibleMatches.forEachIndexed { index, target ->
            appendLine("${index + 1}. ${redactConnectionExportValue(target.label)}")
            appendLine("   transfer: ${redactConnectionExportValue(target.transfer)}")
            appendLine("   bytes: ${formatBytes(target.totalBytes)}")
            appendLine("   detail: ${redactConnectionExportValue(target.detail)}")
        }
        if (visibleMatches.size < matches.size) {
            appendLine()
            appendLine("... ${matches.size - visibleMatches.size} more matches not shown")
        }
    }.trim()
}

private fun redactConnectionExportValue(value: String): String = redactSupportText(value).ifBlank { "(empty)" }
