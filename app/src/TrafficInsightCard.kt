package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class TrafficInsight(
    val kind: TrafficInsightKind,
    val level: TrafficInsightLevel,
    val detail: String,
    val recentAverage: Float,
    val latestRate: Float,
    val windowMillis: Long,
    val profile: TrafficProfile,
)

enum class TrafficInsightLevel {
    Waiting,
    Ok,
    Notice,
    Warning,
}

enum class TrafficInsightKind {
    Waiting,
    Stale,
    Spike,
    SustainedHigh,
    UploadHeavy,
    DownloadHeavy,
    Quiet,
    Stable,
}

@Composable
fun TrafficInsightCard(
    samples: List<LiveStats>,
    sampleInterval: TrafficSampleInterval,
    alertThreshold: TrafficAlertThreshold,
    copied: Boolean,
    onThresholdChange: (TrafficAlertThreshold) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val t = LocalUiText.current
    val insight = buildTrafficInsight(samples, sampleInterval, alertThreshold)
    val report = formatTrafficInsight(insight, samples.size, alertThreshold)
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.trafficInsight())
                Body(t.trafficInsightSummary(insight))
            }
            StatusPill(t.trafficInsightLevel(insight.level))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.recentAverage(), formatRate(insight.recentAverage), Modifier.weight(1f))
            TrafficMetricColumn(t.latestRate(), formatRate(insight.latestRate), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.insightWindow(), formatTrafficWindow(insight.windowMillis), Modifier.weight(1f))
            TrafficMetricColumn(t.sampleCount(), samples.size.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TrafficMetricColumn(t.trafficProfile(), t.trafficProfileState(insight.profile.state), Modifier.weight(1f))
            TrafficMetricColumn(t.trafficTrend(), t.trafficTrendState(insight.profile.trend), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Label(t.trafficAlertThreshold())
        Spacer(Modifier.height(6.dp))
        SegmentedControl(
            TrafficAlertThreshold.entries,
            alertThreshold,
            { t.trafficAlertThresholdName(it) },
            onThresholdChange,
        )
        Spacer(Modifier.height(6.dp))
        Body(t.trafficAlertThresholdSummary(alertThreshold))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (copied) t.copied() else t.copyTrafficInsight(),
                enabled = samples.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                onCopy(report)
            }
            SmallButton(
                t.shareTrafficInsight(),
                enabled = samples.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                onShare(report)
            }
        }
    }
}

fun buildTrafficInsight(
    samples: List<LiveStats>,
    sampleInterval: TrafficSampleInterval,
    alertThreshold: TrafficAlertThreshold = TrafficAlertThreshold.Balanced,
    nowMillis: Long = System.currentTimeMillis(),
): TrafficInsight {
    val overview = buildTrafficOverview(samples)
    val profile = buildTrafficProfile(samples, overview)
    val latest = samples.lastOrNull()
    val latestRate = latest?.total ?: 0f
    val recentAverage = samples.takeLast(12).takeIf { it.isNotEmpty() }?.map { it.total }?.average()?.toFloat() ?: 0f
    val sampleAgeMillis = latest?.let { (nowMillis - it.timestampMillis).coerceAtLeast(0L) }
    val staleLimit = sampleInterval.millis * 3L
    val levelAndText =
        when {
            latest == null -> TrafficInsightKind.Waiting to (TrafficInsightLevel.Waiting to "Run one sample or keep auto sampling enabled.")
            sampleAgeMillis != null && sampleAgeMillis > staleLimit ->
                TrafficInsightKind.Stale to (TrafficInsightLevel.Notice to "Latest sample is older than ${formatTrafficWindow(staleLimit)}.")
            latestRate >= recentAverage * 3f && latestRate >= alertThreshold.spikeBytesPerSecond ->
                TrafficInsightKind.Spike to (TrafficInsightLevel.Warning to "Latest rate is much higher than the recent average.")
            recentAverage >= alertThreshold.sustainedBytesPerSecond ->
                TrafficInsightKind.SustainedHigh to
                    (TrafficInsightLevel.Warning to "Recent average stays above ${formatRate(alertThreshold.sustainedBytesPerSecond)}.")
            profile.state == TrafficProfileState.UploadHeavy ->
                TrafficInsightKind.UploadHeavy to (TrafficInsightLevel.Notice to "Estimated upload dominates this sample window.")
            profile.state == TrafficProfileState.DownloadHeavy ->
                TrafficInsightKind.DownloadHeavy to (TrafficInsightLevel.Notice to "Estimated download dominates this sample window.")
            profile.state == TrafficProfileState.Idle ->
                TrafficInsightKind.Quiet to (TrafficInsightLevel.Ok to "Current sample window is mostly idle.")
            else -> TrafficInsightKind.Stable to (TrafficInsightLevel.Ok to "No sustained spike or directional imbalance in recent samples.")
        }
    return TrafficInsight(
        kind = levelAndText.first,
        level = levelAndText.second.first,
        detail = levelAndText.second.second,
        recentAverage = recentAverage,
        latestRate = latestRate,
        windowMillis = overview.windowMillis,
        profile = profile,
    )
}

fun formatTrafficInsight(
    insight: TrafficInsight,
    sampleCount: Int,
    alertThreshold: TrafficAlertThreshold = TrafficAlertThreshold.Balanced,
): String =
    """
    MagicBox traffic insight
    level: ${insight.level.name.lowercase()}
    title: ${insight.kind.reportTitle()}
    detail: ${insight.detail}
    samples: $sampleCount
    window: ${formatTrafficWindow(insight.windowMillis)}
    alert_threshold: ${alertThreshold.name.lowercase()}
    sustained_threshold: ${formatRate(alertThreshold.sustainedBytesPerSecond)}
    spike_floor: ${formatRate(alertThreshold.spikeBytesPerSecond)}
    recent_average: ${formatRate(insight.recentAverage)}
    latest_rate: ${formatRate(insight.latestRate)}
    profile: ${insight.profile.state.name.lowercase()}
    trend: ${insight.profile.trend.name.lowercase()}
    burst_ratio: ${String.format("%.1fx", insight.profile.burstRatio)}
    up_share: ${formatPercent(insight.profile.upShare)}
    down_share: ${formatPercent(insight.profile.downShare)}
    volatility: ${formatPercent(insight.profile.stabilityCv)}
    """.trimIndent()

fun UiText.trafficInsight(): String = if (this === UiText.zh) "流量判断" else "Traffic insight"

fun UiText.trafficInsightSummary(insight: TrafficInsight): String =
    if (this === UiText.zh) {
        when (insight.kind) {
            TrafficInsightKind.Waiting -> "等待真实流量样本。保持自动采样或手动采样一次。"
            TrafficInsightKind.Stale -> "样本已过期，建议刷新状态或恢复自动采样。"
            TrafficInsightKind.Spike -> "检测到突发流量，最新速率明显高于近期平均。"
            TrafficInsightKind.SustainedHigh -> "持续高流量，近期平均超过当前告警阈值。"
            TrafficInsightKind.UploadHeavy -> "上传占比偏高，建议检查后台同步或上传任务。"
            TrafficInsightKind.DownloadHeavy -> "下载占比偏高，建议检查下载任务或代理节点。"
            TrafficInsightKind.Quiet -> "当前采样窗口基本空闲。"
            TrafficInsightKind.Stable -> "近期流量稳定，未发现持续突发或明显方向偏移。"
        }
    } else {
        "${insight.kind.reportTitle()}. ${insight.detail}"
    }

fun UiText.trafficInsightLevel(level: TrafficInsightLevel): String =
    when (level) {
        TrafficInsightLevel.Waiting -> if (this === UiText.zh) "等待" else "Waiting"
        TrafficInsightLevel.Ok -> if (this === UiText.zh) "正常" else "OK"
        TrafficInsightLevel.Notice -> if (this === UiText.zh) "注意" else "Notice"
        TrafficInsightLevel.Warning -> if (this === UiText.zh) "告警" else "Warning"
    }

fun UiText.recentAverage(): String = if (this === UiText.zh) "近期平均" else "Recent average"

fun UiText.latestRate(): String = if (this === UiText.zh) "最新速率" else "Latest"

fun UiText.insightWindow(): String = if (this === UiText.zh) "判断窗口" else "Window"

fun UiText.sampleCount(): String = if (this === UiText.zh) "样本数" else "Samples"

fun UiText.copyTrafficInsight(): String = if (this === UiText.zh) "复制判断" else "Copy insight"

fun UiText.shareTrafficInsight(): String = if (this === UiText.zh) "分享判断" else "Share insight"

private fun TrafficInsightKind.reportTitle(): String =
    when (this) {
        TrafficInsightKind.Waiting -> "Waiting for real traffic samples"
        TrafficInsightKind.Stale -> "Samples are stale"
        TrafficInsightKind.Spike -> "Traffic spike detected"
        TrafficInsightKind.SustainedHigh -> "Sustained high traffic"
        TrafficInsightKind.UploadHeavy -> "Upload-heavy window"
        TrafficInsightKind.DownloadHeavy -> "Download-heavy window"
        TrafficInsightKind.Quiet -> "Quiet traffic"
        TrafficInsightKind.Stable -> "Traffic looks stable"
    }
