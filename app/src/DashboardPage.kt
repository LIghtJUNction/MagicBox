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
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<LiveStats>() }
    var health by remember { mutableStateOf<CliResult?>(null) }
    var stats by remember { mutableStateOf<CliResult?>(null) }
    var service by remember { mutableStateOf<CliResult?>(null) }
    var transparent by remember { mutableStateOf<CliResult?>(null) }
    var lastAction by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        scope.launch {
            val statsJob = async { runMagicNet("api stats") }
            val healthJob = async { runMagicNet("health") }
            val serviceJob = async { runMagicNet("service status") }
            val transparentJob = async { runMagicNet("transparent status") }
            stats = normalizeStatsResult(statsJob.await())
            health = healthJob.await()
            service = serviceJob.await()
            transparent = transparentJob.await()
            val parsedSamples = parseStatsSamples(stats?.output.orEmpty())
            parsedSamples.forEach { sample ->
                samples.add(sample)
                while (samples.size > 18) {
                    samples.removeAt(0)
                }
            }
            parsedSamples.lastOrNull()?.let { sample ->
                onTrafficRateChange(sample.total)
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun runAction(args: String) {
        loading = true
        scope.launch {
            lastAction = runMagicNet(args)
            service = runMagicNet("service status")
            transparent = runMagicNet("transparent status")
            health = runMagicNet("health")
            loading = false
        }
    }

    val healthEntries = health?.output?.lineSequence()?.mapNotNull { parseHealthEntry(it) }?.toList().orEmpty()
    val okCount = healthEntries.count { it.severity == HealthSeverity.Ok }
    val warnCount = healthEntries.count { it.severity == HealthSeverity.Warn }
    val errorCount = healthEntries.count { it.severity == HealthSeverity.Error }
    val latest = samples.lastOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(14.dp))
        PageMasthead("MagicNet")
        HeroStatsPanel(
            title = t.networkSpeed,
            summary = latest?.let { t.latestSample(formatRate(it.up), formatRate(it.down)) } ?: stats?.summary ?: t.waitingLiveSamples,
            latest = latest,
            samples = samples,
        )
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
                value = if (healthEntries.isEmpty()) t.checking else t.healthCompact(okCount, warnCount, errorCount),
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
            lastAction = lastAction,
            onAction = ::runAction,
        )
        HealthPanel(health)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun HealthPanel(result: CliResult?) {
    val t = LocalUiText.current
    val entries = result?.output?.lineSequence()?.mapNotNull { parseHealthEntry(it) }?.toList().orEmpty()
    val errorCount = entries.count { it.severity == HealthSeverity.Error }
    val warnCount = entries.count { it.severity == HealthSeverity.Warn }
    val okCount = entries.count { it.severity == HealthSeverity.Ok }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.moduleHealth)
                Body(
                    when {
                        result == null -> t.waitingCliHealth
                        result.success && entries.isNotEmpty() -> t.healthLong(okCount, warnCount, errorCount)
                        else -> result.summary
                    },
                )
            }
            StatusPill(
                when {
                    result == null -> t.check
                    !result.success || errorCount > 0 -> t.fail
                    warnCount > 0 -> t.warn
                    else -> t.ok
                },
            )
        }
        if (result?.success == true && entries.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            entries.take(3).forEach { entry ->
                HealthLine(entry)
                Spacer(Modifier.height(6.dp))
            }
        } else if (result?.success == false && result.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(result.output.take(900))
        }
    }
}

@Composable
fun ControlPanel(
    loading: Boolean,
    service: CliResult?,
    lastAction: CliResult?,
    onAction: (String) -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.control)
                Body(service?.summary ?: t.checkingService)
            }
            StatusPill(
                when (lastAction?.success) {
                    true -> t.done
                    false -> t.fail
                    null -> if (loading) t.busy else t.ready
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(t.start, enabled = !loading, modifier = Modifier.weight(1f)) { onAction("service start") }
            SmallButton(t.restart, enabled = !loading, modifier = Modifier.weight(1f)) { onAction("service restart sing-box") }
            SmallButton(t.stop, enabled = !loading, modifier = Modifier.weight(1f)) { onAction("service stop") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("TUN", enabled = !loading, modifier = Modifier.weight(1f)) { onAction("transparent set tun") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton(t.applyConfig, enabled = !loading, modifier = Modifier.weight(1f)) { onAction("config apply") }
            SmallButton(t.closeConns, enabled = !loading, modifier = Modifier.weight(1f)) { onAction("api close-all") }
        }
        if (lastAction != null) {
            Spacer(Modifier.height(8.dp))
            Body(lastAction.summary)
        }
    }
}
