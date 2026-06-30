package com.github.lightjunction.magicbox

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppCommandResultCard(
    result: CliResult?,
    copied: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    CommandBlock(t.lastAppCommand, result, showOutput = true)
    if (!result?.output.isNullOrBlank()) {
        AppCommandExportActions(copied = copied, onCopy = onCopy, onShare = onShare)
    }
}

@Composable
fun AppCommandExportActions(
    copied: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton(
            if (copied) t.copied() else t.copyToolOutput(),
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = onCopy,
        )
        SmallButton(
            t.shareToolOutput(),
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = onShare,
        )
    }
}

fun copyAppCommandResult(
    context: Context,
    result: CliResult?,
): Boolean {
    val command = result ?: return false
    if (command.output.isBlank()) return false
    copyPlainText(context, "MagicBox app command", formatToolResult(command))
    return true
}

fun shareAppCommandResult(
    context: Context,
    result: CliResult?,
) {
    val command = result ?: return
    if (command.output.isBlank()) return
    sharePlainText(context, "MagicBox app command", formatToolResult(command))
}
