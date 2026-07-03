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
fun AppsPage() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appPolicy by remember { mutableStateOf<CliResult?>(null) }
    var packages by remember { mutableStateOf<CliResult?>(null) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var installedPackageNames by remember { mutableStateOf<Set<String>?>(null) }
    var lastCommand by remember { mutableStateOf<CliResult?>(null) }
    var packageName by remember { mutableStateOf("") }
    var packageBatchDraft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var policyFilter by remember { mutableStateOf("") }
    var copiedPolicy by remember { mutableStateOf(false) }
    var copiedInstalledPackages by remember { mutableStateOf(false) }
    var copiedAppCommand by remember { mutableStateOf(false) }
    var copiedRemovalPlan by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(AppTarget.Proxy) }
    var pendingRecommendedBypass by remember { mutableStateOf(false) }
    var pendingStaleCleanup by remember { mutableStateOf(false) }
    var pendingVisibleRemoval by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        copiedPolicy = false
        copiedInstalledPackages = false
        scope.launch {
            val policyJob = async { runMagicNet("app list") }
            val packagesJob = async { runMagicNet("app packages ${query.trim()}".trim()) }
            val installedJob = async { loadInstalledApps(context, query) }
            val installedNamesJob = async { loadInstalledPackageNames(context) }
            appPolicy = policyJob.await()
            val packageResult = packagesJob.await()
            packages = packageResult
            val localApps = installedJob.await()
            installedPackageNames = installedNamesJob.await()
            installedApps =
                localApps.ifEmpty {
                    if (packageResult.success && packageResult.output.isNotBlank()) loadInstalledAppsFromPackages(context, packageResult.output, query) else emptyList()
                }
            loading = false
        }
    }

    LaunchedEffect(lastCommand?.command, lastCommand?.output) {
        copiedAppCommand = false
    }

    fun addPackage(pkg: String = packageName, targetPolicy: AppTarget = target) {
        val clean = pkg.trim()
        if (!isSafePackage(clean)) {
            lastCommand = CliResult(false, "$MAGICNET_CLI app add <package> ${targetPolicy.cli}", t.invalidPackage(pkg))
            return
        }
        loading = true
        copiedPolicy = false
        scope.launch {
            lastCommand = runMagicNet("app add $clean ${targetPolicy.cli}")
            appPolicy = runMagicNet("app list")
            packageName = ""
            loading = false
        }
    }

    fun removePackage(pkg: String) {
        loading = true
        copiedPolicy = false
        scope.launch {
            lastCommand = runMagicNet("app remove $pkg ${target.cli}")
            appPolicy = runMagicNet("app list")
            loading = false
        }
    }

    fun recommendedBypassCandidates(): List<String> {
        val summary = appPolicy?.takeIf { it.success }?.let { parseAppSummary(it.output) }
        val active = (summary?.proxy.orEmpty() + summary?.bypass.orEmpty()).toSet()
        val installed = packages?.takeIf { it.success }?.output?.lineSequence()?.filter { it.isNotBlank() }?.toSet().orEmpty()
        return RECOMMENDED_BYPASS_PACKAGES.filter { pkg ->
            pkg !in active && (installed.isEmpty() || pkg in installed)
        }
    }

    fun requestRecommendedBypass() {
        val candidates = recommendedBypassCandidates()
        if (candidates.isEmpty()) {
            lastCommand = CliResult(true, "$MAGICNET_CLI app add-many bypass", t.noRecommendedBypass)
            return
        }
        pendingRecommendedBypass = true
    }

    fun addRecommendedBypass() {
        val candidates = recommendedBypassCandidates()
        if (candidates.isEmpty()) {
            lastCommand = CliResult(true, "$MAGICNET_CLI app add-many bypass", t.noRecommendedBypass)
            pendingRecommendedBypass = false
            return
        }
        loading = true
        copiedPolicy = false
        pendingRecommendedBypass = false
        scope.launch {
            lastCommand = runMagicNet("app add-many bypass ${candidates.joinToString(" ")}")
            appPolicy = runMagicNet("app list")
            loading = false
        }
    }

    fun importPackages() {
        val batch = parsePackageBatch(packageBatchDraft)
        if (batch.valid.isEmpty()) {
            lastCommand = CliResult(false, "$MAGICNET_CLI app add-many ${target.cli}", t.noValidPackages())
            return
        }
        loading = true
        copiedPolicy = false
        scope.launch {
            val latestPolicy = runMagicNet("app list")
            if (!latestPolicy.success) {
                lastCommand = latestPolicy
                appPolicy = latestPolicy
                loading = false
                return@launch
            }
            val latestSummary = parseAppSummary(latestPolicy.output)
            val existing =
                if (target == AppTarget.Proxy) latestSummary.proxy.toSet()
                else latestSummary.bypass.toSet()
            val imported = addPackageBatch(target, batch, existing, t)
            lastCommand = imported.result
            appPolicy = runMagicNet("app list")
            if (imported.wroteAny) packageBatchDraft = ""
            loading = false
        }
    }

    fun cleanStalePackages(stalePackages: List<String>) {
        if (stalePackages.isEmpty()) return
        loading = true
        copiedPolicy = false
        pendingStaleCleanup = false
        scope.launch {
            val cleanup = removeStaleAppPolicies(target, stalePackages, t)
            lastCommand = cleanup.result
            appPolicy = runMagicNet("app list")
            loading = false
        }
    }

    fun removeVisiblePolicyPackages(packages: List<String>) {
        if (packages.isEmpty()) return
        loading = true
        copiedPolicy = false
        copiedRemovalPlan = false
        pendingVisibleRemoval = false
        scope.launch {
            val cleanup = removeAppPolicies(target, packages, t)
            lastCommand = cleanup.result
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
    val stalePackages =
        if (query.isBlank() && installedPackageNames != null) {
            selected.filterNot { pkg -> pkg in installedPackageNames.orEmpty() }
        } else {
            emptyList()
        }
    val staleCleanupAvailable = query.isBlank() && installedPackageNames != null

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
                copiedPolicy = false
                scope.launch {
                    lastCommand = runMagicNet("app mode $mode")
                    appPolicy = runMagicNet("app list")
                    loading = false
                }
            }
            Spacer(Modifier.height(8.dp))
            Body(if (summary?.mode == "whitelist") t.whitelistHint else t.blacklistHint)
            Spacer(Modifier.height(12.dp))
            SegmentedControl(AppTarget.entries, target, { t.appTarget(it) }) {
                target = it
                copiedPolicy = false
                pendingRecommendedBypass = false
                pendingVisibleRemoval = false
                copiedRemovalPlan = false
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(t.recommendedBypass, enabled = !loading, modifier = Modifier.weight(1f), onClick = ::requestRecommendedBypass)
                SmallButton(t.apply, enabled = !loading, modifier = Modifier.weight(1f)) {
                    loading = true
                    scope.launch {
                        lastCommand = runMagicNet("app apply")
                        appPolicy = runMagicNet("app list")
                        loading = false
                    }
                }
            }
            if (pendingRecommendedBypass) {
                Spacer(Modifier.height(8.dp))
                Body(t.confirmRecommendedBypass(recommendedBypassCandidates().size))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.weight(1f), onClick = ::addRecommendedBypass)
                    SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f)) { pendingRecommendedBypass = false }
                }
            }
        }
        AppPolicyListCard(
            summary = summary,
            appPolicy = appPolicy,
            target = target,
            policyFilter = policyFilter,
            filteredSelected = filteredSelected,
            copiedPolicy = copiedPolicy,
            copiedRemovalPlan = copiedRemovalPlan,
            pendingVisibleRemoval = pendingVisibleRemoval,
            loading = loading,
            onPolicyFilterChange = {
                policyFilter = it
                copiedPolicy = false
                pendingVisibleRemoval = false
                copiedRemovalPlan = false
            },
            onCopyVisiblePackages = { copiedPolicy = copyVisibleAppPolicyPackages(context, filteredSelected) },
            onShareVisiblePackages = { shareVisibleAppPolicyPackages(context, filteredSelected) },
            onRequestVisibleRemoval = {
                pendingVisibleRemoval = true
                copiedRemovalPlan = false
            },
            onConfirmVisibleRemoval = { removeVisiblePolicyPackages(filteredSelected) },
            onCancelVisibleRemoval = {
                pendingVisibleRemoval = false
                copiedRemovalPlan = false
            },
            onCopyRemovalPlan = {
                copyPlainText(
                    context,
                    appPolicyRemovalPlanShareTitle(target),
                    formatAppPolicyRemovalPlan(target, filteredSelected),
                )
                copiedRemovalPlan = true
            },
            onShareRemovalPlan = {
                sharePlainText(
                    context,
                    appPolicyRemovalPlanShareTitle(target),
                    formatAppPolicyRemovalPlan(target, filteredSelected),
                )
            },
            onRemovePackage = ::removePackage,
        )
        InstalledPackagesCard(
            query = query,
            visibleApps = visibleApps,
            activePackages = activePackages,
            packagesResult = packages,
            loading = loading,
            copied = copiedInstalledPackages,
            onQueryChange = {
                query = it
                copiedInstalledPackages = false
            },
            onSearch = ::refresh,
            onAddProxy = { addPackage(it, AppTarget.Proxy) },
            onAddBypass = { addPackage(it, AppTarget.Bypass) },
            onOpenInfo = { openAppDetails(context, it) },
            onOpenApp = { launchInstalledApp(context, it) },
            onCopy = { copiedInstalledPackages = copyInstalledApps(context, visibleApps) },
            onShare = { shareInstalledApps(context, visibleApps) },
        )
        AppStalePolicyCard(
            target = target,
            staleCount = stalePackages.size,
            cleanupAvailable = staleCleanupAvailable,
            pendingCleanup = pendingStaleCleanup,
            loading = loading,
            onRequestCleanup = { pendingStaleCleanup = true },
            onConfirmCleanup = { cleanStalePackages(stalePackages) },
            onCancelCleanup = { pendingStaleCleanup = false },
        )
        AppPolicyInputCard(
            target = target,
            packageName = packageName,
            loading = loading,
            onPackageNameChange = { packageName = it },
            onAdd = { addPackage() },
        )
        AppPackageInspectorCard(
            packageName = packageName,
            installedPackageNames = installedPackageNames,
            summary = summary,
            loading = loading,
            onAddProxy = { addPackage(packageName, AppTarget.Proxy) },
            onAddBypass = { addPackage(packageName, AppTarget.Bypass) },
        )
        PackageBatchInputCard(
            draft = packageBatchDraft,
            loading = loading,
            onDraftChange = { packageBatchDraft = it },
            onImport = ::importPackages,
        )
        AppCommandResultCard(
            lastCommand,
            copiedAppCommand,
            onCopy = { copiedAppCommand = copyAppCommandResult(context, lastCommand) },
            onShare = { shareAppCommandResult(context, lastCommand) },
        )
        Spacer(Modifier.height(10.dp))
    }
}
