package com.github.lightjunction.magicbox.reboot

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val LocalReducedMotion = staticCompositionLocalOf { false }

/** A surface becomes tokens, then returns to a solid form. No idle frame clock. */
@Composable
internal fun TokenSurface(
    label: String?, modifier: Modifier, reduced: Boolean, enabled: Boolean,
    plain: Boolean = false, onClick: () -> Unit,
) {
    val colors = LocalCloud.current
    val phase = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactions = remember { MutableInteractionSource() }
    var job by remember { mutableStateOf<Job?>(null) }
    var drag by remember { mutableFloatStateOf(0f) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    val hero = label == null
    fun animate(from: Float = 0f) {
        job?.cancel()
        if (!reduced) job = scope.launch { phase.snapTo(from); phase.animateTo(1f, tween(680)) }
    }
    LaunchedEffect(reduced) { if (reduced) { job?.cancel(); drag = 0f; phase.snapTo(1f) } }
    Box(
        modifier.semantics { if (hero) contentDescription = "Token 字符云" }
            .pointerInput(reduced, enabled) {
                if (enabled && !reduced) detectHorizontalDragGestures(
                    onDragStart = { job?.cancel(); drag = 0.2f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag = (drag + abs(amount) / size.width.coerceAtLeast(1)).coerceIn(0.2f, 1f)
                    },
                    onDragEnd = { drag = 0f; animate(0.5f) },
                    onDragCancel = { drag = 0f; animate(0.5f) },
                )
            }
            .clickable(
                enabled = enabled, role = Role.Button,
                interactionSource = interactions, indication = null,
                onClickLabel = label ?: "打散字符云",
            ) { animate(); onClick() },
        contentAlignment = if (plain) Alignment.CenterStart else Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val spread = if (reduced) 0f else maxOf(drag, TokenGeometry.spread(phase.value))
            if (!hero && !plain) drawRoundRect(
                color = (if (enabled) colors.ink else colors.muted).copy(alpha = (1f - spread) * (1f - spread)),
                cornerRadius = CornerRadius(22.dp.toPx()),
            )
            if (hero || spread > 0.01f) {
                paint.color = colors.accent.toArgb()
                paint.textSize = (if (hero) 11.dp else 10.dp).toPx()
                repeat(if (plain) 48 else TokenGeometry.COUNT) { index ->
                    val u = TokenGeometry.x(index)
                    val v = TokenGeometry.y(index)
                    val homeX = (0.08f + u * 0.84f) * size.width
                    val homeY = (0.5f + (v - 0.5f) * (if (hero) TokenGeometry.taper(index) * 0.85f else 0.42f)) * size.height
                    paint.alpha = ((if (hero) 0.22f + v * 0.66f else spread) * 255).toInt().coerceIn(0, 255)
                    drawContext.canvas.nativeCanvas.drawText(
                        TokenGeometry.glyphs[index % TokenGeometry.glyphs.size],
                        homeX + TokenGeometry.dx(index) * size.width * spread,
                        homeY + TokenGeometry.dy(index) * size.height * spread,
                        paint,
                    )
                }
            }
        }
        if (label != null) BasicText(
            label,
            Modifier.graphicsLayer { alpha = 1f - maxOf(drag, TokenGeometry.spread(phase.value)) },
            style = TextStyle(
                color = if (plain) if (enabled) colors.accent else colors.muted else colors.paper,
                fontSize = if (plain) 14.sp else 16.sp,
                lineHeight = 24.sp,
                fontWeight = if (plain) FontWeight.Normal else FontWeight.SemiBold,
            ),
        )
    }
}
