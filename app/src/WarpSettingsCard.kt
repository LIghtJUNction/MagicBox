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
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun WarpSettingsCard(
    result: CliResult?,
    loading: Boolean,
    pendingAction: WarpAction?,
    onReload: () -> Unit,
    onTest: () -> Unit,
    onRequestAction: (WarpAction) -> Unit,
    onCancelAction: () -> Unit,
    onConfirmAction: (WarpAction) -> Unit,
) {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf<CliResult?>(null) }
    var importing by remember { mutableStateOf(false) }
    val status = result?.takeIf { it.success }?.let { parseWarpStatus(it.output) }
    fun importWarpConfig() {
        val text = importText.trim()
        if (text.isBlank()) {
            importResult = CliResult(false, "$MAGICNET_CLI warp import-file", t.warpImportMissing())
            return
        }
        importing = true
        scope.launch {
            val file = File(context.cacheDir, "magicnet-warp.conf")
            runCatching { file.writeText(text) }
                .onFailure { importResult = CliResult(false, "$MAGICNET_CLI warp import-file", it.message ?: "write failed") }
                .onSuccess {
                    importResult = runMagicNetLong("warp import-file ${shellQuote(file.absolutePath)}")
                    if (importResult?.success == true) {
                        importText = ""
                        onReload()
                    }
                }
            file.delete()
            importing = false
        }
    }

    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.warp())
                Body(warpSummary(t, result, status))
            }
            StatusPill(if (status?.enabled == true) t.enabled else if (status?.configured == true) t.ready else t.idle)
        }
        if (status != null && status.configured) {
            Spacer(Modifier.height(8.dp))
            Body(t.warpEndpoint(status.endpoint, status.addresses, status.allowedIps))
        } else if (result?.success == false && result.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(result.output.take(700))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
            SmallButton(t.testWarp(), enabled = !loading && status?.enabled == true, modifier = Modifier.weight(1f), onClick = onTest)
            SmallButton(
                if (status?.enabled == true) t.disableWarp() else t.enableWarp(),
                enabled = !loading && status?.configured == true,
                modifier = Modifier.weight(1f),
            ) {
                onRequestAction(if (status?.enabled == true) WarpAction.Disable else WarpAction.Enable)
            }
        }
        Spacer(Modifier.height(8.dp))
        MultiLineTextInput(
            value = importText,
            placeholder = t.warpImportPlaceholder(),
            onValueChange = { importText = it },
        )
        Spacer(Modifier.height(8.dp))
        SmallButton(
            t.importWarpConfig(),
            enabled = !loading && !importing && importText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = ::importWarpConfig,
        )
        importResult?.takeIf { it.output.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(700))
        }
        if (pendingAction != null) {
            Spacer(Modifier.height(8.dp))
            Body(t.confirmWarpAction(pendingAction == WarpAction.Enable))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onCancelAction)
                SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.weight(1f)) {
                    onConfirmAction(pendingAction)
                }
            }
        }
    }
}

private fun warpSummary(
    t: UiText,
    result: CliResult?,
    status: WarpStatus?,
): String =
    when {
        result == null -> t.loadingWarp()
        !result.success -> result.summary
        status == null -> result.summary
        !status.configured -> t.warpNotConfigured()
        status.enabled -> t.warpEnabled(status.tag)
        else -> t.warpConfigured(status.tag)
    }
