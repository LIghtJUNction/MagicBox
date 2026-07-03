package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ControlPanel(
    loading: Boolean,
    service: CliResult?,
    transparent: CliResult?,
    lastAction: CliResult?,
    onAction: (String) -> Unit,
    pendingDangerAction: String?,
    onRequestDangerAction: (String) -> Unit,
    onCancelDangerAction: () -> Unit,
    copiedControlAction: Boolean,
    onCopyAction: () -> Unit,
    onShareAction: () -> Unit,
) {
    val t = LocalUiText.current
    fun requestControlAction(command: String) {
        if (controlActionRequiresConfirmation(command)) onRequestDangerAction(command) else onAction(command)
    }
    Card(padding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.control)
                Body(t.controlSummary(service?.summary ?: t.checkingService, currentTransparentMode(transparent)))
            }
            StatusPill(
                when (lastAction?.success) {
                    true -> t.done
                    false -> t.fail
                    null -> if (loading) t.busy else t.ready
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(t.start, enabled = !loading, modifier = Modifier.weight(1f)) { requestControlAction("service start") }
            SmallButton(t.ensureService(), enabled = !loading, modifier = Modifier.weight(1f)) { requestControlAction("service ensure") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(t.restart, enabled = !loading, modifier = Modifier.weight(1f)) { requestControlAction("service restart sing-box") }
            SmallButton(t.stop, enabled = !loading, modifier = Modifier.weight(1f)) { requestControlAction("service stop") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(t.transparentModeProxy(), enabled = !loading, modifier = Modifier.weight(1f)) {
                requestControlAction("transparent set proxy")
            }
            SmallButton(t.transparentModeExternalTun(), enabled = !loading, modifier = Modifier.weight(1f)) {
                requestControlAction("transparent set external-tun")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(t.transparentModeHybrid(), enabled = !loading, modifier = Modifier.weight(1f)) {
                requestControlAction("transparent set hybrid")
            }
            SmallButton("TUN", enabled = !loading, modifier = Modifier.weight(1f)) {
                requestControlAction("transparent set tun")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(t.applyConfig, enabled = !loading, modifier = Modifier.weight(1f)) { requestControlAction("config apply") }
            SmallButton(t.closeConns, enabled = !loading, modifier = Modifier.weight(1f)) { requestControlAction("api close-all") }
        }
        if (pendingDangerAction != null) {
            Spacer(Modifier.height(10.dp))
            Body(t.confirmDangerAction(pendingDangerAction))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.weight(1f)) { onAction(pendingDangerAction) }
                SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onCancelDangerAction)
            }
        }
        if (lastAction != null) {
            Spacer(Modifier.height(8.dp))
            Body(lastAction.summary)
        }
        if (!lastAction?.output.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copiedControlAction) t.copied() else t.copyToolOutput(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyAction,
                )
                SmallButton(
                    t.shareToolOutput(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onShareAction,
                )
            }
        }
    }
}

private fun currentTransparentMode(result: CliResult?): String =
    result
        ?.output
        ?.lineSequence()
        ?.firstOrNull { it.startsWith("mode=") }
        ?.substringAfter("=")
        ?.trim()
        .orEmpty()

fun UiText.controlSummary(
    service: String,
    transparentMode: String,
): String =
    if (transparentMode.isBlank()) {
        service
    } else if (this === UiText.zh) {
        "$service；透明模式 $transparentMode"
    } else {
        "$service; transparent mode $transparentMode"
    }

fun UiText.transparentModeProxy(): String = if (this === UiText.zh) "Proxy" else "Proxy"

fun UiText.transparentModeExternalTun(): String = if (this === UiText.zh) "外部 TUN" else "External TUN"

fun UiText.transparentModeHybrid(): String = if (this === UiText.zh) "Hybrid" else "Hybrid"

fun UiText.ensureService(): String = if (this === UiText.zh) "确保运行" else "Ensure"
