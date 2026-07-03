package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IssueDraftCard(
    draft: String,
    copied: Boolean,
    showFullDraft: Boolean,
    loading: Boolean,
    onReload: () -> Unit,
    onRefreshReport: () -> Unit,
    onDraft: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Label(t.diffIssue)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
            SmallButton(t.refreshSupportReport(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onRefreshReport)
            SmallButton(t.draftFromCurrent(), enabled = !loading, modifier = Modifier.weight(1f), onClick = onDraft)
        }
        if (draft.isNotBlank()) {
            val summary = parseSupportReportSummary(draft)
            if (summary.total > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(t.supportReportOk(summary.ok))
                    StatusPill(t.supportReportNeedsCheck(summary.needsCheck))
                }
                if (summary.needsCheckTitles.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Body(t.supportReportNeedsCheckList(summary.needsCheckTitles.take(4), summary.needsCheckTitles.drop(4).size))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copied) t.copied() else t.copyReport(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onCopy,
                )
                SmallButton(
                    t.shareReport(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onShare,
                )
            }
            Spacer(Modifier.height(8.dp))
            SmallButton(
                if (showFullDraft) t.collapseReport() else t.showFullReport(),
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onTogglePreview,
            )
            Spacer(Modifier.height(8.dp))
            Mono(if (showFullDraft) draft else draft.take(900))
        }
    }
}

fun UiText.refreshSupportReport(): String = if (this === UiText.zh) "刷新报告" else "Refresh report"

fun UiText.draftFromCurrent(): String = if (this === UiText.zh) "使用当前状态" else "Use current"
