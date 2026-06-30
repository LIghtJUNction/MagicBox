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

suspend fun runMagicNet(args: String): CliResult = runRootCommand("$MAGICNET_CLI $args")

suspend fun runMagicNetLong(args: String): CliResult = runRootCommand("$MAGICNET_CLI $args", timeoutSeconds = 45)

fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

suspend fun runRootCommand(command: String, timeoutSeconds: Long = 6): CliResult =
    withContext(Dispatchers.IO) {
        var lastResult: CliResult? = null
        for (su in SU_CANDIDATES) {
            val withNamespace = executeSu(listOf(su, "-M", "-c", command), command, timeoutSeconds)
            val result =
                when {
                    withNamespace.success -> withNamespace
                    withNamespace.output.contains("invalid", ignoreCase = true) ||
                        withNamespace.output.contains("unknown option", ignoreCase = true) -> {
                        executeSu(listOf(su, "-c", command), command, timeoutSeconds)
                    }
                    else -> withNamespace
                }
            if (result.success || !result.output.contains("Cannot run program", ignoreCase = true)) {
                return@withContext result
            }
            lastResult = result
        }
        lastResult?.let {
            if (it.output.contains("Cannot run program", ignoreCase = true)) {
                CliResult(false, command, rootUnavailableMessage())
            } else {
                it
            }
        } ?: CliResult(false, command, rootUnavailableMessage())
    }

fun rootUnavailableMessage(): String =
    "Root is not granted to MagicBox. Grant com.github.lightjunction.magicbox in KernelSU/Magisk, then tap Run."

fun executeSu(args: List<String>, command: String, timeoutSeconds: Long = 6): CliResult {
    val process =
        runCatching {
            ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
        }.getOrElse { error ->
            return CliResult(false, command, error.message ?: "Unable to start su")
        }

    val output = StringBuilder()
    val readerThread =
        Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> output.appendLine(line) }
            }
        }.apply { start() }

    val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        readerThread.join(500)
        return CliResult(false, command, output.toString().trim().ifBlank { "Command timed out." })
    }
    readerThread.join(500)
    return CliResult(process.exitValue() == 0, command, output.toString().trim())
}

fun parseStats(output: String): LiveStats? {
    val lastJson = output.lineSequence().lastOrNull { it.contains("\"up\"") && it.contains("\"down\"") } ?: return null
    val up = Regex(""""up"\s*:\s*([0-9.]+)""").find(lastJson)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    val down = Regex(""""down"\s*:\s*([0-9.]+)""").find(lastJson)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    return if (up != null && down != null) LiveStats(up = up, down = down) else null
}

fun parseStatsSamples(output: String): List<LiveStats> {
    val parsedAt = System.currentTimeMillis()
    return output
        .lineSequence()
        .filter { it.contains("\"up\"") && it.contains("\"down\"") }
        .mapIndexedNotNull { index, line ->
            parseStats(line)?.copy(timestampMillis = parsedAt + index)
        }
        .toList()
}

fun normalizeStatsResult(result: CliResult): CliResult {
    val jsonLines = result.output.lineSequence().filter { it.contains("\"up\"") && it.contains("\"down\"") }.toList()
    if (jsonLines.isEmpty()) return result
    val latest = parseStats(jsonLines.last()) ?: return result
    return CliResult(
        success = true,
        command = result.command,
        output =
            buildString {
                appendLine(jsonLines.takeLast(6).joinToString(separator = "\n"))
                if (!result.success) {
                    append("Latest sample kept; command exited after partial output.")
                }
            }.trim(),
        summary = "Latest sample: ${formatRate(latest.up)} up / ${formatRate(latest.down)} down",
    )
}

fun parseRouteSummary(output: String): RouteSummary {
    val buckets = RuleBucket.entries.associateWith { mutableListOf<String>() }
    var current: RuleBucket? = null

    output.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) return@forEach
        val heading =
            RuleBucket.entries.firstOrNull { bucket ->
                line.equals("${bucket.cli} domain suffixes:", ignoreCase = true)
            }
        if (heading != null) {
            current = heading
            return@forEach
        }
        current?.let { bucket ->
            if (!line.endsWith(":")) buckets.getValue(bucket).add(line)
        }
    }

    return RouteSummary(
        proxy = buckets.getValue(RuleBucket.Proxy),
        direct = buckets.getValue(RuleBucket.Direct),
        block = buckets.getValue(RuleBucket.Block),
        warp = buckets.getValue(RuleBucket.Warp),
    )
}

suspend fun loadRuntimeRuleSets(): CliResult =
    runRootCommand(
        """
        rules_dir='/data/adb/modules/MagicNet/.config/sing-box/rules'
        echo 'runtime rule sets:'
        [ -d "${'$'}rules_dir" ] || exit 0
        find "${'$'}rules_dir" -type f -name '*.srs' 2>/dev/null | while read -r file; do
          name="${'$'}{file##*/}"
          printf '%s\n' "${'$'}{name%.srs}"
        done | sort -u
        """.trimIndent(),
    )

suspend fun loadModuleBridgeStatus(): CliResult =
    runRootCommand(
        "printf 'MagicNet module: '; " +
            "sed -n 's/^version=//p' /data/adb/modules/MagicNet/module.prop | head -n 1; " +
            "test -x $MAGICNET_CLI && echo 'CLI: executable' || echo 'CLI: missing'; " +
            "$MAGICNET_CLI mcp status 2>&1 | head -n 4",
    )

fun parseRuntimeRuleSummary(output: String): RuntimeRuleSummary =
    RuntimeRuleSummary(
        output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.endsWith(":") }
            .distinct()
            .toList(),
    )

fun parseHealthEntry(line: String): HealthEntry? {
    val match = Regex("""\[(ok|warn|error)]\s*(.*)""", RegexOption.IGNORE_CASE).find(line.trim()) ?: return null
    val severity =
        when (match.groupValues[1].lowercase()) {
            "ok" -> HealthSeverity.Ok
            "warn" -> HealthSeverity.Warn
            else -> HealthSeverity.Error
        }
    val body = match.groupValues[2].trim()
    val title = body.substringBefore(":").trim().ifBlank { body }
    val details = body.substringAfter(":", missingDelimiterValue = "").trim()
    return HealthEntry(severity, title, details)
}

fun parseModuleBridge(output: String): ModuleBridge {
    fun valueAfter(prefix: String): String =
        output.lineSequence()
            .firstOrNull { it.trim().startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(prefix, "")
            ?.trim()
            .orEmpty()

    return ModuleBridge(
        version = valueAfter("MagicNet module:"),
        cli = valueAfter("CLI:"),
        enabled = valueAfter("enabled="),
        bind = valueAfter("bind="),
        port = valueAfter("port="),
        secretSet = valueAfter("secret_set="),
    )
}

fun formatRate(value: Float): String =
    when {
        value >= 1024 * 1024 -> "${"%.1f".format(value / 1024f / 1024f)} MB/s"
        value >= 1024 -> "${"%.1f".format(value / 1024f)} KB/s"
        else -> "${value.toInt()} B/s"
    }

fun isSafeDomain(value: String): Boolean =
    value.matches(Regex("""[a-z0-9][a-z0-9._-]*\.[a-z0-9][a-z0-9._-]*"""))

fun isSafePackage(value: String): Boolean =
    value.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+"""))

fun parseAppSummary(output: String): AppSummary {
    var mode = "blacklist"
    val proxy = mutableListOf<String>()
    val bypass = mutableListOf<String>()
    var current: AppTarget? = null

    output.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) return@forEach
        when {
            line.startsWith("mode=") -> {
                mode = line.substringAfter("mode=").trim().ifBlank { mode }
                current = null
            }
            line.equals("proxy apps:", ignoreCase = true) -> current = AppTarget.Proxy
            line.equals("bypass apps:", ignoreCase = true) -> current = AppTarget.Bypass
            current == AppTarget.Proxy -> proxy.add(line)
            current == AppTarget.Bypass -> bypass.add(line)
        }
    }

    return AppSummary(mode, proxy, bypass)
}

suspend fun checkForUpdates(t: UiText): UpdateState =
    withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME.trim()
        val latest =
            runCatching { fetchLatestReleaseTag() }
                .getOrElse { error ->
                    return@withContext UpdateState(t.updateCheckFailed, error.message ?: t.unableToReachGithub, false)
                }
        val latestVersion = latest.removePrefix("v").trim()
        val hasUpdate = latestVersion != current
        UpdateState(
            title = if (hasUpdate) t.updateAvailable(latest) else t.alreadyUpToDate,
            summary = if (hasUpdate) t.installedVersion(current) else t.installedMatchesLatest,
            checking = false,
        )
    }

fun fetchLatestReleaseTag(): String {
    val connection = URL(MAGICBOX_LATEST_RELEASE_API).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 6000
    connection.readTimeout = 6000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "MagicBox/${BuildConfig.VERSION_NAME}")

    return connection.use {
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) throw IllegalStateException("No GitHub release has been published yet.")
        if (responseCode !in 200..299) throw IllegalStateException("GitHub returned HTTP $responseCode.")
        val body = inputStream.bufferedReader().use { reader -> reader.readText() }
        Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            ?: throw IllegalStateException("Latest release tag was not found.")
    }
}

inline fun <T> HttpURLConnection.use(block: HttpURLConnection.() -> T): T =
    try {
        block()
    } finally {
        disconnect()
    }

fun openUri(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.recoverCatching { error ->
        if (error is ActivityNotFoundException) Unit else throw error
    }
}

fun readBackgroundPalette(context: Context, style: BackgroundStyle): BackgroundPalette {
    if (style != BackgroundStyle.Monet || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
        return BackgroundPalette.forStyle(style)
    }
    return runCatching {
        val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        val primary = colors?.primaryColor?.toArgb()?.let(::Color)
        val secondary = colors?.secondaryColor?.toArgb()?.let(::Color)
        val tertiary = colors?.tertiaryColor?.toArgb()?.let(::Color)
        BackgroundPalette(
            base = Color(0xFF07080D),
            primary = primary?.darkAccent() ?: BackgroundPalette.forStyle(style).primary,
            secondary = secondary?.darkAccent() ?: tertiary?.darkAccent() ?: BackgroundPalette.forStyle(style).secondary,
        )
    }.getOrElse {
        BackgroundPalette.forStyle(style)
    }
}

fun Color.darkAccent(): Color =
    copy(
        red = (red * 0.82f).coerceIn(0.08f, 0.85f),
        green = (green * 0.82f).coerceIn(0.08f, 0.85f),
        blue = (blue * 0.82f).coerceIn(0.08f, 0.85f),
        alpha = 1f,
    )
