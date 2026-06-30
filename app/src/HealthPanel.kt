package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HealthPanel(
    result: CliResult?,
    copied: Boolean,
    copiedIssues: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCopyIssues: (String) -> Unit,
    onShareIssues: (String) -> Unit,
) {
    val t = LocalUiText.current
    var showAllEntries by remember(result?.output) { mutableStateOf(false) }
    var entryFilter by remember(result?.output) { mutableStateOf(HealthEntryFilter.Issues) }
    val entries = prioritizeHealthEntries(parseHealthEntries(result?.output.orEmpty()))
    val issueSummary = buildHealthIssueSummary(entries)
    val effectiveFilter = if (issueSummary.hasIssues) entryFilter else HealthEntryFilter.All
    val displayEntries = filterHealthEntries(entries, effectiveFilter)
    val visibleEntries = if (showAllEntries) displayEntries else displayEntries.take(3)
    val counts = buildHealthSeverityCounts(entries)
    val issueReport = formatHealthIssueSummary(issueSummary, t)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.moduleHealth)
                Body(
                    when {
                        result == null -> t.waitingCliHealth
                        result.success && entries.isNotEmpty() -> t.healthLong(counts.ok, counts.warn, counts.error)
                        else -> result.summary
                    },
                )
            }
            StatusPill(
                when {
                    result == null -> t.check
                    !result.success || counts.error > 0 -> t.fail
                    counts.warn > 0 -> t.warn
                    else -> t.ok
                },
            )
        }
        if (!result?.output.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copied) t.copied() else t.copyToolOutput(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onCopy,
                )
                SmallButton(
                    t.shareToolOutput(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onShare,
                )
            }
        }
        if (result?.success == true && entries.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficMetricColumn(t.healthOkLabel(), counts.ok.toString(), Modifier.weight(1f))
                TrafficMetricColumn(t.healthWarnLabel(), counts.warn.toString(), Modifier.weight(1f))
                TrafficMetricColumn(t.healthErrorLabel(), counts.error.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Body(t.healthIssueSummary(issueSummary))
            if (issueSummary.hasIssues) {
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    HealthEntryFilter.entries,
                    effectiveFilter,
                    { t.healthEntryFilter(it) },
                ) {
                    entryFilter = it
                    showAllEntries = false
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(
                        if (copiedIssues) t.copied() else t.copyHealthIssues(),
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        onCopyIssues(issueReport)
                    }
                    SmallButton(
                        t.shareHealthIssues(),
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        onShareIssues(issueReport)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            visibleEntries.forEach { entry ->
                HealthLine(entry)
                Spacer(Modifier.height(6.dp))
            }
            if (displayEntries.size > 3) {
                SmallButton(
                    if (showAllEntries) t.collapseHealthEntries() else t.showAllHealthEntries(displayEntries.size),
                    enabled = true,
                    onClick = { showAllEntries = !showAllEntries },
                )
            }
        } else if (result?.success == false && result.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(result.output.take(900))
        }
    }
}
