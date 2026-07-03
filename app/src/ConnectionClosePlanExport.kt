package com.github.lightjunction.magicbox

enum class ConnectionClosePlanKind {
    Single,
    Top,
    Matching,
}

fun formatConnectionClosePlan(
    kind: ConnectionClosePlanKind,
    query: String,
    preview: ConnectionClosePreview,
    command: String = defaultClosePlanCommand(kind, query, preview.candidates.size, preview.candidates.firstOrNull()?.id),
): String {
    val title =
        when (kind) {
            ConnectionClosePlanKind.Single -> "MagicBox connection close plan: single"
            ConnectionClosePlanKind.Top -> "MagicBox connection close plan: top"
            ConnectionClosePlanKind.Matching -> "MagicBox connection close plan: matching"
        }
    return buildString {
        appendLine(title)
        appendLine("command: ${redactClosePlanValue(command)}")
        if (kind == ConnectionClosePlanKind.Matching) {
            appendLine("query: ${redactClosePlanValue(query.ifBlank { "(empty)" })}")
        }
        appendLine("matches: ${preview.totalMatches}")
        appendLine("closing: ${preview.candidates.size}")
        appendLine("truncated: ${preview.truncated}")
        appendLine("total_bytes: ${formatBytes(preview.totalBytes)}")
        appendLine()
        preview.candidates.forEachIndexed { index, target ->
            appendLine("${index + 1}. ${redactClosePlanValue(target.label)}")
            appendLine("   transfer: ${redactClosePlanValue(target.transfer)}")
            appendLine("   detail: ${redactClosePlanValue(target.detail)}")
        }
    }.trim()
}

private fun defaultClosePlanCommand(
    kind: ConnectionClosePlanKind,
    query: String,
    count: Int,
    candidateId: String? = null,
): String =
    when (kind) {
        ConnectionClosePlanKind.Single -> "api close ${candidateId?.let(::shellQuote).orEmpty()}"
        ConnectionClosePlanKind.Top -> "api close-top $count"
        ConnectionClosePlanKind.Matching -> "api close-matching ${shellQuote(query)}"
    }

fun closePlanShareTitle(kind: ConnectionClosePlanKind): String =
    when (kind) {
        ConnectionClosePlanKind.Single -> "MagicBox single connection close plan"
        ConnectionClosePlanKind.Top -> "MagicBox top connection close plan"
        ConnectionClosePlanKind.Matching -> "MagicBox matching connection close plan"
    }

private fun redactClosePlanValue(value: String): String = redactSupportText(value).ifBlank { "(empty)" }
