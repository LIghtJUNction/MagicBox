package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TrafficSamplerCard(
    autoSample: Boolean,
    sampleCount: Int,
    samples: List<LiveStats>,
    failedSamples: Int,
    averageRate: Float,
    peakRate: Float,
    sampleInterval: TrafficSampleInterval,
    sampleLoading: Boolean,
    statusLoading: Boolean,
    onToggleAuto: () -> Unit,
    onIntervalChange: (TrafficSampleInterval) -> Unit,
    onSampleNow: () -> Unit,
    onRefreshStatus: () -> Unit,
    onCopySamples: () -> Unit,
    onShareSamples: () -> Unit,
    onClear: () -> Unit,
    copiedSamples: Boolean,
    lastSampleFailure: CliResult?,
    copiedSampleFailure: Boolean,
    onCopyFailure: () -> Unit,
    onShareFailure: () -> Unit,
) {
    val t = LocalUiText.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.trafficSamplerTitle())
                Body(t.trafficSamplerSummary(sampleCount, failedSamples))
            }
            StatusPill(if (autoSample) t.live else t.idle)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.averageRate(), formatRate(averageRate), Modifier.weight(1f))
            TrafficMetricColumn(t.peakRate(), formatRate(peakRate), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Label(t.sampleInterval())
        Spacer(Modifier.height(6.dp))
        SegmentedControl(TrafficSampleInterval.entries, sampleInterval, { it.label }, onIntervalChange)
        Spacer(Modifier.height(6.dp))
        Body(t.sampleIntervalSummary(sampleInterval))
        TrafficTrendChart(samples)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (autoSample) t.pause() else t.resume(), enabled = true, modifier = Modifier.weight(1f), onClick = onToggleAuto)
            SmallButton(t.sampleNow(), enabled = !sampleLoading, modifier = Modifier.weight(1f), onClick = onSampleNow)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.sync, enabled = !statusLoading, modifier = Modifier.weight(1f), onClick = onRefreshStatus)
            SmallButton(t.clear(), enabled = sampleCount > 0, modifier = Modifier.weight(1f), onClick = onClear)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (copiedSamples) t.copied() else t.copyTrafficSamples(), enabled = sampleCount > 0, modifier = Modifier.weight(1f), onClick = onCopySamples)
            SmallButton(t.shareTrafficSamples(), enabled = sampleCount > 0, modifier = Modifier.weight(1f), onClick = onShareSamples)
        }
        if (!lastSampleFailure?.output.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Body(t.lastSampleFailure())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copiedSampleFailure) t.copied() else t.copySampleFailure(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyFailure,
                )
                SmallButton(
                    t.shareSampleFailure(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onShareFailure,
                )
            }
        }
    }
}

@Composable
fun TrafficMetricColumn(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Label(title)
        Body(value)
    }
}
