package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class TrafficActionPlan(
    val kind: TrafficActionKind,
    val level: TrafficInsightLevel,
    val commands: List<String>,
    val sampleCount: Int,
    val latestRate: Float,
    val recentAverage: Float,
    val windowMillis: Long,
)

enum class TrafficActionKind {
    CollectSamples,
    RefreshStaleSamples,
    InspectSpike,
    ReduceSustainedTraffic,
    InspectUploadHeavy,
    InspectDownloadHeavy,
    KeepMonitoring,
}

@Composable
fun TrafficActionPlanCard(
    samples: List<LiveStats>,
    sampleInterval: TrafficSampleInterval,
    alertThreshold: TrafficAlertThreshold,
    copied: Boolean,
    diagnosticResult: CliResult?,
    copiedDiagnostics: Boolean,
    sampleLoading: Boolean,
    statusLoading: Boolean,
    diagnosticsLoading: Boolean,
    onSampleNow: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRunDiagnostics: (TrafficActionPlan) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopyDiagnostics: () -> Unit,
    onShareDiagnostics: () -> Unit,
) {
    val t = LocalUiText.current
    val plan = buildTrafficActionPlan(samples, sampleInterval, alertThreshold)
    val report = formatTrafficActionPlan(plan)
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrafficMetricColumn(t.trafficActionPlan(), t.trafficActionPlanTitle(plan.kind), Modifier.weight(1f))
            TrafficMetricColumn(t.priority(), t.trafficInsightLevel(plan.level), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Body(t.trafficActionPlanSummary(plan.kind))
        Spacer(Modifier.height(8.dp))
        plan.commands.take(4).forEach { command ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mono(command)
                StatusPill(t.trafficCommandSafety(trafficCommandSafety(command)))
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.sampleNow(), enabled = !sampleLoading, modifier = Modifier.weight(1f), onClick = onSampleNow)
            SmallButton(t.sync, enabled = !statusLoading, modifier = Modifier.weight(1f), onClick = onRefreshStatus)
        }
        Spacer(Modifier.height(8.dp))
        SmallButton(
            if (diagnosticsLoading) t.running else t.runTrafficDiagnostics(),
            enabled = !diagnosticsLoading && trafficDiagnosticCommands(plan).isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) {
            onRunDiagnostics(plan)
        }
        val diagnosticOutput = diagnosticResult?.output.orEmpty()
        if (diagnosticOutput.isNotBlank()) {
            val diagnosticStatus = parseTrafficDiagnosticsStatus(diagnosticOutput)
            Spacer(Modifier.height(8.dp))
            Body(diagnosticResult?.summary.orEmpty())
            diagnosticStatus?.let {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrafficMetricColumn(t.trafficDiagnosticsCommands(), "${it.succeeded}/${it.commands}", Modifier.weight(1f))
                    TrafficMetricColumn(t.trafficDiagnosticsSnapshot(), "${it.snapshotSucceeded}/${it.snapshotCommands}", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copiedDiagnostics) t.copied() else t.copyTrafficDiagnostics(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyDiagnostics,
                )
                SmallButton(
                    t.shareTrafficDiagnostics(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onShareDiagnostics,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (copied) t.copied() else t.copyTrafficActionPlan(),
                enabled = plan.commands.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                onCopy(report)
            }
            SmallButton(
                t.shareTrafficActionPlan(),
                enabled = plan.commands.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                onShare(report)
            }
        }
    }
}

fun buildTrafficActionPlan(
    samples: List<LiveStats>,
    sampleInterval: TrafficSampleInterval,
    alertThreshold: TrafficAlertThreshold = TrafficAlertThreshold.Balanced,
    nowMillis: Long = System.currentTimeMillis(),
): TrafficActionPlan {
    val insight = buildTrafficInsight(samples, sampleInterval, alertThreshold, nowMillis)
    val kind =
        when (insight.kind) {
            TrafficInsightKind.Waiting -> TrafficActionKind.CollectSamples
            TrafficInsightKind.Stale -> TrafficActionKind.RefreshStaleSamples
            TrafficInsightKind.Spike -> TrafficActionKind.InspectSpike
            TrafficInsightKind.SustainedHigh -> TrafficActionKind.ReduceSustainedTraffic
            TrafficInsightKind.UploadHeavy -> TrafficActionKind.InspectUploadHeavy
            TrafficInsightKind.DownloadHeavy -> TrafficActionKind.InspectDownloadHeavy
            TrafficInsightKind.Quiet,
            TrafficInsightKind.Stable,
            -> TrafficActionKind.KeepMonitoring
        }
    return TrafficActionPlan(
        kind = kind,
        level = insight.level,
        commands = trafficActionCommands(kind),
        sampleCount = samples.size,
        latestRate = insight.latestRate,
        recentAverage = insight.recentAverage,
        windowMillis = insight.windowMillis,
    )
}

fun formatTrafficActionPlan(plan: TrafficActionPlan): String =
    buildString {
        appendLine("MagicBox traffic action plan")
        appendLine("kind: ${plan.kind.name}")
        appendLine("level: ${plan.level.name.lowercase()}")
        appendLine("samples: ${plan.sampleCount}")
        appendLine("window: ${formatTrafficWindow(plan.windowMillis)}")
        appendLine("latest_rate: ${formatRate(plan.latestRate)}")
        appendLine("recent_average: ${formatRate(plan.recentAverage)}")
        appendLine()
        appendLine("commands:")
        plan.commands.forEachIndexed { index, command ->
            appendLine("${index + 1}. $command")
            appendLine("   safety: ${trafficCommandSafety(command).name}")
        }
    }.trim()

internal fun trafficActionCommands(kind: TrafficActionKind): List<String> =
    when (kind) {
        TrafficActionKind.CollectSamples -> listOf("api stats")
        TrafficActionKind.RefreshStaleSamples -> listOf("api stats", "health", "service status", "transparent status")
        TrafficActionKind.InspectSpike -> listOf("api conns", "api close-top 3", "api groups")
        TrafficActionKind.ReduceSustainedTraffic -> listOf("api conns", "api close-top 3", "api groups", "health")
        TrafficActionKind.InspectUploadHeavy -> listOf("api conns", "api groups", "app list")
        TrafficActionKind.InspectDownloadHeavy -> listOf("api conns", "api groups", "api close-top 3")
        TrafficActionKind.KeepMonitoring -> listOf("api stats", "health")
    }

fun UiText.trafficActionPlan(): String = if (this === UiText.zh) "处置建议" else "Action plan"

fun UiText.priority(): String = if (this === UiText.zh) "优先级" else "Priority"

fun UiText.copyTrafficActionPlan(): String = if (this === UiText.zh) "复制建议" else "Copy plan"

fun UiText.shareTrafficActionPlan(): String = if (this === UiText.zh) "分享建议" else "Share plan"

fun UiText.runTrafficDiagnostics(): String = if (this === UiText.zh) "运行诊断" else "Run diagnostics"

fun UiText.copyTrafficDiagnostics(): String = if (this === UiText.zh) "复制诊断" else "Copy diagnostics"

fun UiText.shareTrafficDiagnostics(): String = if (this === UiText.zh) "分享诊断" else "Share diagnostics"

fun UiText.trafficDiagnosticsCommands(): String = if (this === UiText.zh) "诊断命令" else "Diagnostics"

fun UiText.trafficDiagnosticsSnapshot(): String = if (this === UiText.zh) "回读快照" else "Snapshot"

fun UiText.trafficCommandSafety(safety: TrafficCommandSafety): String =
    when (safety) {
        TrafficCommandSafety.ReadOnly -> if (this === UiText.zh) "只读" else "Read-only"
        TrafficCommandSafety.ManualConfirm -> if (this === UiText.zh) "需确认" else "Confirm"
    }

fun UiText.trafficActionPlanTitle(kind: TrafficActionKind): String =
    when (kind) {
        TrafficActionKind.CollectSamples -> if (this === UiText.zh) "先采集真实样本" else "Collect real samples"
        TrafficActionKind.RefreshStaleSamples -> if (this === UiText.zh) "刷新过期状态" else "Refresh stale status"
        TrafficActionKind.InspectSpike -> if (this === UiText.zh) "排查突发连接" else "Inspect spike"
        TrafficActionKind.ReduceSustainedTraffic -> if (this === UiText.zh) "压低持续高流量" else "Reduce sustained traffic"
        TrafficActionKind.InspectUploadHeavy -> if (this === UiText.zh) "检查上传来源" else "Inspect upload source"
        TrafficActionKind.InspectDownloadHeavy -> if (this === UiText.zh) "检查下载来源" else "Inspect download source"
        TrafficActionKind.KeepMonitoring -> if (this === UiText.zh) "继续监控" else "Keep monitoring"
    }

fun UiText.trafficActionPlanSummary(kind: TrafficActionKind): String =
    if (this === UiText.zh) {
        when (kind) {
            TrafficActionKind.CollectSamples -> "当前没有可判断样本，先读取一次真实 api stats。"
            TrafficActionKind.RefreshStaleSamples -> "样本时间已过期，刷新采样和服务状态后再做处置。"
            TrafficActionKind.InspectSpike -> "先拉取当前连接，必要时关闭流量最高的少量连接。"
            TrafficActionKind.ReduceSustainedTraffic -> "持续高流量需要同时检查连接、节点组和健康状态。"
            TrafficActionKind.InspectUploadHeavy -> "上传偏高时优先检查连接、节点组和应用策略名单。"
            TrafficActionKind.InspectDownloadHeavy -> "下载偏高时优先查看连接明细和节点组，必要时关闭高流量连接。"
            TrafficActionKind.KeepMonitoring -> "当前没有高风险信号，保持采样并定期检查健康状态。"
        }
    } else {
        when (kind) {
            TrafficActionKind.CollectSamples -> "No usable samples yet; read real api stats first."
            TrafficActionKind.RefreshStaleSamples -> "Samples are stale; refresh traffic and service status before acting."
            TrafficActionKind.InspectSpike -> "Fetch current connections, then close a small number of top traffic connections if needed."
            TrafficActionKind.ReduceSustainedTraffic -> "Sustained high traffic needs connection, proxy group, and health checks together."
            TrafficActionKind.InspectUploadHeavy -> "Upload-heavy windows should start with connections, groups, and app policies."
            TrafficActionKind.InspectDownloadHeavy -> "Download-heavy windows should inspect connections and groups, then close top traffic if needed."
            TrafficActionKind.KeepMonitoring -> "No high-risk signal; keep sampling and check health periodically."
        }
    }
