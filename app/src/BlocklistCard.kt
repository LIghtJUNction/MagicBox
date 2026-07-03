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
fun BlocklistCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<CliResult?>(null) }
    var lastAction by remember { mutableStateOf<CliResult?>(null) }
    var domain by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        scope.launch {
            status = runMagicNet("block list")
            loading = false
        }
    }

    fun runBlock(command: String, longRunning: Boolean = false) {
        loading = true
        copied = false
        scope.launch {
            lastAction = if (longRunning) runMagicNetLong(command) else runMagicNet(command)
            status = runMagicNet("block list")
            loading = false
        }
    }

    fun addDomain() {
        val clean = domain.trim().lowercase()
        if (!isSafeDomain(clean)) {
            lastAction = CliResult(false, "$MAGICNET_CLI block add-domain <domain>", t.invalidDomain(domain))
            copied = false
            return
        }
        runBlock("block add-domain $clean")
        domain = ""
    }

    fun saveUrl() {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            lastAction = CliResult(false, "$MAGICNET_CLI block url <http-url>", t.invalidBlocklistUrl())
            copied = false
            return
        }
        runBlock("block url ${shellQuote(clean)}")
    }

    LaunchedEffect(Unit) {
        status = runMagicNet("block list")
    }

    val output = listOfNotNull(status, lastAction).joinToString("\n\n") { formatToolResult(it) }
    val enabled = blockValue(status?.output.orEmpty(), "enabled") != "0"
    val community = blockValue(status?.output.orEmpty(), "community") != "0"
    val currentUrl = blockValue(status?.output.orEmpty(), "url")

    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.blocklist())
                Body(t.blocklistSummary(if (enabled) t.enabled() else t.disabled(), if (community) t.enabled() else t.disabled()))
            }
            StatusPill(if (enabled) t.running else t.idle)
        }
        if (currentUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(currentUrl.take(140))
        }
        Spacer(Modifier.height(8.dp))
        TextInput(domain, t.blockDomainPlaceholder(), { domain = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.add, enabled = !loading, modifier = Modifier.weight(1f), onClick = ::addDomain)
            SmallButton(t.blockDiff(), enabled = !loading, modifier = Modifier.weight(1f)) { runBlock("block diff") }
        }
        Spacer(Modifier.height(8.dp))
        TextInput(url, t.blockUrlPlaceholder(), { url = it })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.saveUrl(), enabled = !loading, modifier = Modifier.weight(1f), onClick = ::saveUrl)
            SmallButton(t.updateBlocklist(), enabled = !loading, modifier = Modifier.weight(1f)) { runBlock("block update", longRunning = true) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (enabled) t.disableBlocklist() else t.enableBlocklist(), enabled = !loading, modifier = Modifier.weight(1f)) {
                runBlock(if (enabled) "block disable" else "block enable")
            }
            SmallButton(if (community) t.disableCommunityBlocklist() else t.enableCommunityBlocklist(), enabled = !loading, modifier = Modifier.weight(1f)) {
                runBlock(if (community) "block community off" else "block community on")
            }
        }
        lastAction?.takeIf { it.output.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(700))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = ::refresh)
            SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox blocklist", output)
                copied = true
            }
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                sharePlainText(context, "MagicBox blocklist", output)
            }
        }
    }
}

private fun blockValue(text: String, key: String): String =
    text.lineSequence()
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        .orEmpty()
