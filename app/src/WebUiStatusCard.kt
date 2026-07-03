package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
fun WebUiStatusCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<CliResult?>(null) }
    var verify by remember { mutableStateOf<CliResult?>(null) }
    var install by remember { mutableStateOf<CliResult?>(null) }
    var panelUrl by remember { mutableStateOf(DefaultWebUiPanelUrl) }
    var copied by remember { mutableStateOf(false) }
    var confirmInstall by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun refresh(command: String = "webui status") {
        loading = true
        confirmInstall = false
        scope.launch {
            val result = runMagicNet(command)
            if (command == "webui status") status = result else verify = result
            copied = false
            loading = false
        }
    }

    fun installPanel() {
        val url = panelUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            install = CliResult(false, "magicnet webui install-local <download-url>", t.webUiInstallInvalidUrl())
            confirmInstall = false
            copied = false
            return
        }
        loading = true
        confirmInstall = false
        copied = false
        scope.launch {
            install = runMagicNetLong("webui install-local ${shellQuote(url)} zashboard")
            status = runMagicNet("webui status")
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        status = runMagicNet("webui status")
    }

    val summary = status?.takeIf { it.success }?.let { parseWebUiStatus(it.output) }
    val output = listOfNotNull(status, verify, install).joinToString("\n\n") { formatToolResult(it) }
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.webUiStatus())
                Body(webUiSummary(t, status, summary))
            }
            StatusPill(if (summary?.localReady == true) t.ready else t.idle)
        }
        summary?.let {
            Spacer(Modifier.height(8.dp))
            Body("${it.version.ifBlank { t.unknown }} · ${it.singBox.ifBlank { t.notReported }}")
        }
        verify?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(700))
        }
        install?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(700))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f)) { refresh() }
            SmallButton(t.webUiVerify(), enabled = !loading, modifier = Modifier.weight(1f)) { refresh("webui verify") }
        }
        Spacer(Modifier.height(8.dp))
        TextInput(
            panelUrl,
            t.webUiInstallUrlPlaceholder(),
            {
                panelUrl = it
                confirmInstall = false
            },
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SmallButton(
            if (confirmInstall) t.confirm() else t.webUiInstallLocal(),
            enabled = !loading && panelUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (confirmInstall) installPanel() else confirmInstall = true
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox WebUI status", output)
                copied = true
            }
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                sharePlainText(context, "MagicBox WebUI status", output)
            }
        }
    }
}

private const val DefaultWebUiPanelUrl = "https://github.com/Zephyruso/zashboard/releases/latest/download/dist.zip"

private data class WebUiStatus(
    val localReady: Boolean,
    val singBox: String,
    val version: String,
)

private fun parseWebUiStatus(output: String): WebUiStatus {
    fun value(key: String): String =
        output
            .lineSequence()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()
    return WebUiStatus(
        localReady = value("local_ready") == "1",
        singBox = value("sing-box"),
        version = value("version"),
    )
}

private fun webUiSummary(
    t: UiText,
    result: CliResult?,
    status: WebUiStatus?,
): String =
    when {
        result == null -> t.notRunYet
        !result.success -> result.summary
        status?.localReady == true -> t.webUiReady()
        else -> t.webUiMissing()
    }
