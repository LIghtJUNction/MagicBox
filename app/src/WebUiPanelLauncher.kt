package com.github.lightjunction.magicbox

import android.content.Context

suspend fun launchSingBoxWebUi(
    context: Context,
    t: UiText,
): CliResult {
    val probe = runMagicNet("api groups")
    if (!probe.success) {
        return probe.copy(summary = t.singBoxApiNotReady())
    }

    val entry = runMagicNet("api ui sing-box")
    val url = parseFirstHttpUrl(entry.output)
    if (!entry.success || url == null) {
        return entry.copy(summary = t.singBoxPanelMissing())
    }

    openUri(context, url)
    return entry.copy(summary = t.openedSingBoxPanel())
}

fun parseFirstHttpUrl(output: String): String? =
    Regex("""https?://[^\s]+""")
        .find(output)
        ?.value
        ?.trimEnd('.', ',', ';')
