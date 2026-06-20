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
    val scope = rememberCoroutineScope()
    var bucket by remember { mutableStateOf(RuleBucket.Proxy) }
    var domain by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var routeList by remember { mutableStateOf<CliResult?>(null) }
    var runtimeRuleSets by remember { mutableStateOf<CliResult?>(null) }
    var lastCommand by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun reloadRoutes() {
        loading = true
        scope.launch {
            val routesJob = async { runMagicNet("route list") }
            val runtimeJob = async { loadRuntimeRuleSets() }
            routeList = routesJob.await()
            runtimeRuleSets = runtimeJob.await()
            loading = false
        }
    }

    fun addDomain() {
        val clean = domain.trim().lowercase()
        if (!isSafeDomain(clean)) {
            lastCommand = CliResult(false, "$MAGICNET_CLI route add-domain ${bucket.cli} <domain>", t.invalidDomain(domain))
            return
        }
        loading = true
        scope.launch {
            lastCommand = runMagicNet("route add-domain ${bucket.cli} $clean")
            routeList = runMagicNet("route list")
            runtimeRuleSets = loadRuntimeRuleSets()
            domain = ""
            loading = false
        }
    }

    fun removeDomain(domain: String) {
        loading = true
        scope.launch {
            lastCommand = runMagicNet("route remove-domain ${bucket.cli} $domain")
            routeList = runMagicNet("route list")
            runtimeRuleSets = loadRuntimeRuleSets()
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(10.dp))
        PageMasthead(t.rules)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountBadge(t.customRouteSuffixes.uppercase(), summary?.total ?: 0, MagicPalette.rose, Modifier.weight(1f))
            CountBadge(t.runtimeRuleSets.uppercase(), runtimeSummary?.total ?: 0, MagicPalette.green, Modifier.weight(1f))
        }
        if (runtimeSummary != null) {
            Card {
                Label(t.runtimeRuleSets)
                Body(t.runtimeRuleSummary(runtimeSummary.total))
                Spacer(Modifier.height(8.dp))
                runtimeSummary.ruleSets.take(8).forEach { ruleSet ->
                    DomainRow(ruleSet)
                    Spacer(Modifier.height(5.dp))
                }
                if (runtimeSummary.total > 8) Body(t.more(runtimeSummary.total - 8))
            }
        }
        Card {
            SegmentedControl(RuleBucket.entries, bucket, { t.ruleBucket(it) }) { bucket = it }
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
                            runtimeRuleSets = loadRuntimeRuleSets()
                            loading = false
                        }
                    },
                )
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(filter, t.filterDomain, { filter = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusPill("${selectedDomains.size}")
            }
            Spacer(Modifier.height(10.dp))
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
        CommandBlock(t.lastWriteCommand, lastCommand)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun AppsPage() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appPolicy by remember { mutableStateOf<CliResult?>(null) }
    var packages by remember { mutableStateOf<CliResult?>(null) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var lastCommand by remember { mutableStateOf<CliResult?>(null) }
    var packageName by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var policyFilter by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(AppTarget.Proxy) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        scope.launch {
            val policyJob = async { runMagicNet("app list") }
            val packagesJob = async { runMagicNet("app packages ${query.trim()}".trim()) }
            val installedJob = async { loadInstalledApps(context, query) }
            appPolicy = policyJob.await()
            val packageResult = packagesJob.await()
            packages = packageResult
            val localApps = installedJob.await()
            installedApps =
                localApps.ifEmpty {
                    if (packageResult.output.isNotBlank()) loadInstalledAppsFromPackages(context, packageResult.output, query) else emptyList()
                }
            loading = false
        }
    }

    fun addPackage(pkg: String = packageName, targetPolicy: AppTarget = target) {
        val clean = pkg.trim()
        if (!isSafePackage(clean)) {
            lastCommand = CliResult(false, "$MAGICNET_CLI app add <package> ${targetPolicy.cli}", t.invalidPackage(pkg))
            return
        }
        loading = true
        scope.launch {
            lastCommand = runMagicNet("app add $clean ${targetPolicy.cli}")
            appPolicy = runMagicNet("app list")
            packageName = ""
            loading = false
        }
    }

    fun removePackage(pkg: String) {
        loading = true
        scope.launch {
            lastCommand = runMagicNet("app remove $pkg ${target.cli}")
            appPolicy = runMagicNet("app list")
            loading = false
        }
    }

    fun addRecommendedBypass() {
        val summary = appPolicy?.takeIf { it.success }?.let { parseAppSummary(it.output) }
        val active = (summary?.proxy.orEmpty() + summary?.bypass.orEmpty()).toSet()
        val installed = packages?.takeIf { it.success }?.output?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val candidates =
            RECOMMENDED_BYPASS_PACKAGES.filter { pkg ->
                pkg !in active && (installed.isEmpty() || pkg in installed)
            }
        if (candidates.isEmpty()) {
            lastCommand = CliResult(true, "$MAGICNET_CLI app add-many bypass", t.noRecommendedBypass)
            return
        }
        loading = true
        scope.launch {
            lastCommand = runMagicNet("app add-many bypass ${candidates.joinToString(" ")}")
            appPolicy = runMagicNet("app list")
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val summary = appPolicy?.takeIf { it.success }?.let { parseAppSummary(it.output) }
    val selected = if (target == AppTarget.Proxy) summary?.proxy.orEmpty() else summary?.bypass.orEmpty()
    val filteredSelected =
        selected.filter { item ->
            policyFilter.isBlank() || item.contains(policyFilter.trim(), ignoreCase = true)
        }
    val activePackages = (summary?.proxy.orEmpty() + summary?.bypass.orEmpty()).toSet()
    val visibleApps =
        remember(installedApps, packages?.output, query) {
            installedApps.ifEmpty {
                packages?.output?.let { packageOutputToInstalledApps(context, it, query) }.orEmpty()
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(6.dp))
        PageMasthead(t.apps)
        Card(padding = PaddingValues(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Label(t.policy)
                    Body(summary?.let { t.policySummary(it.mode, it.proxy.size, it.bypass.size) } ?: (appPolicy?.summary ?: t.loadingAppPolicy))
                }
                StatusPill(summary?.mode?.let { t.appMode(it) } ?: t.idle)
            }
            Spacer(Modifier.height(10.dp))
            SegmentedControl(listOf("blacklist", "whitelist"), summary?.mode ?: "blacklist", { t.appMode(it) }) { mode ->
                loading = true
                scope.launch {
                    lastCommand = runMagicNet("app mode $mode")
                    appPolicy = runMagicNet("app list")
                    loading = false
                }
            }
            Spacer(Modifier.height(8.dp))
            Body(if (summary?.mode == "whitelist") t.whitelistHint else t.blacklistHint)
            Spacer(Modifier.height(12.dp))
            SegmentedControl(AppTarget.entries, target, { t.appTarget(it) }) { target = it }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.recommendedBypass, enabled = !loading, modifier = Modifier.weight(1f), onClick = ::addRecommendedBypass)
                SmallButton(t.apply, enabled = !loading, modifier = Modifier.weight(1f)) {
                    loading = true
                    scope.launch {
                        lastCommand = runMagicNet("app apply")
                        loading = false
                    }
                }
            }
        }
        Card(padding = PaddingValues(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountBadge(t.appTarget(AppTarget.Proxy).uppercase(), summary?.proxy?.size ?: 0, MagicPalette.rose, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CountBadge(t.appTarget(AppTarget.Bypass).uppercase(), summary?.bypass?.size ?: 0, MagicPalette.green, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(policyFilter, t.filterPolicy, { policyFilter = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                StatusPill("${filteredSelected.size}")
            }
            Spacer(Modifier.height(10.dp))
            when {
                summary == null -> Body(appPolicy?.summary ?: t.loadingAppPolicy)
                filteredSelected.isEmpty() -> Body(t.noPackages(t.appTarget(target).lowercase()))
                else -> {
                    filteredSelected.take(90).forEach { pkg ->
                        ManageRow(
                            title = pkg,
                            detail = t.inPolicy(t.appTarget(target)),
                            action = t.remove,
                            enabled = !loading,
                            onAction = { removePackage(pkg) },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (filteredSelected.size > 90) Body(t.more(filteredSelected.size - 90))
                }
            }
        }
        Card(padding = PaddingValues(10.dp)) {
            Label(t.installedPackages)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(query, t.searchPackage, { query = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SmallButton(t.search, enabled = !loading, onClick = ::refresh)
            }
            Spacer(Modifier.height(8.dp))
            if (visibleApps.isEmpty()) {
                Body(packages?.summary ?: t.searchPackage)
            } else {
                visibleApps.take(80).forEach { app ->
                    InstalledAppRow(
                        app = app,
                        detail = if (app.packageName in activePackages) t.alreadyInPolicy else t.installedPackage,
                        primaryAction = t.appTarget(AppTarget.Proxy),
                        secondaryAction = t.appTarget(AppTarget.Bypass),
                        enabled = !loading,
                        onPrimary = { addPackage(app.packageName, AppTarget.Proxy) },
                        onSecondary = { addPackage(app.packageName, AppTarget.Bypass) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (visibleApps.size > 80) Body(t.more(visibleApps.size - 80))
            }
        }
        Card(padding = PaddingValues(10.dp)) {
            Label(t.addPackage(t.appTarget(target).lowercase()))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(packageName, t.packagePlaceholder, { packageName = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SmallButton(t.add, enabled = !loading, onClick = { addPackage() })
            }
        }
        CommandBlock(t.lastAppCommand, lastCommand)
        Spacer(Modifier.height(10.dp))
    }
}
