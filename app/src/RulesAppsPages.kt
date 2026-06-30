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
fun RulesPage() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bucket by remember { mutableStateOf(RuleBucket.Proxy) }
    var domain by remember { mutableStateOf("") }
    var domainBatchDraft by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var copiedDomains by remember { mutableStateOf(false) }
    var copiedRuntimeRuleSets by remember { mutableStateOf(false) }
    var copiedWriteResult by remember { mutableStateOf(false) }
    var pendingVisibleRemoval by remember { mutableStateOf(false) }
    var routeList by remember { mutableStateOf<CliResult?>(null) }
    var runtimeRuleSets by remember { mutableStateOf<CliResult?>(null) }
    var lastCommand by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun reloadRoutes() {
        loading = true
        copiedDomains = false
        copiedRuntimeRuleSets = false
        scope.launch {
            val routesJob = async { runMagicNet("route list") }
            val runtimeJob = async { loadRuntimeRuleSets() }
            routeList = routesJob.await()
            runtimeRuleSets = runtimeJob.await()
            copiedRuntimeRuleSets = false
            loading = false
        }
    }

    fun addDomain() {
        val clean = domain.trim().lowercase()
        if (!isSafeDomain(clean)) {
            lastCommand = CliResult(false, "$MAGICNET_CLI route add-domain ${bucket.cli} <domain>", t.invalidDomain(domain))
            copiedWriteResult = false
            return
        }
        loading = true
        copiedDomains = false
        scope.launch {
            lastCommand = runMagicNet("route add-domain ${bucket.cli} $clean")
            copiedWriteResult = false
            routeList = runMagicNet("route list")
            runtimeRuleSets = loadRuntimeRuleSets()
            copiedRuntimeRuleSets = false
            domain = ""
            loading = false
        }
    }

    fun addDomainTo(targetBucket: RuleBucket) {
        val clean = domain.trim().lowercase()
        if (!isSafeDomain(clean)) {
            lastCommand = CliResult(false, "$MAGICNET_CLI route add-domain ${targetBucket.cli} <domain>", t.invalidDomain(domain))
            copiedWriteResult = false
            return
        }
        loading = true
        copiedDomains = false
        scope.launch {
            lastCommand = runMagicNet("route add-domain ${targetBucket.cli} $clean")
            copiedWriteResult = false
            routeList = runMagicNet("route list")
            runtimeRuleSets = loadRuntimeRuleSets()
            copiedRuntimeRuleSets = false
            if (lastCommand?.success == true) domain = ""
            loading = false
        }
    }

    fun removeDomain(domain: String) {
        loading = true
        copiedDomains = false
        pendingVisibleRemoval = false
        scope.launch {
            lastCommand = runMagicNet("route remove-domain ${bucket.cli} $domain")
            copiedWriteResult = false
            routeList = runMagicNet("route list")
            runtimeRuleSets = loadRuntimeRuleSets()
            copiedRuntimeRuleSets = false
            loading = false
        }
    }

    fun removeVisibleDomains(domains: List<String>) {
        if (domains.isEmpty()) return
        loading = true
        copiedDomains = false
        pendingVisibleRemoval = false
        scope.launch {
            val cleanup = removeDomainsFromBucket(bucket, domains, t)
            lastCommand = cleanup.result
            copiedWriteResult = false
            routeList = runMagicNet("route list")
            runtimeRuleSets = loadRuntimeRuleSets()
            copiedRuntimeRuleSets = false
            loading = false
        }
    }

    fun importDomains() {
        val batch = parseDomainBatch(domainBatchDraft)
        if (batch.valid.isEmpty()) {
            lastCommand = CliResult(false, "$MAGICNET_CLI route add-domain ${bucket.cli}", t.noValidDomains())
            copiedWriteResult = false
            return
        }
        loading = true
        copiedDomains = false
        scope.launch {
            val latestRoutes = runMagicNet("route list")
            if (!latestRoutes.success) {
                lastCommand = latestRoutes
                copiedWriteResult = false
                routeList = latestRoutes
                loading = false
                return@launch
            }
            val latestSummary = parseRouteSummary(latestRoutes.output)
            val existing = latestSummary.forBucket(bucket).toSet()
            lastCommand = addDomainBatch(bucket, batch, existing, t)
            copiedWriteResult = false
            routeList = runMagicNet("route list")
            runtimeRuleSets = loadRuntimeRuleSets()
            copiedRuntimeRuleSets = false
            if (lastCommand?.success == true) domainBatchDraft = ""
            loading = false
        }
    }

    LaunchedEffect(Unit) { reloadRoutes() }

    val summary = routeList?.takeIf { it.success }?.let { parseRouteSummary(it.output) }
    val runtimeSummary = runtimeRuleSets?.takeIf { it.success }?.let { parseRuntimeRuleSummary(it.output) }
    val selectedDomains =
        summary
            ?.forBucket(bucket)
            ?.filter { item -> filter.isBlank() || item.contains(filter.trim(), ignoreCase = true) }
            .orEmpty()

    fun copyVisibleDomains() {
        if (selectedDomains.isEmpty()) return
        copyPlainText(context, "MagicBox domains", selectedDomains.joinToString("\n"))
        copiedDomains = true
    }

    fun shareVisibleDomains() {
        if (selectedDomains.isEmpty()) return
        sharePlainText(context, "MagicBox domains", selectedDomains.joinToString("\n"))
    }

    fun copyRuntimeRules() {
        val output = runtimeRuleSets?.output.orEmpty().trim()
        if (output.isBlank()) return
        copyPlainText(context, "MagicBox runtime rule sets", output)
        copiedRuntimeRuleSets = true
    }

    fun shareRuntimeRules() {
        val output = runtimeRuleSets?.output.orEmpty().trim()
        if (output.isBlank()) return
        sharePlainText(context, "MagicBox runtime rule sets", output)
    }

    fun copyWriteResult() {
        val result = lastCommand ?: return
        if (result.output.isBlank()) return
        copyPlainText(context, "MagicBox route command", formatToolResult(result))
        copiedWriteResult = true
    }

    fun shareWriteResult() {
        val result = lastCommand ?: return
        if (result.output.isBlank()) return
        sharePlainText(context, "MagicBox route command", formatToolResult(result))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        PageMasthead(t.rules)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountBadge(t.customRouteSuffixes.uppercase(), summary?.total ?: 0, MagicPalette.rose, Modifier.weight(1f))
            CountBadge(t.runtimeRuleSets.uppercase(), runtimeSummary?.total ?: 0, MagicPalette.green, Modifier.weight(1f))
        }
        RuntimeRuleSetsCard(
            result = runtimeRuleSets,
            summary = runtimeSummary,
            loading = loading,
            copied = copiedRuntimeRuleSets,
            onReload = ::reloadRoutes,
            onCopy = ::copyRuntimeRules,
            onShare = ::shareRuntimeRules,
        )
        Card {
            SegmentedControl(RuleBucket.entries, bucket, { t.ruleBucket(it) }) {
                bucket = it
                copiedDomains = false
                pendingVisibleRemoval = false
            }
            Spacer(Modifier.height(12.dp))
            Label(t.addDomain(t.ruleBucket(bucket).lowercase()))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(domain, t.domainPlaceholder, { domain = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SmallButton(t.add, enabled = !loading, onClick = ::addDomain)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = ::reloadRoutes)
                SmallButton(
                    t.apply,
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        loading = true
                        scope.launch {
                            lastCommand = runMagicNet("route apply")
                            copiedWriteResult = false
                            routeList = runMagicNet("route list")
                            runtimeRuleSets = loadRuntimeRuleSets()
                            copiedRuntimeRuleSets = false
                            loading = false
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Label(t.bulkDomains())
            Spacer(Modifier.height(8.dp))
            TextInput(domainBatchDraft, t.bulkDomainPlaceholder(), { domainBatchDraft = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SmallButton(t.importDomains(), enabled = !loading, modifier = Modifier.fillMaxWidth(), onClick = ::importDomains)
        }
        DomainRuleInspectorCard(domain = domain, summary = summary, loading = loading, onAddToBucket = ::addDomainTo)
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(
                    filter,
                    t.filterDomain,
                    {
                        filter = it
                        copiedDomains = false
                        pendingVisibleRemoval = false
                    },
                    Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusPill("${selectedDomains.size}")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copiedDomains) t.copied() else t.copyVisibleDomains(),
                    enabled = selectedDomains.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = ::copyVisibleDomains,
                )
                SmallButton(
                    t.shareVisibleDomains(),
                    enabled = selectedDomains.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    onClick = ::shareVisibleDomains,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (pendingVisibleRemoval && selectedDomains.isNotEmpty()) {
                Body(t.confirmRemoveVisibleDomains(selectedDomains.size, t.ruleBucket(bucket)))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.weight(1f)) {
                        removeVisibleDomains(selectedDomains)
                    }
                    SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f)) {
                        pendingVisibleRemoval = false
                    }
                }
            } else {
                SmallButton(
                    t.removeVisibleDomains(),
                    enabled = !loading && selectedDomains.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    pendingVisibleRemoval = true
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                routeList == null -> Body(t.waitingRouteList)
                routeList?.success != true -> Mono(routeList?.output.orEmpty().take(900))
                selectedDomains.isEmpty() -> Body(t.noDomains(t.ruleBucket(bucket).lowercase()))
                else -> {
                    selectedDomains.take(80).forEach { item ->
                        ManageRow(
                            title = item,
                            detail = "${t.ruleBucket(bucket)} ${t.domainSuffix}",
                            action = t.remove,
                            enabled = !loading,
                            onAction = { removeDomain(item) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (selectedDomains.size > 80) Body(t.more(selectedDomains.size - 80))
                }
            }
        }
        CommandBlock(t.lastWriteCommand, lastCommand, showOutput = true)
        if (!lastCommand?.output.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copiedWriteResult) t.copied() else t.copyToolOutput(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = ::copyWriteResult,
                )
                SmallButton(
                    t.shareToolOutput(),
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = ::shareWriteResult,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
