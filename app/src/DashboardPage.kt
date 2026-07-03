package com.github.lightjunction.magicbox

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StatsPage(onTrafficRateChange: (Float) -> Unit) {
    val t = LocalUiText.current
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<LiveStats>() }
    var health by remember { mutableStateOf<CliResult?>(null) }
    var stats by remember { mutableStateOf<CliResult?>(null) }
    var service by remember { mutableStateOf<CliResult?>(null) }
    var transparent by remember { mutableStateOf<CliResult?>(null) }
    var lastAction by remember { mutableStateOf<CliResult?>(null) }
    var pendingDangerAction by remember { mutableStateOf<String?>(null) }
    var autoSample by remember {
        mutableStateOf(preferences.getBoolean(PREF_TRAFFIC_AUTO_SAMPLE, true))
    }
    var sampleInterval by remember {
        mutableStateOf(TrafficSampleInterval.fromName(preferences.getString(PREF_TRAFFIC_SAMPLE_INTERVAL, null)))
    }
    var alertThreshold by remember {
        mutableStateOf(TrafficAlertThreshold.fromName(preferences.getString(PREF_TRAFFIC_ALERT_THRESHOLD, null)))
    }
    var copiedSamples by remember { mutableStateOf(false) }
    var copiedControlAction by remember { mutableStateOf(false) }
    var copiedHealth by remember { mutableStateOf(false) }
    var copiedHealthIssues by remember { mutableStateOf(false) }
    var copiedSampleFailure by remember { mutableStateOf(false) }
    var copiedTrafficInsight by remember { mutableStateOf(false) }
    var copiedTrafficActionPlan by remember { mutableStateOf(false) }
    var copiedTrafficDiagnostics by remember { mutableStateOf(false) }
    var trafficDiagnostics by remember { mutableStateOf<CliResult?>(null) }
    var lastSampleFailure by remember { mutableStateOf<CliResult?>(null) }
    var failedSamples by remember { mutableStateOf(0) }
    var sampleLoading by remember { mutableStateOf(false) }
    var diagnosticsLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun appendSamples(result: CliResult) {
        val latestSample = parseStats(result.output)
        if (latestSample == null) {
            failedSamples += 1
            lastSampleFailure = result
            copiedSampleFailure = false
            return
        }
        failedSamples = 0
        copiedSamples = false
        copiedTrafficInsight = false
        copiedTrafficActionPlan = false
        copiedTrafficDiagnostics = false
        trafficDiagnostics = null
        lastSampleFailure = null
        copiedSampleFailure = false
        samples.add(latestSample)
        while (samples.size > 36) {
            samples.removeAt(0)
        }
        onTrafficRateChange(latestSample.total)
    }

    suspend fun collectSample() {
        try {
            val result = normalizeStatsResult(runMagicNet("api stats"))
            stats = result
            appendSamples(result)
        } finally {
            sampleLoading = false
        }
    }

    fun startSample() {
        if (sampleLoading) return
        sampleLoading = true
        scope.launch { collectSample() }
    }

    fun refresh(includeStatus: Boolean = true) {
        if (!includeStatus) {
            startSample()
            return
        }
        val shouldSample = !sampleLoading
        if (shouldSample) sampleLoading = true
        if (includeStatus) loading = true
        scope.launch {
            val sampleJob = if (shouldSample) async { collectSample() } else null
            val healthJob = if (includeStatus) async { runMagicNet("health") } else null
            val serviceJob = if (includeStatus) async { runMagicNet("service status") } else null
            val transparentJob = if (includeStatus) async { runMagicNet("transparent status") } else null
            sampleJob?.await()
            healthJob?.await()?.let { health = it }
            if (includeStatus) copiedHealth = false
            if (includeStatus) copiedHealthIssues = false
            serviceJob?.await()?.let { service = it }
            transparentJob?.await()?.let { transparent = it }
            if (includeStatus) loading = false
        }
    }

    fun refreshStatusOnly() {
        loading = true
        scope.launch {
            val healthJob = async { runMagicNet("health") }
            val serviceJob = async { runMagicNet("service status") }
            val transparentJob = async { runMagicNet("transparent status") }
            health = healthJob.await()
            copiedHealth = false
            copiedHealthIssues = false
            service = serviceJob.await()
            transparent = transparentJob.await()
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(autoSample, sampleInterval) {
        while (autoSample) {
            delay(sampleInterval.millis)
            if (!sampleLoading) {
                sampleLoading = true
                collectSample()
            }
        }
    }

    fun runAction(args: String) {
        pendingDangerAction = null
        loading = true
        scope.launch {
            lastAction = runMagicNet(args)
            copiedControlAction = false
            service = runMagicNet("service status")
            transparent = runMagicNet("transparent status")
            health = runMagicNet("health")
            copiedHealth = false
            copiedHealthIssues = false
            loading = false
        }
    }

    fun copyControlAction() {
        val result = lastAction ?: return
        if (result.output.isBlank()) return
        copyPlainText(context, "MagicBox control command", formatToolResult(result))
        copiedControlAction = true
    }

    fun shareControlAction() {
        val result = lastAction ?: return
        if (result.output.isBlank()) return
        sharePlainText(context, "MagicBox control command", formatToolResult(result))
    }

    fun copyHealthResult() {
        val result = health ?: return
        if (result.output.isBlank()) return
        copyPlainText(context, "MagicBox health", formatToolResult(result))
        copiedHealth = true
    }

    fun shareHealthResult() {
        val result = health ?: return
        if (result.output.isBlank()) return
        sharePlainText(context, "MagicBox health", formatToolResult(result))
    }

    fun copySampleFailure() {
        val result = lastSampleFailure ?: return
        if (result.output.isBlank()) return
        copyPlainText(context, "MagicBox traffic sample failure", formatToolResult(result))
        copiedSampleFailure = true
    }

    fun shareSampleFailure() {
        val result = lastSampleFailure ?: return
        if (result.output.isBlank()) return
        sharePlainText(context, "MagicBox traffic sample failure", formatToolResult(result))
    }

    fun runTrafficDiagnostics(plan: TrafficActionPlan) {
        if (diagnosticsLoading) return
        diagnosticsLoading = true
        copiedTrafficDiagnostics = false
        scope.launch {
            trafficDiagnostics = collectTrafficDiagnostics(plan)
            diagnosticsLoading = false
        }
    }

    fun copyTrafficDiagnostics() {
        val result = trafficDiagnostics ?: return
        if (result.output.isBlank()) return
        copyPlainText(context, "MagicBox traffic diagnostics", formatToolResult(result))
        copiedTrafficDiagnostics = true
    }

    fun shareTrafficDiagnostics() {
        val result = trafficDiagnostics ?: return
        if (result.output.isBlank()) return
        sharePlainText(context, "MagicBox traffic diagnostics", formatToolResult(result))
    }

    val healthEntries = parseHealthEntries(health?.output.orEmpty())
    val healthCounts = buildHealthSeverityCounts(healthEntries)
    val latest = samples.lastOrNull()
    val averageRate = samples.takeLast(12).takeIf { it.isNotEmpty() }?.map { it.total }?.average()?.toFloat() ?: 0f
    val peakRate = samples.maxOfOrNull { it.total } ?: 0f

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(14.dp))
        PageMasthead("MagicNet")
        HeroStatsPanel(
            title = t.networkSpeed,
            summary = latest?.let { t.latestSample(formatRate(it.up), formatRate(it.down)) } ?: stats?.summary ?: t.waitingLiveSamples,
            latest = latest,
            samples = samples,
        )
        TrafficOverviewCard(samples)
        TrafficInsightCard(
            samples = samples,
            sampleInterval = sampleInterval,
            alertThreshold = alertThreshold,
            copied = copiedTrafficInsight,
            onThresholdChange = {
                alertThreshold = it
                copiedTrafficInsight = false
                copiedTrafficActionPlan = false
                copiedTrafficDiagnostics = false
                trafficDiagnostics = null
                preferences.edit().putString(PREF_TRAFFIC_ALERT_THRESHOLD, it.name).apply()
            },
            onCopy = {
                copyPlainText(context, "MagicBox traffic insight", it)
                copiedTrafficInsight = true
            },
            onShare = {
                sharePlainText(context, "MagicBox traffic insight", it)
            },
        )
        TrafficActionPlanCard(
            samples = samples,
            sampleInterval = sampleInterval,
            alertThreshold = alertThreshold,
            copied = copiedTrafficActionPlan,
            diagnosticResult = trafficDiagnostics,
            copiedDiagnostics = copiedTrafficDiagnostics,
            sampleLoading = sampleLoading,
            statusLoading = loading,
            diagnosticsLoading = diagnosticsLoading,
            onSampleNow = { refresh(includeStatus = false) },
            onRefreshStatus = ::refreshStatusOnly,
            onRunDiagnostics = ::runTrafficDiagnostics,
            onCopy = {
                copyPlainText(context, "MagicBox traffic action plan", it)
                copiedTrafficActionPlan = true
            },
            onShare = {
                sharePlainText(context, "MagicBox traffic action plan", it)
            },
            onCopyDiagnostics = ::copyTrafficDiagnostics,
            onShareDiagnostics = ::shareTrafficDiagnostics,
        )
        TrafficSamplerCard(
            autoSample = autoSample,
            sampleCount = samples.size,
            samples = samples,
            failedSamples = failedSamples,
            averageRate = averageRate,
            peakRate = peakRate,
            sampleInterval = sampleInterval,
            sampleLoading = sampleLoading,
            statusLoading = loading,
            onToggleAuto = {
                val next = !autoSample
                autoSample = next
                preferences.edit().putBoolean(PREF_TRAFFIC_AUTO_SAMPLE, next).apply()
            },
            onIntervalChange = {
                sampleInterval = it
                copiedTrafficActionPlan = false
                copiedTrafficDiagnostics = false
                trafficDiagnostics = null
                preferences.edit().putString(PREF_TRAFFIC_SAMPLE_INTERVAL, it.name).apply()
            },
            onSampleNow = { refresh(includeStatus = false) },
            onRefreshStatus = ::refreshStatusOnly,
            onCopySamples = {
                if (samples.isNotEmpty()) {
                    copyPlainText(context, "MagicBox traffic samples", formatTrafficSamples(samples))
                    copiedSamples = true
                }
            },
            onShareSamples = {
                if (samples.isNotEmpty()) sharePlainText(context, "MagicBox traffic samples", formatTrafficSamples(samples))
            },
            onClear = {
                samples.clear()
                failedSamples = 0
                copiedSamples = false
                copiedTrafficInsight = false
                copiedTrafficActionPlan = false
                copiedTrafficDiagnostics = false
                trafficDiagnostics = null
                lastSampleFailure = null
                copiedSampleFailure = false
                onTrafficRateChange(0f)
            },
            copiedSamples = copiedSamples,
            lastSampleFailure = lastSampleFailure,
            copiedSampleFailure = copiedSampleFailure,
            onCopyFailure = ::copySampleFailure,
            onShareFailure = ::shareSampleFailure,
        )
        ConnectionsCard()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile(
                title = t.mode,
                value = transparent?.output?.substringAfter("mode=", "")?.lineSequence()?.firstOrNull()?.ifBlank { null }
                    ?: healthEntries.firstOrNull { it.title == "TUN" }?.details?.substringBefore(",")
                    ?: t.notReported,
                modifier = Modifier.weight(1f),
            )
            DashboardTile(
                title = t.core,
                value = healthEntries.firstOrNull { it.title == "Core" }?.details ?: t.notReported,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile(
                title = t.health,
                value = if (healthEntries.isEmpty()) t.checking else t.healthCompact(healthCounts.ok, healthCounts.warn, healthCounts.error),
                modifier = Modifier.weight(1f),
            )
            DashboardTile(
                title = t.sync,
                value = if (loading) t.running else if (stats?.success == true) t.ready else t.needsRoot,
                modifier = Modifier.weight(1f),
                action = if (loading) "..." else t.run,
                enabled = !loading,
                onClick = ::refresh,
            )
        }
        ControlPanel(
            loading = loading,
            service = service,
            transparent = transparent,
            lastAction = lastAction,
            onAction = ::runAction,
            pendingDangerAction = pendingDangerAction,
            onRequestDangerAction = { pendingDangerAction = it },
            onCancelDangerAction = { pendingDangerAction = null },
            copiedControlAction = copiedControlAction,
            onCopyAction = ::copyControlAction,
            onShareAction = ::shareControlAction,
        )
        HealthPanel(
            result = health,
            copied = copiedHealth,
            copiedIssues = copiedHealthIssues,
            onCopy = ::copyHealthResult,
            onShare = ::shareHealthResult,
            onCopyIssues = {
                copyPlainText(context, "MagicBox health issues", it)
                copiedHealthIssues = true
            },
            onShareIssues = {
                sharePlainText(context, "MagicBox health issues", it)
            },
        )
        Spacer(Modifier.height(10.dp))
    }
}
