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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MagicBoxApp() }
    }
}

@Composable
fun MagicBoxApp() {
    val context = LocalContext.current
    var selectedPage by remember { mutableStateOf(MagicPage.Stats) }
    var language by remember {
        mutableStateOf(
            context
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(PREF_LANGUAGE, null)
                ?.let(AppLanguage::fromCode)
                ?: AppLanguage.systemDefault(),
        )
    }
    var backgroundStyle by remember {
        mutableStateOf(
            context
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(PREF_BACKGROUND, null)
                ?.let(BackgroundStyle::fromCode)
                ?: BackgroundStyle.Monet,
        )
    }
    val text = UiText.forLanguage(language)
    val backgroundPalette = remember(backgroundStyle) { readBackgroundPalette(context, backgroundStyle) }
    val themeColors = remember(backgroundPalette) { MagicThemeColors.from(backgroundPalette) }
    var backgroundTrafficRate by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            val result = normalizeStatsResult(runMagicNet("api stats"))
            parseStatsSamples(result.output).lastOrNull()?.let { sample ->
                backgroundTrafficRate = sample.total
            }
            delay(2500)
        }
    }

    CompositionLocalProvider(LocalUiText provides text, LocalMagicTheme provides themeColors) {
        Box(modifier = Modifier.fillMaxSize()) {
            MagicBackground(backgroundStyle, backgroundPalette, backgroundTrafficRate)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                ) {
                    when (selectedPage) {
                        MagicPage.Stats -> StatsPage(onTrafficRateChange = { backgroundTrafficRate = it })
                        MagicPage.Rules -> RulesPage()
                        MagicPage.Apps -> AppsPage()
                        MagicPage.Tools -> ToolsPage(
                            language = language,
                            onLanguageChange = { next ->
                                language = next
                                context
                                    .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString(PREF_LANGUAGE, next.code)
                                    .apply()
                            },
                            backgroundStyle = backgroundStyle,
                            onBackgroundChange = { next ->
                                backgroundStyle = next
                                context
                                    .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString(PREF_BACKGROUND, next.code)
                                    .apply()
                            },
                        )
                    }
                }
                BottomNavigation(
                    selectedPage = selectedPage,
                    onSelect = { selectedPage = it },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
