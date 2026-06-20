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
fun MagicBackground(
    style: BackgroundStyle,
    palette: BackgroundPalette,
    trafficRate: Float,
) {
    val traffic = trafficIntensity(trafficRate)
    val motion = 0.72f + traffic * 3.8f
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(style, palette, traffic) {
        while (true) {
            withFrameNanos { nanos ->
                phase = (nanos / 1_000_000_000f) * (0.18f + traffic * 1.55f)
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(palette.base)) {
        val driftX = sin(phase) * size.width * 0.055f
        val driftY = cos(phase * 0.82f) * size.height * 0.035f
        val counterDriftX = cos(phase * 0.73f) * size.width * 0.045f
        val counterDriftY = sin(phase * 1.1f) * size.height * 0.028f
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        when (style) {
                            BackgroundStyle.Monet -> listOf(palette.base, palette.primary.copy(alpha = 0.34f), palette.secondary.copy(alpha = 0.18f))
                            BackgroundStyle.Ember -> listOf(Color(0xFF080814), Color(0xFF23123A), Color(0xFF3C171C))
                            BackgroundStyle.Aurora -> listOf(Color(0xFF071018), Color(0xFF17204A), Color(0xFF341D3F))
                            BackgroundStyle.Minimal -> listOf(Color(0xFF0B0A0D), Color(0xFF111015), Color(0xFF0B0A0D))
                        },
                    start = Offset(driftX, -driftY),
                    end = Offset(size.width + counterDriftX, size.height + driftY),
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(palette.primary.copy(alpha = 0.42f), palette.primary.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.84f + driftX, size.height * 0.12f + driftY),
                    radius = size.minDimension * 0.72f,
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(palette.secondary.copy(alpha = 0.32f), palette.secondary.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.08f + counterDriftX, size.height * 0.22f + counterDriftY),
                    radius = size.minDimension * 0.78f,
                ),
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(palette.primary.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.58f + sin(phase * 0.9f) * 80.dp.toPx(), size.height * 0.46f),
                    radius = size.minDimension * 0.38f,
                ),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.58f, size.height * 0.46f),
        )
        drawRect(color = Color.Black.copy(alpha = if (style == BackgroundStyle.Minimal) 0.62f else 0.42f))

        val asciiPaint =
            Paint().apply {
                isAntiAlias = true
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textSize = 11.sp.toPx()
            }
        val largeAsciiPaint =
            Paint(asciiPaint).apply {
                textSize = 22.sp.toPx()
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
        val cellX = 24.dp.toPx()
        val cellY = 28.dp.toPx()
        val columns = (size.width / cellX).toInt() + 5
        val rows = (size.height / cellY).toInt() + 5
        val slowFall = phase * (52.dp.toPx() + traffic * 150.dp.toPx())
        val sideDrift = sin(phase * (0.72f + traffic * 1.4f)) * (18.dp.toPx() + traffic * 64.dp.toPx())
        drawContext.canvas.nativeCanvas.apply {
            repeat(rows) { row ->
                repeat(columns) { column ->
                    val seed = row * 37.13f + column * 19.71f
                    val flicker = (sin(seed + phase * (5.8f + traffic * 17.2f)) + 1f) * 0.5f
                    val sweep = sin(column * 0.35f - row * 0.14f + phase * (3.4f + traffic * 10.4f))
                    val sparseGate = sin(seed * 0.47f + phase * (1.8f + traffic * 5.8f))
                    if (sparseGate > (-0.18f - traffic * 0.56f) || sweep > (0.88f - traffic * 0.42f)) {
                        val glyphIndex = ((row * 11 + column * 17 + (phase * (18f + traffic * 104f) + sweep * 7f).toInt()) % ASCII_GLYPHS.length).let { if (it < 0) it + ASCII_GLYPHS.length else it }
                        val glyph = ASCII_GLYPHS[glyphIndex].toString()
                        val waveX = sin(phase * (1.4f + traffic * 2.9f) + row * 0.41f) * (11.dp.toPx() + traffic * 26.dp.toPx())
                        val waveY = cos(phase * (1.1f + traffic * 2.5f) + column * 0.33f) * (9.dp.toPx() + traffic * 22.dp.toPx())
                        val x = ((column * cellX - cellX * 2f + waveX + sideDrift + slowFall * (0.1f + traffic * 0.14f)) % (size.width + cellX * 5f)) - cellX * 2f
                        val y = ((row * cellY + slowFall * ((0.48f + traffic * 0.54f) + column % 5 * 0.08f)) % (size.height + cellY * 5f)) - cellY * 2f + waveY
                        val alpha = (0.018f + traffic * 0.028f + flicker * (0.056f + traffic * 0.072f) + sweep.coerceAtLeast(0f) * (0.036f + traffic * 0.082f)) * if (style == BackgroundStyle.Minimal) 0.76f else 1f
                        asciiPaint.color =
                            (if ((row + column) % 5 == 0) palette.secondary else palette.primary)
                                .copy(alpha = alpha.coerceAtMost(0.26f))
                                .toArgb()
                        drawText(glyph, x, y, asciiPaint)
                    }
                }
            }
            repeat(18) { index ->
                val seed = index * 23.91f
                val glyphIndex = ((index * 7 + (phase * (10f + traffic * 42f)).toInt()) % ASCII_GLYPHS.length).let { if (it < 0) it + ASCII_GLYPHS.length else it }
                val x = size.width * ((index % 6) / 5f) + sin(phase * (0.72f + traffic * 1.65f) + seed) * (28.dp.toPx() + traffic * 82.dp.toPx())
                val y = size.height * ((index / 6 + 1) / 4.2f) + cos(phase * (0.58f + traffic * 1.35f) + seed) * (36.dp.toPx() + traffic * 90.dp.toPx())
                largeAsciiPaint.color =
                    palette.secondary
                        .copy(alpha = 0.022f + traffic * 0.048f + (sin(phase * (1.6f + traffic * 5.2f) + seed) + 1f) * (0.018f + traffic * 0.034f))
                        .toArgb()
                drawText(ASCII_GLYPHS[glyphIndex].toString(), x, y, largeAsciiPaint)
            }
        }
        repeat(3) { index ->
            val scanY = ((phase * ((92.dp.toPx() + traffic * 330.dp.toPx()) + index * 42.dp.toPx()) + index * size.height * 0.31f) % (size.height + 120.dp.toPx())) - 60.dp.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                palette.primary.copy(alpha = 0.028f + traffic * 0.105f),
                                Color.Transparent,
                            ),
                        startY = scanY - 44.dp.toPx(),
                        endY = scanY + 44.dp.toPx(),
                    ),
                topLeft = Offset(0f, scanY - 44.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 88.dp.toPx()),
            )
        }
        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                    center = Offset(size.width * 0.5f, size.height * 0.38f),
                    radius = size.maxDimension * 0.82f,
                ),
        )
    }
}

fun trafficIntensity(rate: Float): Float {
    val clean = rate.coerceAtLeast(0f)
    return (clean / (clean + 524_288f)).coerceIn(0f, 1f)
}

