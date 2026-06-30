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
fun DnsSettingsCard(
    result: CliResult?,
    loading: Boolean,
    pendingProfile: DnsProfile?,
    onReload: () -> Unit,
    onRequestProfile: (DnsProfile) -> Unit,
    onCancelProfile: () -> Unit,
    onConfirmProfile: (DnsProfile) -> Unit,
) {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testDomain by remember { mutableStateOf("www.gstatic.com") }
    var testResult by remember { mutableStateOf<CliResult?>(null) }
    var testing by remember { mutableStateOf(false) }
    var copiedTest by remember { mutableStateOf(false) }
    val status = result?.takeIf { it.success }?.let { parseDnsStatus(it.output) }
    fun runDnsTest() {
        testing = true
        copiedTest = false
        scope.launch {
            testResult = runMagicNetLong("dns test ${shellQuote(testDomain.trim())}")
            testing = false
        }
    }

    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.dnsSettings())
                Body(dnsSummary(t, result, status))
            }
            StatusPill(status?.transport?.uppercase() ?: t.idle)
        }
        if (status != null) {
            Spacer(Modifier.height(8.dp))
            Body(t.dnsEndpoint(status.primary, status.secondary))
        } else if (result?.success == false && result.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(result.output.take(700))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DnsProfile.entries.take(2).forEach { profile ->
                SmallButton(dnsProfileLabel(t, profile), enabled = !loading, modifier = Modifier.weight(1f)) {
                    onRequestProfile(profile)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DnsProfile.entries.drop(2).forEach { profile ->
                SmallButton(dnsProfileLabel(t, profile), enabled = !loading, modifier = Modifier.weight(1f)) {
                    onRequestProfile(profile)
                }
            }
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
        }
        Spacer(Modifier.height(8.dp))
        TextInput(
            value = testDomain,
            placeholder = t.dnsTestDomainPlaceholder(),
            onValueChange = {
                testDomain = it
                copiedTest = false
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.testDns(), enabled = !testing && testDomain.isNotBlank(), modifier = Modifier.weight(1f), onClick = ::runDnsTest)
            SmallButton(
                if (copiedTest) t.copied() else t.copyReport(),
                enabled = !testing && !testResult?.output.isNullOrBlank(),
                modifier = Modifier.weight(1f),
            ) {
                testResult?.let {
                    copyPlainText(context, "MagicBox DNS test", formatToolResult(it))
                    copiedTest = true
                }
            }
            SmallButton(t.shareReport(), enabled = !testing && !testResult?.output.isNullOrBlank(), modifier = Modifier.weight(1f)) {
                testResult?.let { sharePlainText(context, "MagicBox DNS test", formatToolResult(it)) }
            }
        }
        testResult?.takeIf { it.output.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(700))
        }
        if (pendingProfile != null) {
            Spacer(Modifier.height(8.dp))
            Body(t.confirmDnsProfile(dnsProfileLabel(t, pendingProfile)))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onCancelProfile)
                SmallButton(
                    t.confirm(),
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    onConfirmProfile(pendingProfile)
                }
            }
        }
    }
}

private fun dnsSummary(
    t: UiText,
    result: CliResult?,
    status: DnsStatus?,
): String =
    when {
        result == null -> t.loadingDns()
        !result.success -> result.summary
        status == null -> result.summary
        else -> t.dnsProfileSummary(status.profile, status.transport)
    }
