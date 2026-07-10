package com.github.lightjunction.magicbox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MagicBackground(
    style: BackgroundStyle,
    palette: BackgroundPalette,
    trafficRate: Float,
) {
    val traffic = remember(trafficRate) { trafficIntensity(trafficRate) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseColors =
            when (style) {
                BackgroundStyle.Monet ->
                    listOf(
                        Color(0xFF050507),
                        palette.primary.copy(alpha = 0.22f),
                        palette.secondary.copy(alpha = 0.12f),
                    )
                BackgroundStyle.Ember ->
                    listOf(
                        Color(0xFF050506),
                        Color(0xFF241015),
                        Color(0xFF100A18),
                    )
                BackgroundStyle.Aurora ->
                    listOf(
                        Color(0xFF040709),
                        Color(0xFF071A18),
                        Color(0xFF151025),
                    )
                BackgroundStyle.Minimal ->
                    listOf(
                        Color(0xFF050505),
                        Color(0xFF0B090B),
                        Color(0xFF050505),
                    )
            }
        val detailScale = if (style == BackgroundStyle.Minimal) 0.45f else 1f

        drawRect(
            brush =
                Brush.linearGradient(
                    colors = baseColors,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            palette.primary.copy(alpha = (0.14f + traffic * 0.12f) * detailScale),
                            palette.primary.copy(alpha = 0.035f * detailScale),
                            Color.Transparent,
                        ),
                    center = Offset(size.width * 0.86f, size.height * 0.12f),
                    radius = size.maxDimension * (0.5f + traffic * 0.06f),
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            palette.secondary.copy(alpha = (0.11f + traffic * 0.1f) * detailScale),
                            palette.secondary.copy(alpha = 0.025f * detailScale),
                            Color.Transparent,
                        ),
                    center = Offset(size.width * 0.08f, size.height * 0.72f),
                    radius = size.maxDimension * 0.58f,
                ),
        )

        val gridColor =
            palette.secondary.copy(
                alpha = (0.018f + traffic * 0.022f) * detailScale,
            )
        val gridStroke = (0.45f + traffic * 0.25f).dp.toPx()
        repeat(5) { index ->
            val x = size.width * (index + 1) / 6f
            drawLine(
                color = gridColor,
                start = Offset(x, size.height * 0.08f),
                end = Offset(x, size.height * 0.92f),
                strokeWidth = gridStroke,
            )
        }
        repeat(7) { index ->
            val y = size.height * (index + 1) / 8f
            drawLine(
                color = gridColor,
                start = Offset(size.width * 0.06f, y),
                end = Offset(size.width * 0.94f, y),
                strokeWidth = gridStroke,
            )
        }

        val nodeColor =
            palette.primary.copy(
                alpha = (0.09f + traffic * 0.17f) * detailScale,
            )
        val nodeA = Offset(size.width * 0.18f, size.height * 0.29f)
        val nodeB = Offset(size.width * 0.48f, size.height * 0.43f)
        val nodeC = Offset(size.width * 0.78f, size.height * 0.31f)
        val nodeD = Offset(size.width * 0.66f, size.height * 0.7f)
        val linkWidth = (0.6f + traffic * 0.7f).dp.toPx()
        drawLine(nodeColor, nodeA, nodeB, linkWidth)
        drawLine(nodeColor, nodeB, nodeC, linkWidth)
        drawLine(nodeColor, nodeB, nodeD, linkWidth)
        listOf(nodeA, nodeB, nodeC, nodeD).forEach { node ->
            drawCircle(
                color = nodeColor,
                radius = (1.6f + traffic * 1.4f).dp.toPx(),
                center = node,
            )
        }

        drawRect(
            brush =
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f)),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.maxDimension * 0.78f,
                ),
        )
    }
}

fun trafficIntensity(rate: Float): Float {
    val clean = rate.coerceAtLeast(0f)
    return (clean / (clean + 524_288f)).coerceIn(0f, 1f)
}
