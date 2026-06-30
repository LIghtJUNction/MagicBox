package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ConnectionsCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<CliResult?>(null) }
    var action by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var copiedMatches by remember { mutableStateOf(false) }
    var copiedClosePlan by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmCloseTop by remember { mutableStateOf(false) }
    var confirmCloseMatched by remember { mutableStateOf(false) }
    var pendingSingleClose by remember { mutableStateOf<ConnectionTarget?>(null) }
    var query by remember { mutableStateOf("") }
    var topCloseCount by remember { mutableStateOf("3") }

    fun refresh() {
        loading = true
        copied = false
        copiedMatches = false
        copiedClosePlan = false
        pendingSingleClose = null
        scope.launch {
            result = runMagicNet("api conns")
            loading = false
        }
    }

    fun closeAll() {
        loading = true
        confirmClose = false
        confirmCloseTop = false
        confirmCloseMatched = false
        pendingSingleClose = null
        copied = false
        copiedMatches = false
        copiedClosePlan = false
        scope.launch {
            action = runMagicNet("api close-all")
            result = runMagicNet("api conns")
            loading = false
        }
    }

    fun closeConnection(target: ConnectionTarget) {
        loading = true
            confirmClose = false
            confirmCloseTop = false
            confirmCloseMatched = false
            pendingSingleClose = null
            copied = false
        copiedMatches = false
        copiedClosePlan = false
        scope.launch {
            action = runMagicNet("api close ${shellQuote(target.id)}")
            result = runMagicNet("api conns")
            loading = false
        }
    }

    fun closeTopConnections(count: String) {
        loading = true
        confirmClose = false
        confirmCloseTop = false
        confirmCloseMatched = false
        pendingSingleClose = null
        copied = false
        copiedMatches = false
        copiedClosePlan = false
        scope.launch {
            action = runMagicNetLong("api close-top $count")
            result = runMagicNet("api conns")
            loading = false
        }
    }

    fun closeMatchedConnections() {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return
        loading = true
        confirmClose = false
        confirmCloseTop = false
        confirmCloseMatched = false
        pendingSingleClose = null
        copied = false
        copiedMatches = false
        copiedClosePlan = false
        scope.launch {
            action = runMagicNetLong("api close-matching ${shellQuote(cleanQuery)}")
            result = runMagicNet("api conns")
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        result = runMagicNet("api conns")
    }

    val snapshot = result?.takeIf { it.success }?.let { parseConnectionSnapshot(it.output) }
    val matchedConnections =
        snapshot
            ?.connections
            .orEmpty()
            .filter { it.matchesConnectionQuery(query) }
    val closePreview = buildConnectionClosePreview(matchedConnections)
    val visibleConnections = if (query.isBlank()) snapshot?.topConnections.orEmpty() else matchedConnections.take(12)
    val topCloseLimit = topCloseCount.toIntOrNull()
    val topCountValid = topCloseLimit?.let { it in 1..CONNECTION_CLOSE_LIMIT } == true
    val topClosePreview =
        if (topCountValid) {
            buildConnectionClosePreview(snapshot?.connections.orEmpty(), limit = topCloseLimit ?: CONNECTION_CLOSE_LIMIT)
        } else {
            ConnectionClosePreview(0, emptyList())
        }
    val ruleBuckets = ruleConnectionBuckets(snapshot?.connections.orEmpty())
    val chainBuckets = chainConnectionBuckets(snapshot?.connections.orEmpty())
    val output = listOfNotNull(result, action).joinToString("\n\n") { formatToolResult(it) }
    val activeClosePlan =
        when {
            pendingSingleClose != null ->
                ConnectionClosePlanKind.Single to
                    formatConnectionClosePlan(
                        ConnectionClosePlanKind.Single,
                        "",
                        ConnectionClosePreview(totalMatches = 1, candidates = listOf(pendingSingleClose!!)),
                        command = "api close ${shellQuote(pendingSingleClose!!.id)}",
                    )
            confirmCloseMatched && closePreview.candidates.isNotEmpty() ->
                ConnectionClosePlanKind.Matching to
                    formatConnectionClosePlan(
                        ConnectionClosePlanKind.Matching,
                        query,
                        closePreview,
                        command = "api close-matching ${shellQuote(query.trim())}",
                    )
            confirmCloseTop && topClosePreview.candidates.isNotEmpty() ->
                ConnectionClosePlanKind.Top to
                    formatConnectionClosePlan(
                        ConnectionClosePlanKind.Top,
                        "",
                        topClosePreview,
                        command = "api close-top $topCloseCount",
                    )
            else -> null
        }

    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.connections())
                Body(snapshot?.let { t.connectionsSummary(it.count, formatBytes(it.uploadTotal), formatBytes(it.downloadTotal)) } ?: result?.summary ?: t.notRunYet)
            }
            StatusPill(snapshot?.count?.toString() ?: t.idle)
        }
        snapshot?.connections?.takeIf { it.isNotEmpty() }?.let {
            if (ruleBuckets.isNotEmpty() || chainBuckets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ConnectionBucketSection(t.ruleConnectionBuckets(), ruleBuckets, loading) { selected ->
                    query = selected
                    confirmCloseMatched = false
                    pendingSingleClose = null
                    copiedClosePlan = false
                }
                ConnectionBucketSection(t.chainConnectionBuckets(), chainBuckets, loading) { selected ->
                    query = selected
                    confirmCloseMatched = false
                    pendingSingleClose = null
                    copiedClosePlan = false
                }
            }
            Spacer(Modifier.height(8.dp))
            TextInput(
                query,
                t.connectionFilterPlaceholder(),
                {
                    query = it
                    confirmCloseMatched = false
                    pendingSingleClose = null
                    copiedMatches = false
                    copiedClosePlan = false
                },
            )
            Spacer(Modifier.height(8.dp))
            if (query.isNotBlank()) {
                Body(t.matchedConnections(matchedConnections.size))
                if (closePreview.candidates.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Body(t.connectionClosePreview(closePreview.candidates.size, closePreview.totalMatches, formatBytes(closePreview.totalBytes)))
                    if (confirmCloseMatched) {
                        closePreview.candidates.take(3).forEach { candidate ->
                            Mono(candidate.label.take(80))
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleConnections.forEach { target ->
                    val confirmingSingle = pendingSingleClose?.id == target.id
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Mono(target.label.take(80))
                            Body(target.transfer)
                            Body(target.detail.take(120))
                            if (confirmingSingle) Body(t.confirmCloseSingleConnection(formatBytes(target.totalBytes)))
                        }
                        SmallButton(if (confirmingSingle) t.confirm() else t.closeConnection(), enabled = !loading) {
                            if (confirmingSingle) {
                                closeConnection(target)
                            } else {
                                pendingSingleClose = target
                                confirmCloseTop = false
                                confirmCloseMatched = false
                                confirmClose = false
                                copiedClosePlan = false
                            }
                        }
                    }
                    if (confirmingSingle) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.weight(1f)) {
                                pendingSingleClose = null
                                copiedClosePlan = false
                            }
                        }
                    }
                }
                if (visibleConnections.isEmpty()) Body(t.noMatchedConnections())
            }
        }
        action?.let {
            Spacer(Modifier.height(8.dp))
            Body(it.summary)
            if (it.output.isNotBlank() && it.output != it.summary) {
                Spacer(Modifier.height(6.dp))
                Mono(it.output.take(500))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = ::refresh)
        }
        Spacer(Modifier.height(8.dp))
        ConnectionCloseControls(
            loading = loading,
            topCloseCount = topCloseCount,
            topClosePreview = topClosePreview,
            confirmCloseTop = confirmCloseTop,
            query = query,
            matchedConnections = matchedConnections,
            confirmCloseMatched = confirmCloseMatched,
            canCloseAll = (snapshot?.count ?: 0) > 0,
            confirmCloseAll = confirmClose,
            activeClosePlan = activeClosePlan,
            copiedClosePlan = copiedClosePlan,
            onTopCloseCountChange = {
                topCloseCount = it
                confirmCloseTop = false
                pendingSingleClose = null
                copiedClosePlan = false
            },
            onRequestTopClose = {
                confirmCloseTop = true
                pendingSingleClose = null
                copiedClosePlan = false
            },
            onConfirmTopClose = { closeTopConnections(topCloseCount) },
            onRequestMatchedClose = {
                confirmCloseMatched = true
                pendingSingleClose = null
                copiedClosePlan = false
            },
            onConfirmMatchedClose = ::closeMatchedConnections,
            onRequestCloseAll = {
                confirmClose = true
                pendingSingleClose = null
                copiedClosePlan = false
            },
            onConfirmCloseAll = ::closeAll,
            onCopyClosePlan = { kind, plan ->
                copyPlainText(context, closePlanShareTitle(kind), plan)
                copiedClosePlan = true
            },
            onShareClosePlan = { kind, plan ->
                sharePlainText(context, closePlanShareTitle(kind), plan)
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val matchedOutput = formatConnectionMatches(query, matchedConnections)
            SmallButton(
                if (copiedMatches) t.copied() else t.copyMatchedConnections(),
                enabled = query.isNotBlank() && matchedConnections.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                copyPlainText(context, "MagicBox matched connections", matchedOutput)
                copiedMatches = true
            }
            SmallButton(
                t.shareMatchedConnections(),
                enabled = query.isNotBlank() && matchedConnections.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                sharePlainText(context, "MagicBox matched connections", matchedOutput)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox connections", output)
                copied = true
            }
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                sharePlainText(context, "MagicBox connections", output)
            }
        }
    }
}

@Composable
private fun ConnectionBucketSection(
    title: String,
    buckets: List<ConnectionBucket>,
    loading: Boolean,
    onSelect: (String) -> Unit,
) {
    if (buckets.isEmpty()) return
    Body(title)
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        buckets.forEach { bucket ->
            SmallButton(
                "${bucket.name.take(24)} ${bucket.count}/${formatBytes(bucket.bytes)}",
                enabled = !loading,
            ) {
                onSelect(bucket.query)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}
