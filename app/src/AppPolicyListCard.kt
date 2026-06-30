package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppPolicyListCard(
    summary: AppSummary?,
    appPolicy: CliResult?,
    target: AppTarget,
    policyFilter: String,
    filteredSelected: List<String>,
    copiedPolicy: Boolean,
    copiedRemovalPlan: Boolean,
    pendingVisibleRemoval: Boolean,
    loading: Boolean,
    onPolicyFilterChange: (String) -> Unit,
    onCopyVisiblePackages: () -> Unit,
    onShareVisiblePackages: () -> Unit,
    onRequestVisibleRemoval: () -> Unit,
    onConfirmVisibleRemoval: () -> Unit,
    onCancelVisibleRemoval: () -> Unit,
    onCopyRemovalPlan: () -> Unit,
    onShareRemovalPlan: () -> Unit,
    onRemovePackage: (String) -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CountBadge(t.appTarget(AppTarget.Proxy).uppercase(), summary?.proxy?.size ?: 0, MagicPalette.rose, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            CountBadge(t.appTarget(AppTarget.Bypass).uppercase(), summary?.bypass?.size ?: 0, MagicPalette.green, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                policyFilter,
                t.filterPolicy,
                onPolicyFilterChange,
                Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            StatusPill("${filteredSelected.size}")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (copiedPolicy) t.copied() else t.copyVisiblePackages(),
                enabled = filteredSelected.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = onCopyVisiblePackages,
            )
            SmallButton(
                t.shareVisiblePackages(),
                enabled = filteredSelected.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = onShareVisiblePackages,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (pendingVisibleRemoval && filteredSelected.isNotEmpty()) {
            Body(t.confirmRemoveVisiblePackages(filteredSelected.size, t.appTarget(target)))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onConfirmVisibleRemoval)
                SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onCancelVisibleRemoval)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copiedRemovalPlan) t.copied() else t.copyAppPolicyRemovalPlan(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyRemovalPlan,
                )
                SmallButton(
                    t.shareAppPolicyRemovalPlan(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onShareRemovalPlan,
                )
            }
        } else {
            SmallButton(
                t.removeVisiblePackages(),
                enabled = !loading && filteredSelected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestVisibleRemoval,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            summary == null -> Body(appPolicy?.summary ?: t.loadingAppPolicy)
            filteredSelected.isEmpty() -> Body(t.noPackages(t.appTarget(target).lowercase()))
            else -> {
                filteredSelected.take(90).forEach { pkg ->
                    ManageRow(
                        title = pkg,
                        detail = t.inPolicy(t.appTarget(target)),
                        action = t.remove,
                        enabled = !loading,
                        onAction = { onRemovePackage(pkg) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (filteredSelected.size > 90) Body(t.more(filteredSelected.size - 90))
            }
        }
    }
}
