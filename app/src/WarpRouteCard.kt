package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WarpRouteCard(
    routes: CliResult?,
    warpStatus: CliResult?,
    domain: String,
    loading: Boolean,
    pendingSelector: WarpSelectorAction?,
    onDomainChange: (String) -> Unit,
    onAddDomain: () -> Unit,
    onRemoveDomain: (String) -> Unit,
    onReload: () -> Unit,
    onRequestSelector: (WarpSelectorAction) -> Unit,
    onCancelSelector: () -> Unit,
    onConfirmSelector: (WarpSelectorAction) -> Unit,
) {
    val t = LocalUiText.current
    val warp = warpStatus?.takeIf { it.success }?.let { parseWarpStatus(it.output) }
    val domains = routes?.takeIf { it.success }?.let { parseWarpRouteDomains(it.output) }.orEmpty()
    val enabled = !loading && warp?.enabled == true
    Card(padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.warpRouting())
                Body(t.warpRoutingSummary(domains.size))
            }
            StatusPill(if (warp?.enabled == true) t.enabled else t.idle)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.globalWarp(), enabled = enabled, modifier = Modifier.weight(1f)) {
                onRequestSelector(WarpSelectorAction.Global)
            }
            SmallButton(t.ruleWarp(), enabled = enabled, modifier = Modifier.weight(1f)) {
                onRequestSelector(WarpSelectorAction.Rule)
            }
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(domain, t.warpDomainPlaceholder(), onDomainChange, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SmallButton(t.add, enabled = !loading, onClick = onAddDomain)
        }
        Spacer(Modifier.height(8.dp))
        if (domains.isEmpty()) {
            Body(t.noWarpRoutes())
        } else {
            domains.take(8).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        DomainRow(item)
                    }
                    Spacer(Modifier.width(8.dp))
                    SmallButton(t.remove, enabled = !loading) { onRemoveDomain(item) }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (domains.size > 8) Body(t.more(domains.size - 8))
        }
        if (pendingSelector != null) {
            Spacer(Modifier.height(8.dp))
            Body(t.confirmWarpSelector(pendingSelector == WarpSelectorAction.Global))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onCancelSelector)
                SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.weight(1f)) {
                    onConfirmSelector(pendingSelector)
                }
            }
        }
    }
}
