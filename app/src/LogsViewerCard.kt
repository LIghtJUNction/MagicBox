package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

fun parseLogLineCount(value: String): Int? {
    val count = value.trim().toIntOrNull() ?: return null
    return count.takeIf { it in 20..300 }
}

@Composable
fun LogsViewerCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf("120") }
    var result by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    fun runLogs(target: LogTarget) {
        val count = parseLogLineCount(lines)
        if (count == null) {
            result = CliResult(false, "magicnet ${target.command} <lines>", t.invalidLogLines())
            copied = false
            return
        }
        loading = true
        copied = false
        scope.launch {
            result = runMagicNet("${target.command} $count")
            loading = false
        }
    }

    val output = result?.output.orEmpty().trim()
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.logsViewer())
                Body(result?.summary ?: t.logsViewerSummary())
            }
            StatusPill(
                when (result?.success) {
                    true -> t.ok
                    false -> t.fail
                    null -> if (loading) t.busy else t.idle
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        TextInput(lines, t.logLinesPlaceholder(), { lines = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.coreLogs(), enabled = !loading, modifier = Modifier.weight(1f)) { runLogs(LogTarget.Core) }
            SmallButton(t.mcpLogs, enabled = !loading, modifier = Modifier.weight(1f)) { runLogs(LogTarget.Mcp) }
        }
        if (output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(output.take(1200))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(if (copied) t.copied() else t.copyReport(), enabled = true, modifier = Modifier.weight(1f)) {
                    copyPlainText(context, "MagicBox logs", formatToolResult(result!!))
                    copied = true
                }
                SmallButton(t.shareReport(), enabled = true, modifier = Modifier.weight(1f)) {
                    sharePlainText(context, "MagicBox logs", formatToolResult(result!!))
                }
            }
        }
    }
}

private enum class LogTarget(val command: String) {
    Core("service logs sing-box"),
    Mcp("mcp logs"),
}
