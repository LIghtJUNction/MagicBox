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

@Composable
fun EcaptureControlCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var seconds by remember { mutableStateOf("8") }
    var pid by remember { mutableStateOf("all") }
    var uid by remember { mutableStateOf("all") }
    var ifname by remember { mutableStateOf("wlan0") }
    var filter by remember { mutableStateOf("tcp port 443") }
    var result by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    fun run(args: String, longRunning: Boolean = false) {
        loading = true
        copied = false
        scope.launch {
            result = if (longRunning) runMagicNetLong("ecapture $args") else runMagicNet("ecapture $args")
            loading = false
        }
    }

    fun runTextCapture(command: String) {
        val captureSeconds = parseCaptureSeconds(seconds) ?: run {
            result = CliResult(false, "magicnet ecapture $command [seconds] [pid|all] [uid|all]", t.invalidCaptureSeconds())
            return
        }
        val cleanPid = parseCaptureId(pid) ?: run {
            result = CliResult(false, "magicnet ecapture $command $captureSeconds [pid|all] [uid|all]", t.invalidCapturePid())
            return
        }
        val cleanUid = parseCaptureId(uid) ?: run {
            result = CliResult(false, "magicnet ecapture $command $captureSeconds $cleanPid [uid|all]", t.invalidCaptureUid())
            return
        }
        run("$command $captureSeconds $cleanPid $cleanUid", longRunning = true)
    }

    fun runPcapCapture() {
        val captureSeconds = parseCaptureSeconds(seconds) ?: run {
            result = CliResult(false, "magicnet ecapture pcap [seconds] <ifname>", t.invalidCaptureSeconds())
            return
        }
        val cleanIfname = ifname.trim()
        if (!cleanIfname.matches(Regex("""[A-Za-z0-9_.:-]{1,32}"""))) {
            result = CliResult(false, "magicnet ecapture pcap $captureSeconds <ifname>", t.invalidCaptureInterface())
            return
        }
        val filterArgs =
            filter
                .trim()
                .split(Regex("""\s+"""))
                .filter { it.isNotBlank() }
                .take(12)
                .joinToString(" ") { shellQuote(it) }
        run("pcap $captureSeconds ${shellQuote(cleanIfname)} $filterArgs".trim(), longRunning = true)
    }

    val output = result?.output.orEmpty().trim()
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.ecaptureControl())
                Body(result?.summary ?: t.ecaptureSummary())
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.ecaptureStatus(), enabled = !loading, modifier = Modifier.weight(1f)) { run("status") }
            SmallButton(t.ecaptureVersion(), enabled = !loading, modifier = Modifier.weight(1f)) { run("version") }
            SmallButton(t.ecaptureHelp(), enabled = !loading, modifier = Modifier.weight(1f)) { run("help tls") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(seconds, t.captureSeconds(), { seconds = it }, Modifier.weight(1f))
            TextInput(pid, t.capturePid(), { pid = it }, Modifier.weight(1f))
            TextInput(uid, t.captureUid(), { uid = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("TLS", enabled = !loading, modifier = Modifier.weight(1f)) { runTextCapture("tls") }
            SmallButton("GoTLS", enabled = !loading, modifier = Modifier.weight(1f)) { runTextCapture("gotls") }
            SmallButton("NSPR", enabled = !loading, modifier = Modifier.weight(1f)) { runTextCapture("nspr") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(ifname, t.captureInterface(), { ifname = it }, Modifier.weight(1f))
            TextInput(filter, t.captureFilter(), { filter = it }, Modifier.weight(2f))
        }
        Spacer(Modifier.height(8.dp))
        SmallButton(t.capturePcap(), enabled = !loading, modifier = Modifier.fillMaxWidth(), onClick = ::runPcapCapture)
        if (output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(output.take(900))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(if (copied) t.copied() else t.copyReport(), enabled = true, modifier = Modifier.weight(1f)) {
                    copyPlainText(context, "MagicBox eCapture", formatToolResult(result!!))
                    copied = true
                }
                SmallButton(t.shareReport(), enabled = true, modifier = Modifier.weight(1f)) {
                    sharePlainText(context, "MagicBox eCapture", formatToolResult(result!!))
                }
            }
        }
    }
}

private fun parseCaptureSeconds(value: String): Int? {
    val parsed = value.trim().toIntOrNull() ?: return null
    return parsed.takeIf { it in 1..30 }
}

private fun parseCaptureId(value: String): String? {
    val clean = value.trim()
    return when {
        clean.isBlank() || clean.equals("all", ignoreCase = true) -> "all"
        clean.toUIntOrNull() != null -> clean
        else -> null
    }
}
