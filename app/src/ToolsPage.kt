package com.github.lightjunction.magicbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

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
    var nodeList by remember { mutableStateOf<CliResult?>(null) }
    var currentNode by remember { mutableStateOf<CliResult?>(null) }
    var networkSnapshot by remember { mutableStateOf<CliResult?>(null) }
    var dnsStatus by remember { mutableStateOf<CliResult?>(null) }
    var warpStatus by remember { mutableStateOf<CliResult?>(null) }
    var health by remember { mutableStateOf<CliResult?>(null) }
    var routes by remember { mutableStateOf<CliResult?>(null) }
    var toolResult by remember { mutableStateOf<CliResult?>(null) }
    var subscriptionDraft by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var draftCopied by remember { mutableStateOf(false) }
    var subListCopied by remember { mutableStateOf(false) }
    var nodeListCopied by remember { mutableStateOf(false) }
    var networkSnapshotCopied by remember { mutableStateOf(false) }
    var pendingDnsProfile by remember { mutableStateOf<DnsProfile?>(null) }
    var pendingWarpAction by remember { mutableStateOf<WarpAction?>(null) }
    var pendingWarpSelector by remember { mutableStateOf<WarpSelectorAction?>(null) }
    var warpDomain by remember { mutableStateOf("") }
    var subscriptionSaved by remember { mutableStateOf(false) }
    var toolOutputCopied by remember { mutableStateOf(false) }
    var showFullDraft by remember { mutableStateOf(false) }
    var commandHistory by remember { mutableStateOf(loadCommandHistory(context)) }
    var loading by remember { mutableStateOf(false) }
    fun applySnapshot(snapshot: ToolsRuntimeSnapshot) {
        module = snapshot.module
        subList = snapshot.subscription
        nodeList = snapshot.nodes
        currentNode = snapshot.currentNode
        dnsStatus = snapshot.dns
        warpStatus = snapshot.warp
        health = snapshot.health
        routes = snapshot.routes
    }

    LaunchedEffect(Unit) {
        applySnapshot(loadToolsRuntimeSnapshot())
    }

    fun runTool(args: String) {
        loading = true
        scope.launch {
            toolResult = if (args == "sub update sing-box" || args == "sub update-all") runMagicNetLong(args) else runMagicNet(args)
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            if (args.startsWith("sub ")) {
                val refresh = reloadSubscriptionRuntime()
                subList = refresh.subscription
                nodeList = refresh.nodes
                currentNode = refresh.currentNode
                subListCopied = false
                subscriptionSaved = false
            }
            if (args.startsWith("dns ")) dnsStatus = runMagicNet("dns status")
            if (args.startsWith("warp ")) warpStatus = runMagicNet("warp status")
            health = runMagicNet("health")
            routes = runMagicNet("route list")
            loading = false
        }
    }

    fun runMcpAction(action: String) {
        loading = true
        scope.launch {
            toolResult = runMagicNet("mcp $action")
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            module = loadModuleBridgeStatus()
            loading = false
        }
    }

    suspend fun refreshRuntimeState() {
        dnsStatus = runMagicNet("dns status")
        warpStatus = runMagicNet("warp status")
        health = runMagicNet("health")
    }

    fun reloadSubscriptions() {
        loading = true
        scope.launch {
            val refresh = reloadSubscriptionRuntime()
            subList = refresh.subscription
            nodeList = refresh.nodes
            currentNode = refresh.currentNode
            subListCopied = false
            subscriptionSaved = false
            loading = false
        }
    }

    fun saveSubscriptions() {
        loading = true
        scope.launch {
            val saved = saveSubscriptionRuntime(subscriptionDraft, t)
            toolResult = saved.result
            toolOutputCopied = false
            subList = saved.refresh.subscription
            nodeList = saved.refresh.nodes
            currentNode = saved.refresh.currentNode
            subListCopied = false
            nodeListCopied = false
            subscriptionSaved = saved.result.success
            if (saved.result.success) subscriptionDraft = ""
            loading = false
        }
    }

    fun rerunHistory(entry: CommandHistoryEntry) {
        commandHistoryArgs(entry.command)?.let { runTool(it) }
    }

    fun removeHistory(entry: CommandHistoryEntry) {
        commandHistory = removeCommandHistory(context, entry)
    }

    fun refreshIssueReport() {
        loading = true
        scope.launch {
            val report = refreshSupportReport()
            applySnapshot(report.runtime)
            toolResult = report.support
            toolOutputCopied = false
            commandHistory = appendCommandHistory(context, report.support)
            draft = report.draft
            draftCopied = false
            showFullDraft = false
            loading = false
        }
    }

    fun copySubscriptionState() {
        subListCopied = copySubscriptionState(context, subList)
    }

    fun shareSubscriptionState() {
        shareSubscriptionState(context, subList)
    }

    fun reloadNodes() {
        loading = true
        scope.launch {
            nodeList = runMagicNet("node list")
            currentNode = runMagicNet("node current")
            nodeListCopied = false
            loading = false
        }
    }

    fun useNode(node: String) {
        loading = true
        scope.launch {
            toolResult = runMagicNet("node use ${shellQuote(node)}")
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            currentNode = runMagicNet("node current")
            loading = false
        }
    }

    fun reloadNetworkSnapshot() {
        loading = true
        scope.launch {
            networkSnapshot = loadNetworkSnapshot(t)
            networkSnapshotCopied = false
            loading = false
        }
    }

    fun reloadDns() {
        loading = true
        scope.launch {
            dnsStatus = runMagicNet("dns status")
            pendingDnsProfile = null
            loading = false
        }
    }

    fun setDnsProfile(profile: DnsProfile) {
        loading = true
        pendingDnsProfile = null
        scope.launch {
            toolResult = runMagicNet("dns set ${profile.cli}")
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            refreshRuntimeState()
            loading = false
        }
    }

    fun reloadWarp() {
        loading = true
        scope.launch {
            warpStatus = runMagicNet("warp status")
            pendingWarpAction = null
            pendingWarpSelector = null
            loading = false
        }
    }

    fun runWarpTool(args: String) {
        loading = true
        pendingWarpAction = null
        scope.launch {
            toolResult = runMagicNet("warp $args")
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            warpStatus = runMagicNet("warp status")
            loading = false
        }
    }

    fun reloadWarpRoutes() {
        loading = true
        scope.launch {
            routes = runMagicNet("route list")
            warpStatus = runMagicNet("warp status")
            pendingWarpSelector = null
            loading = false
        }
    }

    fun setWarpSelector(action: WarpSelectorAction) {
        loading = true
        pendingWarpSelector = null
        scope.launch {
            toolResult = runMagicNet("warp ${action.cli}")
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            warpStatus = runMagicNet("warp status")
            routes = runMagicNet("route list")
            health = runMagicNet("health")
            loading = false
        }
    }

    fun addWarpRouteDomain() {
        val clean = warpDomain.trim().lowercase()
        if (!isSafeDomain(clean)) {
            toolResult = CliResult(false, "$MAGICNET_CLI route add-domain warp <domain>", t.invalidDomain(warpDomain))
            toolOutputCopied = false
            return
        }
        loading = true
        scope.launch {
            toolResult = runMagicNet("route add-domain warp $clean")
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            routes = runMagicNet("route list")
            if (toolResult?.success == true) warpDomain = ""
            loading = false
        }
    }

    fun removeWarpRouteDomain(domain: String) {
        loading = true
        scope.launch {
            toolResult = runMagicNet("route remove-domain warp $domain")
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            routes = runMagicNet("route list")
            loading = false
        }
    }

    fun openSingBoxPanel() {
        loading = true
        scope.launch {
            toolResult = launchSingBoxWebUi(context, t)
            toolOutputCopied = false
            toolResult?.let { commandHistory = appendCommandHistory(context, it) }
            loading = false
        }
    }

    ToolsPageContent(
        language = language,
        onLanguageChange = onLanguageChange,
        backgroundStyle = backgroundStyle,
        onBackgroundChange = onBackgroundChange,
        module = module,
        subList = subList,
        subscriptionDraft = subscriptionDraft,
        subscriptionSaved = subscriptionSaved,
        subListCopied = subListCopied,
        nodeList = nodeList,
        currentNode = currentNode,
        nodeListCopied = nodeListCopied,
        networkSnapshot = networkSnapshot,
        networkSnapshotCopied = networkSnapshotCopied,
        dnsStatus = dnsStatus,
        pendingDnsProfile = pendingDnsProfile,
        warpStatus = warpStatus,
        pendingWarpAction = pendingWarpAction,
        pendingWarpSelector = pendingWarpSelector,
        routes = routes,
        warpDomain = warpDomain,
        draft = draft,
        draftCopied = draftCopied,
        showFullDraft = showFullDraft,
        toolResult = toolResult,
        toolOutputCopied = toolOutputCopied,
        commandHistory = commandHistory,
        loading = loading,
        actions =
            ToolsPageActions(
                onRunTool = ::runTool,
                onMcpAction = ::runMcpAction,
                onSubscriptionDraftChange = {
                    subscriptionDraft = it
                    subscriptionSaved = false
                },
                onSaveSubscriptions = ::saveSubscriptions,
                onReloadSubscriptions = ::reloadSubscriptions,
                onUpdateSingBox = { runTool("sub update sing-box") },
                onUpdateAllSubscriptions = { runTool("sub update-all") },
                onCopySubscription = ::copySubscriptionState,
                onShareSubscription = ::shareSubscriptionState,
                onReloadNodes = ::reloadNodes,
                onUseNode = ::useNode,
                onCopyNodes = { nodeListCopied = copyNodeList(context, nodeList) },
                onShareNodes = { shareNodeList(context, nodeList) },
                onReloadNetworkSnapshot = ::reloadNetworkSnapshot,
                onCopyNetworkSnapshot = { networkSnapshotCopied = copyNetworkSnapshot(context, networkSnapshot) },
                onShareNetworkSnapshot = { shareNetworkSnapshot(context, networkSnapshot) },
                onReloadDns = ::reloadDns,
                onRequestDnsProfile = { pendingDnsProfile = it },
                onCancelDnsProfile = { pendingDnsProfile = null },
                onConfirmDnsProfile = ::setDnsProfile,
                onReloadWarp = ::reloadWarp,
                onTestWarp = { runWarpTool("test") },
                onRequestWarpAction = { pendingWarpAction = it },
                onCancelWarpAction = { pendingWarpAction = null },
                onConfirmWarpAction = { runWarpTool(it.cli) },
                onWarpDomainChange = { warpDomain = it },
                onAddWarpDomain = ::addWarpRouteDomain,
                onRemoveWarpDomain = ::removeWarpRouteDomain,
                onReloadWarpRoutes = ::reloadWarpRoutes,
                onRequestWarpSelector = { pendingWarpSelector = it },
                onCancelWarpSelector = { pendingWarpSelector = null },
                onConfirmWarpSelector = ::setWarpSelector,
                onOpenPanel = ::openSingBoxPanel,
                onReloadIssueDraft = {
                    draft = ""
                    draftCopied = false
                    showFullDraft = false
                    loading = true
                    scope.launch {
                        applySnapshot(loadToolsRuntimeSnapshot())
                        loading = false
                    }
                },
                onRefreshIssueReport = ::refreshIssueReport,
                onDraftIssue = {
                    draft =
                        buildIssueDraft(
                            module = module,
                            subscription = subList,
                            health = health,
                            routes = routes,
                            lastCommand = toolResult,
                            nodes = nodeList,
                            currentNode = currentNode,
                            dns = dnsStatus,
                            warp = warpStatus,
                        )
                    draftCopied = false
                    showFullDraft = false
                },
                onCopyIssueDraft = { draftCopied = copyIssueDraft(context, draft) },
                onShareIssueDraft = { shareIssueDraft(context, draft) },
                onToggleIssuePreview = { showFullDraft = !showFullDraft },
                onCopyToolOutput = { toolOutputCopied = copyToolOutput(context, toolResult) },
                onShareToolOutput = { shareToolOutput(context, toolResult) },
                onClearHistory = { commandHistory = clearCommandHistory(context) },
                onRerunHistory = ::rerunHistory,
                onCopyHistory = { copyCommandHistoryEntry(context, it) },
                onShareHistory = { shareCommandHistoryEntry(context, it) },
                onDeleteHistory = ::removeHistory,
            ),
    )
}
