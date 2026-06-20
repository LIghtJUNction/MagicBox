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
fun PageMasthead(title: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
    ) {
        BasicText(
            text = title,
            style =
                TextStyle(
                    color = MagicPalette.text,
                    fontSize = 58.sp,
                    lineHeight = 62.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier =
                Modifier
                    .padding(top = 8.dp, start = 4.dp)
                    .width(88.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(MagicPalette.rose, MagicPalette.green.copy(alpha = 0.35f), Color.Transparent))),
        )
    }
}

@Composable
fun RateBadge(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMagicTheme.current
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(colors.control)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(7.dp))
            Label(label)
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = value,
            style = TextStyle(color = MagicPalette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HeroStatsPanel(
    title: String,
    summary: String,
    latest: LiveStats?,
    samples: List<LiveStats>,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(horizontal = 8.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Label(title)
                Spacer(Modifier.height(6.dp))
                BasicText(
                    text = formatRate(latest?.total ?: 0f),
                    style = TextStyle(color = MagicPalette.text, fontSize = 44.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = summary,
                    style = TextStyle(color = MagicPalette.muted, fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(t.live)
                Spacer(Modifier.height(9.dp))
                BasicText(
                    text = "↑ ${formatRate(latest?.up ?: 0f)}",
                    style = TextStyle(color = MagicPalette.green, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    text = "↓ ${formatRate(latest?.down ?: 0f)}",
                    style = TextStyle(color = MagicPalette.rose, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        StatsLineChart(samples = samples, modifier = Modifier.fillMaxWidth().height(132.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MicroMetric(t.up, formatRate(latest?.up ?: 0f), MagicPalette.green, Modifier.weight(1f))
            MicroMetric(t.down, formatRate(latest?.down ?: 0f), MagicPalette.rose, Modifier.weight(1f))
        }
    }
}

@Composable
fun MicroMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(9.dp))
        Column {
            Label(label)
            BasicText(
                text = value,
                style = TextStyle(color = MagicPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DashboardTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        padding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Label(title)
                Spacer(Modifier.height(7.dp))
                BasicText(
                    text = value,
                    style = TextStyle(color = MagicPalette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (action != null && onClick != null) {
                Spacer(Modifier.width(6.dp))
                StatusPill(action, enabled = enabled, onClick = onClick)
            }
        }
    }
}

@Composable
fun CountBadge(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMagicTheme.current
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(colors.control)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(7.dp))
            Label(label)
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = value.toString(),
            style = TextStyle(color = MagicPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatsLineChart(samples: List<LiveStats>, modifier: Modifier = Modifier) {
    val colors = LocalMagicTheme.current
    Canvas(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(colors.control).padding(12.dp)) {
        val values =
            when {
                samples.isEmpty() -> listOf(0f, 0f)
                samples.size == 1 -> listOf(samples.first().total, samples.first().total)
                else -> samples.map { it.total }
            }
        val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
        val left = 10.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 14.dp.toPx()
        repeat(3) { i ->
            val y = top + (bottom - top) * i / 2f
            drawLine(colors.line, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        val points =
            values.mapIndexed { index, value ->
                val x = left + (right - left) * index / (values.lastIndex.coerceAtLeast(1).toFloat())
                val y = bottom - (bottom - top) * (value / max)
                Offset(x, y)
            }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area =
            Path().apply {
                moveTo(points.first().x, bottom)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, bottom)
                close()
            }
        drawPath(
            area,
            brush = Brush.verticalGradient(listOf(MagicPalette.cyan.copy(alpha = 0.28f), Color.Transparent)),
        )
        drawPath(path, color = MagicPalette.cyan, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach {
            drawCircle(colors.control, radius = 4.5.dp.toPx(), center = it)
            drawCircle(MagicPalette.cyan, radius = 3.dp.toPx(), center = it)
        }
    }
}
