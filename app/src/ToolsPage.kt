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
fun ToolsPage(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    backgroundStyle: BackgroundStyle,
    onBackgroundChange: (BackgroundStyle) -> Unit,
) {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var module by remember { mutableStateOf<CliResult?>(null) }
    var subList by remember { mutableStateOf<CliResult?>(null) }
    var health by remember { mutableStateOf<CliResult?>(null) }
    var routes by remember { mutableStateOf<CliResult?>(null) }
    var toolResult by remember { mutableStateOf<CliResult?>(null) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var updateState by remember {
        mutableStateOf(UpdateState(t.version(BuildConfig.VERSION_NAME), t.checkGithubReleases, false))
    }

    LaunchedEffect(Unit) {
        module =
            runRootCommand(
                "printf 'MagicNet module: '; " +
                    "sed -n 's/^version=//p' /data/adb/modules/MagicNet/module.prop | head -n 1; " +
                    "test -x $MAGICNET_CLI && echo 'CLI: executable' || echo 'CLI: missing'; " +
                    "$MAGICNET_CLI mcp status 2>&1 | head -n 4",
            )
        subList = runMagicNet("sub list")
        health = runMagicNet("health")
        routes = runMagicNet("route list")
    }

    fun runTool(args: String) {
        loading = true
        scope.launch {
            toolResult = runMagicNet(args)
            if (args.startsWith("sub ")) subList = runMagicNet("sub list")
            health = runMagicNet("health")
            routes = runMagicNet("route list")
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(6.dp))
        PageMasthead(t.tools)
        Card(padding = PaddingValues(10.dp)) {
            Label(t.language)
            Spacer(Modifier.height(8.dp))
            SegmentedControl(AppLanguage.entries, language, { t.languageName(it) }) { onLanguageChange(it) }
        }
        Card(padding = PaddingValues(10.dp)) {
            Label(t.background)
            Spacer(Modifier.height(8.dp))
            SegmentedControl(BackgroundStyle.entries, backgroundStyle, { t.backgroundName(it) }) { onBackgroundChange(it) }
            Spacer(Modifier.height(8.dp))
            Body(t.backgroundHint)
        }
        ModuleBridgeCard(module)
        Card(padding = PaddingValues(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Label(t.subscription)
                    Body(subList?.summary ?: t.loadingSubscription)
                }
                StatusPill(if (subList?.success == true) t.ok else t.idle)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.updateSub, enabled = !loading, modifier = Modifier.weight(1f)) { runTool("sub update sing-box") }
                SmallButton(t.updateAll, enabled = !loading, modifier = Modifier.weight(1f)) { runTool("sub update-all") }
            }
        }
        Card(padding = PaddingValues(10.dp)) {
            Label(t.diagnostics)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.health, enabled = !loading, modifier = Modifier.weight(1f)) { runTool("health") }
                SmallButton(t.diagnose, enabled = !loading, modifier = Modifier.weight(1f)) { runTool("diagnose") }
                SmallButton(t.support, enabled = !loading, modifier = Modifier.weight(1f)) { runTool("support bundle") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.ecapture, enabled = !loading, modifier = Modifier.weight(1f)) { runTool("ecapture status") }
                SmallButton(t.mcpLogs, enabled = !loading, modifier = Modifier.weight(1f)) { runTool("mcp logs 40") }
            }
        }
        Card(padding = PaddingValues(10.dp)) {
            Label(t.diffIssue)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f)) {
                    loading = true
                    scope.launch {
                        val healthJob = async { runMagicNet("health") }
                        val routeJob = async { runMagicNet("route list") }
                        health = healthJob.await()
                        routes = routeJob.await()
                        loading = false
                    }
                }
                SmallButton(t.draft, enabled = !loading, modifier = Modifier.weight(1f)) {
                    draft = buildIssueDraft(health, routes)
                }
            }
            if (draft.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Mono(draft.take(900))
            }
        }
        CommandBlock(t.toolOutput, toolResult)
        ActionCard(
            title = updateState.title,
            summary = updateState.summary,
            action = if (updateState.checking) t.checking else t.check,
            enabled = !updateState.checking,
            onClick = {
                updateState = UpdateState(t.checkingUpdates, t.contactingGithub, true)
                scope.launch { updateState = checkForUpdates(t) }
            },
        )
        ActionCard(
            title = t.githubReleases,
            summary = t.openApkPage,
            action = t.open,
            onClick = { openUri(context, MAGICBOX_RELEASES_URL) },
        )
        Card {
            Label(t.attribution)
            Body(t.attributionText)
        }
        Spacer(Modifier.height(10.dp))
    }
}
