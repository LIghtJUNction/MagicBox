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
fun RouteSummaryCard(result: CliResult?, selectedBucket: RuleBucket) {
    val t = LocalUiText.current
    val summary = result?.takeIf { it.success }?.let { parseRouteSummary(it.output) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.routingTable)
                Body(
                    when {
                        result == null -> t.waitingRouteList
                        result.success && summary != null -> t.routeSummary(summary.total)
                        else -> result.summary
                    },
                )
            }
            StatusPill(
                when (result?.success) {
                    true -> t.ok
                    false -> t.fail
                    null -> t.idle
                },
            )
        }
        if (result?.success == true && summary != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountBadge(t.ruleBucket(RuleBucket.Proxy).uppercase(), summary.proxy.size, MagicPalette.rose, Modifier.weight(1f))
                CountBadge(t.ruleBucket(RuleBucket.Direct).uppercase(), summary.direct.size, MagicPalette.green, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountBadge(t.ruleBucket(RuleBucket.Block).uppercase(), summary.block.size, MagicPalette.red, Modifier.weight(1f))
                CountBadge(t.ruleBucket(RuleBucket.Warp).uppercase(), summary.warp.size, MagicPalette.orange, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Label(t.domainsForBucket(t.ruleBucket(selectedBucket).uppercase()))
            Spacer(Modifier.height(6.dp))
            val domains = summary.forBucket(selectedBucket)
            if (domains.isEmpty()) {
                Body(t.noDomains(t.ruleBucket(selectedBucket).lowercase()))
            } else {
                domains.take(5).forEach { domain ->
                    DomainRow(domain)
                    Spacer(Modifier.height(5.dp))
                }
                if (domains.size > 5) Body(t.more(domains.size - 5))
            }
        } else if (result?.success == false) {
            Spacer(Modifier.height(8.dp))
            Mono(result.output.take(900))
        }
    }
}

@Composable
fun ModuleBridgeCard(result: CliResult?) {
    val t = LocalUiText.current
    val bridge = result?.takeIf { it.success }?.let { parseModuleBridge(it.output) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.backendBridge)
                Body(
                    when {
                        result == null -> t.checkingModule
                        result.success && bridge != null -> t.magicnetReachable(bridge.version.ifBlank { t.module })
                        else -> result.summary
                    },
                )
            }
            StatusPill(
                when (result?.success) {
                    true -> t.ok
                    false -> t.fail
                    null -> t.idle
                },
            )
        }
        if (result?.success == true && bridge != null) {
            Spacer(Modifier.height(10.dp))
            BridgeRow(t.module, bridge.version)
            BridgeRow("CLI", t.cliState(bridge.cli))
            BridgeRow("MCP", if (bridge.enabled == "1") t.enabled else bridge.enabled.ifBlank { t.unknown })
            BridgeRow("API", listOf(bridge.bind, bridge.port).filter { it.isNotBlank() }.joinToString(":").ifBlank { t.notReported })
            BridgeRow(t.secret, if (bridge.secretSet == "1") t.configured else bridge.secretSet.ifBlank { t.notReported })
        } else if (result?.success == false && result.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(result.output.take(900))
        }
    }
}

@Composable
fun BridgeRow(label: String, value: String) {
    val t = LocalUiText.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(label)
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = value.ifBlank { t.unknown },
            style = TextStyle(color = MagicPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DomainRow(domain: String) {
    val colors = LocalMagicTheme.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.control)
                .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        BasicText(
            text = domain,
            style = TextStyle(color = MagicPalette.text, fontSize = 13.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ManageRow(
    title: String,
    detail: String,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val colors = LocalMagicTheme.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.control)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(color = MagicPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = detail,
                style = TextStyle(color = MagicPalette.muted, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallButton(action, enabled = enabled, onClick = onAction)
        if (secondaryAction != null && onSecondaryAction != null) {
            Spacer(Modifier.width(6.dp))
            SmallButton(secondaryAction, enabled = enabled, onClick = onSecondaryAction)
        }
    }
}

@Composable
fun HealthLine(entry: HealthEntry) {
    val color = entry.severity.color
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = entry.title,
                style = TextStyle(color = MagicPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.details.isNotBlank()) {
                BasicText(
                    text = entry.details,
                    style = TextStyle(color = MagicPalette.muted, fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
