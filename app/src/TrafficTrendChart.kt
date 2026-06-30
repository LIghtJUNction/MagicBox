package com.github.lightjunction.magicbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrafficTrendChart(samples: List<LiveStats>) {
    val colors = LocalMagicTheme.current
    val t = LocalUiText.current
    if (samples.size < 2) {
        BasicText(
            text = t.waitingTrafficTrend(),
            style = TextStyle(color = MagicPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
        return
    }
    val maxRate = samples.maxOf { maxOf(it.up, it.down) }
    val chartScale = maxOf(maxRate, 1f)
    val peakUp = samples.maxOf { it.up }
    val peakDown = samples.maxOf { it.down }
    val peakDirection = if (peakUp >= peakDown) t.up else t.down
    val peakValue = maxOf(peakUp, peakDown)
    val windowMillis = (samples.last().timestampMillis - samples.first().timestampMillis).coerceAtLeast(0L)
    Spacer(Modifier.height(8.dp))
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(16.dp))
                .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        val stepX = if (samples.size == 1) size.width else size.width / (samples.size - 1)
        fun yFor(value: Float): Float = size.height - (value / chartScale).coerceIn(0f, 1f) * size.height
        fun pathFor(selector: (LiveStats) -> Float): Path =
            Path().apply {
                samples.forEachIndexed { index, sample ->
                    val point = Offset(index * stepX, yFor(selector(sample)))
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            }
        drawLine(
            color = colors.line.copy(alpha = 0.45f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        drawPath(pathFor { it.down }, color = MagicPalette.cyan, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(pathFor { it.up }, color = MagicPalette.rose, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TrafficLegendText(t.up, MagicPalette.rose, Modifier.weight(1f))
        TrafficLegendText(t.down, MagicPalette.cyan, Modifier.weight(1f))
        TrafficLegendText(t.chartScale(formatRate(maxRate)), MagicPalette.muted, Modifier.weight(1f))
    }
    Spacer(Modifier.height(4.dp))
    BasicText(
        text = t.trafficWindowDetail(samples.size, formatTrafficWindow(windowMillis), peakDirection, formatRate(peakValue)),
        style = TextStyle(color = MagicPalette.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}

@Composable
private fun TrafficLegendText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}

fun UiText.waitingTrafficTrend(): String = if (this === UiText.zh) "等待至少 2 个真实流量样本。" else "Waiting for at least 2 real traffic samples."

fun UiText.chartScale(value: String): String = if (this === UiText.zh) "量程 $value" else "Scale $value"

fun UiText.trafficWindowDetail(
    samples: Int,
    window: String,
    peakDirection: String,
    peakValue: String,
): String =
    if (this === UiText.zh) {
        "$samples 个样本/$window - 峰值 $peakDirection $peakValue"
    } else {
        "$samples samples/$window - peak $peakDirection $peakValue"
    }
