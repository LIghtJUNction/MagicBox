package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsActionsCard(
    loading: Boolean,
    onRunTool: (String) -> Unit,
    onOpenPanel: () -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Label(t.diagnostics)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.health, enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("health") }
            SmallButton(t.diagnose, enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("diagnose") }
            SmallButton(t.support, enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("support bundle") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.connectivityTest(), enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("pingtest") }
            SmallButton(t.ecapture, enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("ecapture status") }
            SmallButton(t.coreLogs(), enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("service logs sing-box 80") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.mcpLogs, enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("mcp logs 40") }
            SmallButton(t.webUiStatus(), enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("webui status") }
            SmallButton(t.webUiVerify(), enabled = !loading, modifier = Modifier.weight(1f)) { onRunTool("webui verify") }
        }
        Spacer(Modifier.height(8.dp))
        SmallButton(t.openSingBoxPanel(), enabled = !loading, modifier = Modifier.fillMaxWidth(), onClick = onOpenPanel)
    }
}
