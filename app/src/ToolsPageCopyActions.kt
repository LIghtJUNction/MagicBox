package com.github.lightjunction.magicbox

import android.content.Context

fun copyCommandHistoryEntry(
    context: Context,
    entry: CommandHistoryEntry,
): Boolean {
    if (entry.output.isBlank()) return false
    copyPlainText(context, "MagicBox command output", formatCommandHistoryEntry(entry))
    return true
}

fun shareCommandHistoryEntry(
    context: Context,
    entry: CommandHistoryEntry,
) {
    if (entry.output.isBlank()) return
    sharePlainText(context, "MagicBox command output", formatCommandHistoryEntry(entry))
}

fun copyIssueDraft(
    context: Context,
    draft: String,
): Boolean {
    if (draft.isBlank()) return false
    copyPlainText(context, "MagicBox support report", draft)
    return true
}

fun shareIssueDraft(
    context: Context,
    draft: String,
) {
    if (draft.isBlank()) return
    sharePlainText(context, "MagicBox support report", draft)
}

fun copyNodeList(
    context: Context,
    nodeList: CliResult?,
): Boolean {
    val nodes = parseNodeList(nodeList?.output.orEmpty())
    if (nodes.isEmpty()) return false
    copyPlainText(context, "MagicBox nodes", nodes.joinToString("\n"))
    return true
}

fun shareNodeList(
    context: Context,
    nodeList: CliResult?,
) {
    val nodes = parseNodeList(nodeList?.output.orEmpty())
    if (nodes.isEmpty()) return
    sharePlainText(context, "MagicBox nodes", nodes.joinToString("\n"))
}

fun copyNetworkSnapshot(
    context: Context,
    result: CliResult?,
): Boolean {
    result ?: return false
    if (result.output.isBlank()) return false
    copyPlainText(context, "MagicBox network snapshot", formatToolResult(result))
    return true
}

fun shareNetworkSnapshot(
    context: Context,
    result: CliResult?,
) {
    result ?: return
    if (result.output.isBlank()) return
    sharePlainText(context, "MagicBox network snapshot", formatToolResult(result))
}

fun copyToolOutput(
    context: Context,
    result: CliResult?,
): Boolean {
    result ?: return false
    if (result.output.isBlank()) return false
    copyPlainText(context, "MagicBox tool output", formatToolResult(result))
    return true
}

fun shareToolOutput(
    context: Context,
    result: CliResult?,
) {
    result ?: return
    if (result.output.isBlank()) return
    sharePlainText(context, "MagicBox tool output", formatToolResult(result))
}

