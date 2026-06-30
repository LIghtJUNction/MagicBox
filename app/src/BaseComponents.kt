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
fun CommandBlock(title: String, result: CliResult?, showOutput: Boolean = false) {
    val t = LocalUiText.current
    val summary =
        when {
            result == null -> t.notRunYet
            result.output.isBlank() && result.success -> t.commandCompletedEmpty
            result.output.isBlank() -> t.commandFailedEmpty
            else -> result.summary
        }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(title)
                Body(summary)
            }
            StatusPill(
                when (result?.success) {
                    true -> t.ok
                    false -> t.fail
                    null -> t.idle
                },
            )
        }
        val output = result?.output.orEmpty().trim()
        if (output.isNotBlank() && output != result?.summary && (showOutput || result?.success == false)) {
            Spacer(Modifier.height(8.dp))
            Mono(output.take(900))
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    summary: String,
    action: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        padding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(text = title, style = TextStyle(color = MagicPalette.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                Body(summary)
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(action)
        }
    }
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = LocalMagicTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(colors.control).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) colors.controlSelected else Color.Transparent)
                        .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = label(option),
                    style = TextStyle(color = if (active) MagicPalette.text else MagicPalette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun SmallButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalMagicTheme.current
    Box(
        modifier =
            modifier
                .height(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (enabled) colors.buttonSurface else colors.control)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style =
                TextStyle(
                    color = if (enabled) MagicPalette.text else MagicPalette.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TextInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMagicTheme.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = MagicPalette.text, fontSize = 15.sp),
        modifier = modifier.height(42.dp).clip(RoundedCornerShape(18.dp)).background(colors.control).padding(horizontal = 14.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) BasicText(placeholder, style = TextStyle(color = MagicPalette.muted, fontSize = 15.sp))
                inner()
            }
        },
    )
}

@Composable
fun MultiLineTextInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMagicTheme.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = MagicPalette.text, fontSize = 14.sp, lineHeight = 18.sp),
        modifier = modifier.height(92.dp).clip(RoundedCornerShape(18.dp)).background(colors.control).padding(horizontal = 14.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) {
                    BasicText(placeholder, style = TextStyle(color = MagicPalette.muted, fontSize = 14.sp, lineHeight = 18.sp))
                }
                inner()
            }
        },
    )
}

@Composable
fun Value(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Label(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Body(text: String) {
    BasicText(text = text, style = TextStyle(color = MagicPalette.muted, fontSize = 13.sp, lineHeight = 18.sp))
}

@Composable
fun Mono(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 11.sp, lineHeight = 15.sp),
    )
}

@Composable
fun StatusPill(text: String, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val colors = LocalMagicTheme.current
    val clickableModifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    Box(
        modifier =
            clickableModifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (enabled) colors.controlSelected else colors.control)
                .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        BasicText(text = text, style = TextStyle(color = if (enabled) MagicPalette.text else MagicPalette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMagicTheme.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .padding(padding),
        content = content,
    )
}

@Composable
fun BottomNavigation(
    selectedPage: MagicPage,
    onSelect: (MagicPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalUiText.current
    val colors = LocalMagicTheme.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(colors.navSurface)
                .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MagicPage.entries.forEach { page ->
            val selected = page == selectedPage
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) colors.controlSelected else Color.Transparent)
                        .clickable { onSelect(page) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = t.pageLabel(page),
                    style = TextStyle(color = if (selected) MagicPalette.text else MagicPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
