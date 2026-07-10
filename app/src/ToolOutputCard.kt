package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToolOutputCard(
    result: CliResult?,
    copied: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    CommandBlock(t.toolOutput, result, showOutput = true)
    if (!result?.output.isNullOrBlank()) {
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
}

