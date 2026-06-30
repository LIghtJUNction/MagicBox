package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SupervisorControlCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<CliResult?>(null) }
    var lastAction by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    fun runSupervisor(action: String = "status") {
        loading = true
        copied = false
        scope.launch {
            val result = runMagicNet("supervisor $action fswatch")
            if (action == "status") status = result else {
                lastAction = result
                status = runMagicNet("supervisor status fswatch")
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        status = runMagicNet("supervisor status fswatch")
    }

    val fswatch = status?.takeIf { it.success }?.output?.lineSequence()
        ?.firstOrNull { it.startsWith("fswatch=") }
        ?.substringAfter("=")
        ?.trim()
        .orEmpty()
    val output = listOfNotNull(status, lastAction).joinToString("\n\n") { formatToolResult(it) }

    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.supervisorControl())
                Body(t.fswatchStatus(fswatch.ifBlank { status?.summary.orEmpty() }))
            }
            StatusPill(if (status?.success == true && fswatch != "stopped") t.running else t.idle)
        }
        lastAction?.takeIf { it.output.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(500))
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f)) { runSupervisor() }
                SmallButton(t.start, enabled = !loading, modifier = Modifier.weight(1f)) { runSupervisor("start") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.restart, enabled = !loading, modifier = Modifier.weight(1f)) { runSupervisor("restart") }
                SmallButton(t.stop, enabled = !loading, modifier = Modifier.weight(1f)) { runSupervisor("stop") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                    copyPlainText(context, "MagicBox supervisor status", output)
                    copied = true
                }
                SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                    sharePlainText(context, "MagicBox supervisor status", output)
                }
            }
        }
    }
}
