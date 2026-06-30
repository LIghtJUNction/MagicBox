package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppStalePolicyCard(
    target: AppTarget,
    staleCount: Int,
    cleanupAvailable: Boolean,
    pendingCleanup: Boolean,
    loading: Boolean,
    onRequestCleanup: () -> Unit,
    onConfirmCleanup: () -> Unit,
    onCancelCleanup: () -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.stalePolicyTitle())
                Body(
                    if (!cleanupAvailable) t.staleCleanupUnavailable()
                    else if (staleCount == 0) t.noStalePackages()
                    else t.stalePolicySummary(staleCount, t.appTarget(target)),
                )
            }
            StatusPill("$staleCount")
        }
        Spacer(Modifier.height(8.dp))
        Body(t.staleCleanupHint())
        Spacer(Modifier.height(8.dp))
        if (pendingCleanup && staleCount > 0) {
            Body(t.confirmStaleCleanup(staleCount, t.appTarget(target)))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onConfirmCleanup)
                SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onCancelCleanup)
            }
        } else {
            SmallButton(
                t.cleanStalePackages(),
                enabled = !loading && cleanupAvailable && staleCount > 0,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestCleanup,
            )
        }
    }
}
