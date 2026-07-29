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
    var panelSha256 by remember { mutableStateOf(DefaultWebUiPanelSha256) }
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
        val sha256 = panelSha256.trim()
        if (!url.startsWith("https://") || !isSha256(sha256)) {
            install = CliResult(
                false,
                "magicnet webui install-local <https-url> <sha256> [name]",
                "需要 HTTPS 下载链接和 64 位十六进制 SHA-256 校验值。",
            )
            confirmInstall = false
            copied = false
            return
        }
        loading = true
        confirmInstall = false
        copied = false
        scope.launch {
            install = runMagicNetLong("webui install-local ${shellQuote(url)} ${shellQuote(sha256)} zashboard")
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
        TextInput(
            panelSha256,
            "面板包 SHA-256（64 位十六进制）",
            {
                panelSha256 = it
                confirmInstall = false
            },
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SmallButton(
            if (confirmInstall) t.confirm() else t.webUiInstallLocal(),
            enabled = !loading && panelUrl.isNotBlank() && panelSha256.isNotBlank(),
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

private const val DefaultWebUiPanelUrl = "https://github.com/Zephyruso/zashboard/releases/download/v3.16.0/dist.zip"
private const val DefaultWebUiPanelSha256 = "d103652ee04e9d73017230f483e0cb8875bf4bdf2c139faae47300ba7f5dfd16"

private fun isSha256(value: String): Boolean =
    value.length == 64 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

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
