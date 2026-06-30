package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

data class TrafficOverview(
    val latest: LiveStats?,
    val averageUp: Float,
    val averageDown: Float,
    val peakUp: Float,
    val peakDown: Float,
    val estimatedUpBytes: Long,
    val estimatedDownBytes: Long,
    val windowMillis: Long,
)

data class TrafficProfile(
    val state: TrafficProfileState,
    val trend: TrafficTrendDirection,
    val burstRatio: Float,
    val stabilityCv: Float,
    val upShare: Float,
    val downShare: Float,
)

enum class TrafficProfileState {
    Waiting,
    Idle,
    Stable,
    Bursty,
    UploadHeavy,
    DownloadHeavy,
}

enum class TrafficTrendDirection {
    Waiting,
    Rising,
    Falling,
    Flat,
}

@Composable
fun TrafficOverviewCard(samples: List<LiveStats>) {
    val t = LocalUiText.current
    val overview = buildTrafficOverview(samples)
    val profile = buildTrafficProfile(samples, overview)
    Card {
        Label(t.trafficOverview())
        Body(t.trafficOverviewSummary(samples.size, formatTrafficWindow(overview.windowMillis)))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.currentRate(), formatRate(overview.latest?.total ?: 0f), Modifier.weight(1f))
            TrafficMetricColumn(t.estimatedTransferred(), formatBytes(overview.estimatedUpBytes + overview.estimatedDownBytes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.upAverage(), formatRate(overview.averageUp), Modifier.weight(1f))
            TrafficMetricColumn(t.downAverage(), formatRate(overview.averageDown), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.upPeak(), formatRate(overview.peakUp), Modifier.weight(1f))
            TrafficMetricColumn(t.downPeak(), formatRate(overview.peakDown), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.estimatedUp(), formatBytes(overview.estimatedUpBytes), Modifier.weight(1f))
            TrafficMetricColumn(t.estimatedDown(), formatBytes(overview.estimatedDownBytes), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.trafficProfile(), t.trafficProfileState(profile.state), Modifier.weight(1f))
            TrafficMetricColumn(t.burstRatio(), String.format("%.1fx", profile.burstRatio), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.trafficTrend(), t.trafficTrendState(profile.trend), Modifier.weight(1f))
            TrafficMetricColumn(t.trafficStability(), formatPercent(profile.stabilityCv), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.upShare(), formatPercent(profile.upShare), Modifier.weight(1f))
            TrafficMetricColumn(t.downShare(), formatPercent(profile.downShare), Modifier.weight(1f))
        }
    }
}

fun buildTrafficOverview(samples: List<LiveStats>): TrafficOverview =
    TrafficOverview(
        latest = samples.lastOrNull(),
        averageUp = samples.averageOf { it.up },
        averageDown = samples.averageOf { it.down },
        peakUp = samples.maxOfOrNull { it.up } ?: 0f,
        peakDown = samples.maxOfOrNull { it.down } ?: 0f,
        estimatedUpBytes = samples.estimatedBytes { it.up },
        estimatedDownBytes = samples.estimatedBytes { it.down },
        windowMillis = samples.windowMillis(),
    )

fun buildTrafficProfile(
    samples: List<LiveStats>,
    overview: TrafficOverview = buildTrafficOverview(samples),
): TrafficProfile {
    if (samples.size < 2) {
        return TrafficProfile(TrafficProfileState.Waiting, TrafficTrendDirection.Waiting, 0f, 0f, 0f, 0f)
    }
    val averageTotal = overview.averageUp + overview.averageDown
    val peakTotal = samples.maxOfOrNull { it.total } ?: 0f
    val burstRatio = if (averageTotal > 0f) peakTotal / averageTotal else 0f
    val stabilityCv = samples.stabilityCv(averageTotal)
    val trend = samples.trafficTrend()
    val totalBytes = overview.estimatedUpBytes + overview.estimatedDownBytes
    val upShare = overview.estimatedUpBytes.toShareOf(totalBytes)
    val downShare = overview.estimatedDownBytes.toShareOf(totalBytes)
    val state =
        when {
            peakTotal < 1024f && totalBytes < 64 * 1024L -> TrafficProfileState.Idle
            burstRatio >= 3.0f && peakTotal >= 32 * 1024f -> TrafficProfileState.Bursty
            upShare >= 0.65f && overview.estimatedUpBytes > 256 * 1024L -> TrafficProfileState.UploadHeavy
            downShare >= 0.65f && overview.estimatedDownBytes > 256 * 1024L -> TrafficProfileState.DownloadHeavy
            else -> TrafficProfileState.Stable
        }
    return TrafficProfile(state, trend, burstRatio, stabilityCv, upShare, downShare)
}

private fun List<LiveStats>.averageOf(selector: (LiveStats) -> Float): Float =
    takeIf { it.isNotEmpty() }?.map(selector)?.average()?.toFloat() ?: 0f

private fun List<LiveStats>.estimatedBytes(selector: (LiveStats) -> Float): Long {
    if (size < 2) return 0L
    return zipWithNext().sumOf { (previous, next) ->
        val seconds = (next.timestampMillis - previous.timestampMillis).coerceAtLeast(0L) / 1000.0
        (((selector(previous) + selector(next)) / 2.0) * seconds).toLong().coerceAtLeast(0L)
    }
}

private fun List<LiveStats>.windowMillis(): Long =
    if (size < 2) 0L else (last().timestampMillis - first().timestampMillis).coerceAtLeast(0L)

private fun Long.toShareOf(total: Long): Float =
    if (total <= 0L) 0f else (this.toDouble() / total.toDouble()).toFloat()

private fun List<LiveStats>.stabilityCv(averageTotal: Float): Float {
    if (size < 2 || averageTotal <= 0f) return 0f
    val variance = map { sample -> (sample.total - averageTotal) * (sample.total - averageTotal) }.average()
    return (sqrt(variance) / averageTotal).toFloat().coerceAtLeast(0f)
}

private fun List<LiveStats>.trafficTrend(): TrafficTrendDirection {
    val recent = takeLast(12)
    if (recent.size < 6) return TrafficTrendDirection.Waiting
    val half = recent.size / 2
    val early = recent.take(half).map { it.total }.average().toFloat()
    val late = recent.takeLast(half).map { it.total }.average().toFloat()
    val baseline = maxOf(early, 1024f)
    val delta = (late - early) / baseline
    return when {
        delta >= 0.25f -> TrafficTrendDirection.Rising
        delta <= -0.25f -> TrafficTrendDirection.Falling
        else -> TrafficTrendDirection.Flat
    }
}

fun formatPercent(value: Float): String = String.format("%.0f%%", (value * 100f).coerceAtLeast(0f))

fun formatTrafficWindow(millis: Long): String {
    if (millis <= 0L) return "0s"
    val seconds = (millis / 1000).coerceAtLeast(1)
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val restSeconds = seconds % 60
    return if (minutes < 60) "${minutes}m${restSeconds}s" else "${minutes / 60}h${minutes % 60}m"
}

fun UiText.trafficProfile(): String = if (this === UiText.zh) "流量画像" else "Traffic profile"

fun UiText.burstRatio(): String = if (this === UiText.zh) "峰均比" else "Burst ratio"

fun UiText.trafficTrend(): String = if (this === UiText.zh) "近况趋势" else "Recent trend"

fun UiText.trafficStability(): String = if (this === UiText.zh) "波动系数" else "Volatility"

fun UiText.upShare(): String = if (this === UiText.zh) "上传占比" else "Up share"

fun UiText.downShare(): String = if (this === UiText.zh) "下载占比" else "Down share"

fun UiText.trafficProfileState(state: TrafficProfileState): String =
    when (state) {
        TrafficProfileState.Waiting -> if (this === UiText.zh) "等待样本" else "Waiting"
        TrafficProfileState.Idle -> if (this === UiText.zh) "空闲" else "Idle"
        TrafficProfileState.Stable -> if (this === UiText.zh) "平稳" else "Stable"
        TrafficProfileState.Bursty -> if (this === UiText.zh) "突发" else "Bursty"
        TrafficProfileState.UploadHeavy -> if (this === UiText.zh) "上传偏高" else "Upload-heavy"
        TrafficProfileState.DownloadHeavy -> if (this === UiText.zh) "下载偏高" else "Download-heavy"
    }

fun UiText.trafficTrendState(trend: TrafficTrendDirection): String =
    when (trend) {
        TrafficTrendDirection.Waiting -> if (this === UiText.zh) "等待样本" else "Waiting"
        TrafficTrendDirection.Rising -> if (this === UiText.zh) "上升" else "Rising"
        TrafficTrendDirection.Falling -> if (this === UiText.zh) "下降" else "Falling"
        TrafficTrendDirection.Flat -> if (this === UiText.zh) "平稳" else "Flat"
    }
