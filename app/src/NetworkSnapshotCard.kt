package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NetworkSnapshotCard(
    result: CliResult?,
    loading: Boolean,
    copied: Boolean,
    onReload: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    val output = result?.output.orEmpty().trim()
    val snapshot = output.takeIf { result?.success == true }?.let(::parseNetworkSnapshotSummary)
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.networkSnapshot())
                Body(result?.summary ?: t.networkSnapshotHint())
            }
            StatusPill(
                when (result?.success) {
                    true -> t.ok
                    false -> t.fail
                    null -> t.idle
                },
            )
        }
        snapshot?.let {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficMetricColumn(t.networkInterfaces(), it.interfaces.toString(), Modifier.weight(1f))
                TrafficMetricColumn(t.networkRules(), it.ipRules.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficMetricColumn(t.networkRoutes(), it.routes.toString(), Modifier.weight(1f))
                TrafficMetricColumn(t.networkNatRules(), it.natRules.toString(), Modifier.weight(1f))
            }
        }
        if (output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(output.take(900))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
            SmallButton(
                if (copied) t.copied() else t.copyReport(),
                enabled = !loading && output.isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = onCopy,
            )
            SmallButton(t.shareReport(), enabled = !loading && output.isNotBlank(), modifier = Modifier.weight(1f), onClick = onShare)
        }
    }
}
