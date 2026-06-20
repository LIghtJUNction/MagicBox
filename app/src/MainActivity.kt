package com.github.lightjunction.magicbox

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAGICNET_CLI = "/data/adb/modules/MagicNet/cli"
private val SU_CANDIDATES =
    listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/debug_ramdisk/su",
        "/sbin/su",
        "su",
    )

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MagicBoxApp() }
    }
}

@Composable
private fun MagicBoxApp() {
    var selectedPage by remember { mutableStateOf(MagicPage.Stats) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MagicPalette.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            when (selectedPage) {
                MagicPage.Stats -> StatsPage()
                MagicPage.Rules -> RulesPage()
                MagicPage.Issues -> IssuesPage()
                MagicPage.Module -> ModulePage()
            }
        }
        BottomNavigation(
            selectedPage = selectedPage,
            onSelect = { selectedPage = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StatsPage() {
    val scope = rememberCoroutineScope()
    val samples = remember { mutableStateListOf<LiveStats>() }
    var health by remember { mutableStateOf<CliResult?>(null) }
    var stats by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        scope.launch {
            val statsJob = async { runMagicNet("api stats") }
            val healthJob = async { runMagicNet("health") }
            stats = statsJob.await()
            health = healthJob.await()
            parseStats(stats?.output.orEmpty())?.let {
                samples.add(it)
                while (samples.size > 18) samples.removeAt(0)
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        Header("MagicBox", "MagicNet frontend. No bundled backend. Reads $MAGICNET_CLI.")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Label("Real-time stats")
                    Value(
                        samples.lastOrNull()?.let { "${formatRate(it.up)} up / ${formatRate(it.down)} down" }
                            ?: if (stats?.success == false) "Root unavailable" else "No sample yet",
                    )
                }
                SmallButton(if (loading) "Running" else "Run", enabled = !loading, onClick = ::refresh)
            }
            Spacer(Modifier.height(12.dp))
            StatsLineChart(samples = samples, modifier = Modifier.fillMaxWidth().height(132.dp))
            Spacer(Modifier.height(10.dp))
            CommandBlock("$MAGICNET_CLI api stats", stats)
        }
        HealthPanel(health)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HealthPanel(result: CliResult?) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label("Module health")
                Body(result?.summary ?: "Waiting for cli health")
            }
            StatusPill(if (result?.success == true) "ok" else "check")
        }
        Spacer(Modifier.height(10.dp))
        result?.output
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.take(if (result.success) 9 else 0)
            ?.forEach { line ->
                HealthLine(line)
                Spacer(Modifier.height(6.dp))
            }
    }
}

@Composable
private fun RulesPage() {
    val scope = rememberCoroutineScope()
    var bucket by remember { mutableStateOf(RuleBucket.Proxy) }
    var domain by remember { mutableStateOf("") }
    var routeList by remember { mutableStateOf<CliResult?>(null) }
    var lastCommand by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun reloadRoutes() {
        loading = true
        scope.launch {
            routeList = runMagicNet("route list")
            loading = false
        }
    }

    fun addDomain() {
        val clean = domain.trim().lowercase()
        if (!isSafeDomain(clean)) {
            lastCommand = CliResult(false, "$MAGICNET_CLI route add-domain ${bucket.cli} <domain>", "Invalid domain: $domain")
            return
        }
        loading = true
        scope.launch {
            lastCommand = runMagicNet("route add-domain ${bucket.cli} $clean")
            routeList = runMagicNet("route list")
            domain = ""
            loading = false
        }
    }

    LaunchedEffect(Unit) { reloadRoutes() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        Header("Rules", "Edits are sent to MagicNet CLI. Empty output means the module has no rules yet.")
        SegmentedControl(RuleBucket.entries, bucket, { it.label }) { bucket = it }
        Card {
            Label("Add ${bucket.label.lowercase()} domain")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(domain, "example.com", { domain = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SmallButton("Add", enabled = !loading, onClick = ::addDomain)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Reload", enabled = !loading, modifier = Modifier.weight(1f), onClick = ::reloadRoutes)
                SmallButton(
                    "Apply",
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        loading = true
                        scope.launch {
                            lastCommand = runMagicNet("route apply")
                            loading = false
                        }
                    },
                )
            }
        }
        CommandBlock("$MAGICNET_CLI route list", routeList)
        CommandBlock("Last write command", lastCommand)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun IssuesPage() {
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf<CliResult?>(null) }
    var routes by remember { mutableStateOf<CliResult?>(null) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun loadContext() {
        loading = true
        scope.launch {
            val healthJob = async { runMagicNet("health") }
            val routeJob = async { runMagicNet("route list") }
            health = healthJob.await()
            routes = routeJob.await()
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadContext() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        Header("Diff Issues", "Draft issue text from current MagicNet command output only.")
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Reload context", enabled = !loading, modifier = Modifier.weight(1f), onClick = ::loadContext)
                SmallButton(
                    "Generate draft",
                    enabled = !loading && health != null && routes != null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        draft = buildIssueDraft(health, routes)
                    },
                )
            }
        }
        CommandBlock("$MAGICNET_CLI health", health)
        CommandBlock("$MAGICNET_CLI route list", routes)
        if (draft.isNotBlank()) {
            Card {
                Label("Issue draft")
                Spacer(Modifier.height(8.dp))
                Mono(draft)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ModulePage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var module by remember { mutableStateOf<CliResult?>(null) }
    var updateState by remember {
        mutableStateOf(UpdateState("Version ${BuildConfig.VERSION_NAME}", "Check GitHub releases.", false))
    }

    LaunchedEffect(Unit) {
        module = runRootCommand("ls -la /data/adb/modules/MagicNet && ls -la $MAGICNET_CLI")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        Header("Module", "Frontend-only app. Backend remains the MagicNet Magisk module.")
        CommandBlock("Module path", module)
        ActionCard(
            title = updateState.title,
            summary = updateState.summary,
            action = if (updateState.checking) "Checking" else "Check",
            enabled = !updateState.checking,
            onClick = {
                updateState = UpdateState("Checking updates", "Contacting GitHub releases...", true)
                scope.launch { updateState = checkForUpdates() }
            },
        )
        ActionCard(
            title = "GitHub releases",
            summary = "Open the published APK page.",
            action = "Open",
            onClick = { openUri(context, MAGICBOX_RELEASES_URL) },
        )
        Card {
            Label("Attribution")
            Body("UI direction references YumeBox by YumeYucca. MagicBox keeps its own package, icon, releases, and issue channel.")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header(title: String, summary: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MagicMark()
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(color = MagicPalette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold),
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
    }
}

@Composable
private fun MagicMark() {
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(MagicPalette.ink),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val path =
                Path().apply {
                    moveTo(size.width * 0.06f, size.height * 0.72f)
                    lineTo(size.width * 0.28f, size.height * 0.38f)
                    lineTo(size.width * 0.48f, size.height * 0.58f)
                    lineTo(size.width * 0.72f, size.height * 0.22f)
                    lineTo(size.width * 0.94f, size.height * 0.42f)
                }
            drawPath(path, color = MagicPalette.cyan, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(MagicPalette.orange, radius = 3.2.dp.toPx(), center = Offset(size.width * 0.72f, size.height * 0.22f))
        }
    }
}

@Composable
private fun StatsLineChart(samples: List<LiveStats>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(MagicPalette.control).padding(8.dp)) {
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
            drawLine(MagicPalette.line, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
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
            drawCircle(MagicPalette.control, radius = 4.5.dp.toPx(), center = it)
            drawCircle(MagicPalette.cyan, radius = 3.dp.toPx(), center = it)
        }
    }
}

@Composable
private fun HealthLine(line: String) {
    val color =
        when {
            line.startsWith("[ok]") -> MagicPalette.green
            line.startsWith("[warn]") -> MagicPalette.orange
            line.startsWith("[error]") -> MagicPalette.red
            else -> MagicPalette.muted
        }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Body(line)
    }
}

@Composable
private fun CommandBlock(title: String, result: CliResult?) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(title)
                Body(result?.summary ?: "Not run yet")
            }
            StatusPill(
                when (result?.success) {
                    true -> "ok"
                    false -> "fail"
                    null -> "idle"
                },
            )
        }
        val output = result?.output.orEmpty().trim()
        if (output.isNotBlank() && output != result?.summary) {
            Spacer(Modifier.height(8.dp))
            Mono(output.take(1800))
        }
    }
}

@Composable
private fun ActionCard(
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
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MagicPalette.control).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) MagicPalette.controlSelected else Color.Transparent)
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
private fun SmallButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(38.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (enabled) MagicPalette.cyan else MagicPalette.line)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style =
                TextStyle(
                    color = if (enabled) MagicPalette.buttonText else MagicPalette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextInput(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = MagicPalette.text, fontSize = 15.sp),
        modifier = modifier.height(38.dp).clip(RoundedCornerShape(7.dp)).background(MagicPalette.control).padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) BasicText(placeholder, style = TextStyle(color = MagicPalette.muted, fontSize = 15.sp))
                inner()
            }
        },
    )
}

@Composable
private fun Value(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Label(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Body(text: String) {
    BasicText(text = text, style = TextStyle(color = MagicPalette.muted, fontSize = 13.sp, lineHeight = 18.sp))
}

@Composable
private fun Mono(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = MagicPalette.text, fontSize = 11.sp, lineHeight = 15.sp),
    )
}

@Composable
private fun StatusPill(text: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(MagicPalette.control).padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        BasicText(text = text, style = TextStyle(color = MagicPalette.text, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MagicPalette.surface)
                .border(1.dp, MagicPalette.line, RoundedCornerShape(8.dp))
                .padding(padding),
        content = content,
    )
}

@Composable
private fun BottomNavigation(
    selectedPage: MagicPage,
    onSelect: (MagicPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MagicPalette.surface)
                .border(1.dp, MagicPalette.line, RoundedCornerShape(8.dp))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MagicPage.entries.forEach { page ->
            val selected = page == selectedPage
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) MagicPalette.controlSelected else Color.Transparent)
                        .clickable { onSelect(page) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = page.label,
                    style = TextStyle(color = if (selected) MagicPalette.text else MagicPalette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private suspend fun runMagicNet(args: String): CliResult = runRootCommand("$MAGICNET_CLI $args")

private suspend fun runRootCommand(command: String): CliResult =
    withContext(Dispatchers.IO) {
        var lastResult: CliResult? = null
        for (su in SU_CANDIDATES) {
            val withNamespace = executeSu(listOf(su, "-M", "-c", command), command)
            val result =
                when {
                    withNamespace.success -> withNamespace
                    withNamespace.output.contains("invalid", ignoreCase = true) ||
                        withNamespace.output.contains("unknown option", ignoreCase = true) -> {
                        executeSu(listOf(su, "-c", command), command)
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

private fun rootUnavailableMessage(): String =
    "Root is not granted to MagicBox. Grant com.github.lightjunction.magicbox in KernelSU/Magisk, then tap Run."

private fun executeSu(args: List<String>, command: String): CliResult {
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

    val finished = process.waitFor(6, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        readerThread.join(500)
        return CliResult(false, command, output.toString().trim().ifBlank { "Command timed out." })
    }
    readerThread.join(500)
    return CliResult(process.exitValue() == 0, command, output.toString().trim())
}

private fun parseStats(output: String): LiveStats? {
    val lastJson = output.lineSequence().lastOrNull { it.contains("\"up\"") && it.contains("\"down\"") } ?: return null
    val up = Regex(""""up"\s*:\s*([0-9.]+)""").find(lastJson)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    val down = Regex(""""down"\s*:\s*([0-9.]+)""").find(lastJson)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    return if (up != null && down != null) LiveStats(up = up, down = down) else null
}

private fun formatRate(value: Float): String =
    when {
        value >= 1024 * 1024 -> "${"%.1f".format(value / 1024f / 1024f)} MB/s"
        value >= 1024 -> "${"%.1f".format(value / 1024f)} KB/s"
        else -> "${value.toInt()} B/s"
    }

private fun isSafeDomain(value: String): Boolean =
    value.matches(Regex("""[a-z0-9][a-z0-9._-]*\.[a-z0-9][a-z0-9._-]*"""))

private fun buildIssueDraft(health: CliResult?, routes: CliResult?): String =
    """
    ## MagicBox Diff Issue

    ### Health
    ${health?.output?.take(1200).orEmpty()}

    ### Routes
    ${routes?.output?.take(1200).orEmpty()}
    """.trimIndent()

private suspend fun checkForUpdates(): UpdateState =
    withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME.trim()
        val latest =
            runCatching { fetchLatestReleaseTag() }
                .getOrElse { error ->
                    return@withContext UpdateState("Update check failed", error.message ?: "Unable to reach GitHub releases.", false)
                }
        val latestVersion = latest.removePrefix("v").trim()
        val hasUpdate = latestVersion != current
        UpdateState(
            title = if (hasUpdate) "Update available: $latest" else "Already up to date",
            summary = if (hasUpdate) "Installed $current. Open releases to download the latest APK." else "Installed version matches latest release.",
            checking = false,
        )
    }

private fun fetchLatestReleaseTag(): String {
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

private inline fun <T> HttpURLConnection.use(block: HttpURLConnection.() -> T): T =
    try {
        block()
    } finally {
        disconnect()
    }

private fun openUri(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.recoverCatching { error ->
        if (error is ActivityNotFoundException) Unit else throw error
    }
}

private enum class MagicPage(val label: String) {
    Stats("Stats"),
    Rules("Rules"),
    Issues("Issues"),
    Module("Module"),
}

private enum class RuleBucket(val label: String, val cli: String) {
    Proxy("Proxy", "proxy"),
    Direct("Direct", "direct"),
    Block("Block", "block"),
}

private data class LiveStats(
    val up: Float,
    val down: Float,
) {
    val total: Float = up + down
}

private data class CliResult(
    val success: Boolean,
    val command: String,
    val output: String,
) {
    val summary: String =
        when {
            output.isBlank() && success -> "Command completed with empty output."
            output.isBlank() -> "Command failed with empty output."
            else -> output.lineSequence().firstOrNull().orEmpty()
        }
}

private data class UpdateState(
    val title: String,
    val summary: String,
    val checking: Boolean,
)

private object MagicPalette {
    val background = Color(0xFF090D10)
    val surface = Color(0xFF11181D)
    val control = Color(0xFF1A242A)
    val controlSelected = Color(0xFF22323A)
    val text = Color(0xFFE9F0EF)
    val muted = Color(0xFF91A0A4)
    val line = Color(0xFF263239)
    val ink = Color(0xFF05080A)
    val cyan = Color(0xFF24C6E0)
    val green = Color(0xFF45C783)
    val orange = Color(0xFFFF9A5C)
    val red = Color(0xFFFF6B6B)
    val buttonText = Color(0xFF041014)
}

private const val MAGICBOX_RELEASES_URL = "https://github.com/LIghtJUNction/MagicBox/releases"
private const val MAGICBOX_LATEST_RELEASE_API =
    "https://api.github.com/repos/LIghtJUNction/MagicBox/releases/latest"
