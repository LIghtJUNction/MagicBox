package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToolsPageContent(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    backgroundStyle: BackgroundStyle,
    onBackgroundChange: (BackgroundStyle) -> Unit,
    module: CliResult?,
    subList: CliResult?,
    subscriptionDraft: String,
    subscriptionSaved: Boolean,
    subListCopied: Boolean,
    nodeList: CliResult?,
    currentNode: CliResult?,
    nodeListCopied: Boolean,
    networkSnapshot: CliResult?,
    networkSnapshotCopied: Boolean,
    dnsStatus: CliResult?,
    pendingDnsProfile: DnsProfile?,
    warpStatus: CliResult?,
    pendingWarpAction: WarpAction?,
    pendingWarpSelector: WarpSelectorAction?,
    routes: CliResult?,
    warpDomain: String,
    draft: String,
    draftCopied: Boolean,
    showFullDraft: Boolean,
    toolResult: CliResult?,
    toolOutputCopied: Boolean,
    commandHistory: List<CommandHistoryEntry>,
    loading: Boolean,
    actions: ToolsPageActions,
) {
    val t = LocalUiText.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(6.dp))
        PageMasthead(t.tools)
        ToolsPreferencesCard(language, onLanguageChange, backgroundStyle, onBackgroundChange)
        ModuleBridgeCard(module)
        ConfigValidationCard()
        McpControlCard(module, loading = loading, onAction = actions.onMcpAction)
        SupervisorControlCard()
        WebUiStatusCard()
        SubscriptionCard(
            result = subList,
            draft = subscriptionDraft,
            loading = loading,
            copied = subListCopied,
            saved = subscriptionSaved,
            onDraftChange = actions.onSubscriptionDraftChange,
            onSave = actions.onSaveSubscriptions,
            onReload = actions.onReloadSubscriptions,
            onUpdateSingBox = actions.onUpdateSingBox,
            onUpdateAll = actions.onUpdateAllSubscriptions,
            onCopy = actions.onCopySubscription,
            onShare = actions.onShareSubscription,
        )
        BackupCard()
        NodeListCard(
            result = nodeList,
            currentResult = currentNode,
            loading = loading,
            copied = nodeListCopied,
            onReload = actions.onReloadNodes,
            onUseNode = actions.onUseNode,
            onCopy = actions.onCopyNodes,
            onShare = actions.onShareNodes,
        )
        ProxyGroupsCard()
        NetworkSnapshotCard(
            result = networkSnapshot,
            loading = loading,
            copied = networkSnapshotCopied,
            onReload = actions.onReloadNetworkSnapshot,
            onCopy = actions.onCopyNetworkSnapshot,
            onShare = actions.onShareNetworkSnapshot,
        )
        SysrouteSnapshotCard()
        DnsSettingsCard(
            result = dnsStatus,
            loading = loading,
            pendingProfile = pendingDnsProfile,
            onReload = actions.onReloadDns,
            onRequestProfile = actions.onRequestDnsProfile,
            onCancelProfile = actions.onCancelDnsProfile,
            onConfirmProfile = actions.onConfirmDnsProfile,
        )
        WarpSettingsCard(
            result = warpStatus,
            loading = loading,
            pendingAction = pendingWarpAction,
            onReload = actions.onReloadWarp,
            onTest = actions.onTestWarp,
            onRequestAction = actions.onRequestWarpAction,
            onCancelAction = actions.onCancelWarpAction,
            onConfirmAction = actions.onConfirmWarpAction,
        )
        WarpRouteCard(
            routes = routes,
            warpStatus = warpStatus,
            domain = warpDomain,
            loading = loading,
            pendingSelector = pendingWarpSelector,
            onDomainChange = actions.onWarpDomainChange,
            onAddDomain = actions.onAddWarpDomain,
            onRemoveDomain = actions.onRemoveWarpDomain,
            onReload = actions.onReloadWarpRoutes,
            onRequestSelector = actions.onRequestWarpSelector,
            onCancelSelector = actions.onCancelWarpSelector,
            onConfirmSelector = actions.onConfirmWarpSelector,
        )
        BlocklistCard()
        LogsViewerCard()
        EcaptureControlCard()
        DiagnosticsActionsCard(loading = loading, onRunTool = actions.onRunTool, onOpenPanel = actions.onOpenPanel)
        IssueDraftCard(
            draft = draft,
            copied = draftCopied,
            showFullDraft = showFullDraft,
            loading = loading,
            onReload = actions.onReloadIssueDraft,
            onRefreshReport = actions.onRefreshIssueReport,
            onDraft = actions.onDraftIssue,
            onCopy = actions.onCopyIssueDraft,
            onShare = actions.onShareIssueDraft,
            onTogglePreview = actions.onToggleIssuePreview,
        )
        ToolOutputCard(toolResult, toolOutputCopied, actions.onCopyToolOutput, actions.onShareToolOutput)
        CommandHistoryCard(
            entries = commandHistory,
            loading = loading,
            onClear = actions.onClearHistory,
            onRerun = actions.onRerunHistory,
            onCopy = actions.onCopyHistory,
            onShare = actions.onShareHistory,
            onDelete = actions.onDeleteHistory,
        )
        UpdateCheckCard()
        Card {
            Label(t.attribution)
            Body(t.attributionText)
        }
        Spacer(Modifier.height(10.dp))
    }
}

data class ToolsPageActions(
    val onRunTool: (String) -> Unit,
    val onMcpAction: (String) -> Unit,
    val onSubscriptionDraftChange: (String) -> Unit,
    val onSaveSubscriptions: () -> Unit,
    val onReloadSubscriptions: () -> Unit,
    val onUpdateSingBox: () -> Unit,
    val onUpdateAllSubscriptions: () -> Unit,
    val onCopySubscription: () -> Unit,
    val onShareSubscription: () -> Unit,
    val onReloadNodes: () -> Unit,
    val onUseNode: (String) -> Unit,
    val onCopyNodes: () -> Unit,
    val onShareNodes: () -> Unit,
    val onReloadNetworkSnapshot: () -> Unit,
    val onCopyNetworkSnapshot: () -> Unit,
    val onShareNetworkSnapshot: () -> Unit,
    val onReloadDns: () -> Unit,
    val onRequestDnsProfile: (DnsProfile) -> Unit,
    val onCancelDnsProfile: () -> Unit,
    val onConfirmDnsProfile: (DnsProfile) -> Unit,
    val onReloadWarp: () -> Unit,
    val onTestWarp: () -> Unit,
    val onRequestWarpAction: (WarpAction) -> Unit,
    val onCancelWarpAction: () -> Unit,
    val onConfirmWarpAction: (WarpAction) -> Unit,
    val onWarpDomainChange: (String) -> Unit,
    val onAddWarpDomain: () -> Unit,
    val onRemoveWarpDomain: (String) -> Unit,
    val onReloadWarpRoutes: () -> Unit,
    val onRequestWarpSelector: (WarpSelectorAction) -> Unit,
    val onCancelWarpSelector: () -> Unit,
    val onConfirmWarpSelector: (WarpSelectorAction) -> Unit,
    val onOpenPanel: () -> Unit,
    val onReloadIssueDraft: () -> Unit,
    val onRefreshIssueReport: () -> Unit,
    val onDraftIssue: () -> Unit,
    val onCopyIssueDraft: () -> Unit,
    val onShareIssueDraft: () -> Unit,
    val onToggleIssuePreview: () -> Unit,
    val onCopyToolOutput: () -> Unit,
    val onShareToolOutput: () -> Unit,
    val onClearHistory: () -> Unit,
    val onRerunHistory: (CommandHistoryEntry) -> Unit,
    val onCopyHistory: (CommandHistoryEntry) -> Unit,
    val onShareHistory: (CommandHistoryEntry) -> Unit,
    val onDeleteHistory: (CommandHistoryEntry) -> Unit,
)
