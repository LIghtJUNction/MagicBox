package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun McpControlCard(
    result: CliResult?,
    loading: Boolean,
    onAction: (String) -> Unit,
) {
    val t = LocalUiText.current
    var confirmRotate by remember { mutableStateOf(false) }
    val bridge = result?.takeIf { it.success }?.let { parseModuleBridge(it.output) }
    var bind by remember(bridge?.bind) { mutableStateOf(bridge?.bind?.ifBlank { "127.0.0.1" } ?: "127.0.0.1") }
    var port by remember(bridge?.port) { mutableStateOf(bridge?.port?.ifBlank { "9097" } ?: "9097") }
    var confirmSetEndpoint by remember { mutableStateOf(false) }
    val enabled = bridge?.enabled == "1"
    val cleanBind = bind.trim()
    val cleanPort = port.trim()
    val endpointValid = isSafeMcpBind(cleanBind) && isSafeMcpPort(cleanPort)
    fun run(action: String) {
        confirmRotate = false
        confirmSetEndpoint = false
        onAction(action)
    }

    Card(padding = PaddingValues(10.dp)) {
        Label(t.mcpControl())
        Spacer(Modifier.height(8.dp))
        Body(mcpControlSummary(t, result, bridge))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (enabled) t.disableMcp() else t.enableMcp(),
                enabled = !loading && (enabled || endpointValid),
                modifier = Modifier.weight(1f),
            ) {
                run(if (enabled) "disable" else "enable ${shellQuote(cleanBind)} $cleanPort")
            }
            SmallButton(t.restart, enabled = !loading, modifier = Modifier.weight(1f)) { run("restart") }
            SmallButton(t.stop, enabled = !loading, modifier = Modifier.weight(1f)) { run("stop") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(
                bind,
                t.mcpBindPlaceholder(),
                {
                    bind = it
                    confirmSetEndpoint = false
                },
                Modifier.weight(1f),
            )
            TextInput(
                port,
                t.mcpPortPlaceholder(),
                {
                    port = it
                    confirmSetEndpoint = false
                },
                Modifier.weight(1f),
            )
        }
        if (!endpointValid) {
            Spacer(Modifier.height(6.dp))
            Body(t.mcpEndpointInvalid())
        }
        Spacer(Modifier.height(8.dp))
        SmallButton(
            if (confirmSetEndpoint) t.confirm() else t.setMcpEndpoint(),
            enabled = !loading && endpointValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (confirmSetEndpoint) {
                run("set ${shellQuote(cleanBind)} $cleanPort")
            } else {
                confirmSetEndpoint = true
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.mcpLogs, enabled = !loading, modifier = Modifier.weight(1f)) { run("logs 80") }
            SmallButton(
                if (confirmRotate) t.confirm() else t.rotateMcpSecret(),
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) {
                if (confirmRotate) run("rotate-secret") else confirmRotate = true
            }
        }
    }
}

private fun mcpControlSummary(
    t: UiText,
    result: CliResult?,
    bridge: ModuleBridge?,
): String =
    when {
        result == null -> t.checkingModule
        !result.success -> result.summary
        bridge == null -> result.summary
        bridge.enabled == "1" -> "MCP ${t.enabled}: ${listOf(bridge.bind, bridge.port).filter { it.isNotBlank() }.joinToString(":")}"
        else -> "MCP ${bridge.enabled.ifBlank { t.unknown }}"
    }

private fun isSafeMcpBind(value: String): Boolean =
    value == "localhost" ||
        value.matches(Regex("""(\d{1,3}\.){3}\d{1,3}""")) ||
        value.matches(Regex("""[A-Za-z0-9_.:-]{1,64}"""))

private fun isSafeMcpPort(value: String): Boolean =
    value.toIntOrNull()?.let { it in 1..65535 } == true
