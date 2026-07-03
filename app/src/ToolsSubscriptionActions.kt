package com.github.lightjunction.magicbox

import android.content.Context

data class SubscriptionRuntimeRefresh(
    val subscription: CliResult,
    val nodes: CliResult,
    val currentNode: CliResult,
)

data class SubscriptionSaveResult(
    val result: CliResult,
    val refresh: SubscriptionRuntimeRefresh,
)

suspend fun reloadSubscriptionRuntime(): SubscriptionRuntimeRefresh =
    SubscriptionRuntimeRefresh(
        subscription = runMagicNet("sub list"),
        nodes = runMagicNet("node list"),
        currentNode = runMagicNet("node current"),
    )

suspend fun saveSubscriptionRuntime(
    draft: String,
    t: UiText,
): SubscriptionSaveResult {
    val saveResult = saveSingBoxSubscriptions(parseSubscriptionInput(draft), t)
    return SubscriptionSaveResult(saveResult, reloadSubscriptionRuntime())
}

fun copySubscriptionState(
    context: Context,
    result: CliResult?,
): Boolean {
    val state = result ?: return false
    if (state.output.isBlank()) return false
    copyPlainText(context, "MagicBox subscription state", formatToolResult(state))
    return true
}

fun shareSubscriptionState(
    context: Context,
    result: CliResult?,
) {
    val state = result ?: return
    if (state.output.isBlank()) return
    sharePlainText(context, "MagicBox subscription state", formatToolResult(state))
}

