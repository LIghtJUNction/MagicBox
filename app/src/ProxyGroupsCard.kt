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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

data class ProxyGroupsSnapshot(
    val groups: List<ProxyGroupSummary>,
)

data class ProxyGroupSummary(
    val name: String,
    val type: String,
    val count: Int,
    val now: String,
    val proxies: List<String>,
)

@Composable
fun ProxyGroupsCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<CliResult?>(null) }
    var action by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var copiedDelays by remember { mutableStateOf(false) }
    var testingNode by remember { mutableStateOf<String?>(null) }
    var testingProgressText by remember { mutableStateOf<String?>(null) }
    var batchResult by remember { mutableStateOf<NodeBatchTestResult?>(null) }
    val nodeTests = remember { mutableStateMapOf<String, String>() }

    fun refresh() {
        loading = true
        copied = false
        copiedDelays = false
        testingProgressText = null
        batchResult = null
        nodeTests.clear()
        scope.launch {
            result = runMagicNet("api proxies")
            loading = false
        }
    }

    fun selectProxy(
        group: String,
        node: String,
    ) {
        loading = true
        copied = false
        copiedDelays = false
        testingProgressText = null
        batchResult = null
        scope.launch {
            action = runMagicNet("api select ${shellQuote(group)} ${shellQuote(node)}")
            result = runMagicNet("api proxies")
            loading = false
        }
    }

    fun testNode(node: String) {
        testingNode = node
        testingProgressText = null
        batchResult = null
        scope.launch {
            val test = runMagicNetLong("node test ${shellQuote(node)}")
            nodeTests[node] =
                parseNodeTestAll(test.output)[node]
                    ?: parseNodeTestSummary(test.output).substringAfter("=", missingDelimiterValue = "").ifBlank { test.summary }
            copiedDelays = false
            testingNode = null
            testingProgressText = null
        }
    }

    LaunchedEffect(Unit) {
        result = runMagicNet("api proxies")
    }

    val snapshot = result?.takeIf { it.success }?.let { parseProxyGroupsSnapshot(it.output) }
    val visibleNodes = snapshot?.groups?.take(6)?.flatMap { it.proxies.take(3) }?.distinct().orEmpty()
    val testOutput =
        nodeTests.entries
            .sortedBy { it.key }
            .joinToString("\n") { (node, test) -> "$node=$test" }
    val output = (listOfNotNull(result, action).map { formatToolResult(it) } + listOf(testOutput).filter { it.isNotBlank() }).joinToString("\n\n")
    val batchReport = formatNodeBatchSummary(batchResult)
    val delayReport = listOf(formatNodeDelayReport(nodeTests), batchReport).filter { it.isNotBlank() }.joinToString("\n\n")
    val delayStats = buildNodeDelayStats(nodeTests)

    fun testVisibleNodes() {
        if (visibleNodes.isEmpty() || testingNode != null) return
        scope.launch {
            testingNode = t.testVisibleNodes()
            visibleNodes.forEach { nodeTests.remove(it) }
            testingProgressText = t.nodeTestProgress(buildNodeTestProgress(visibleNodes))
            val test =
                runNodeTestsInBatches(
                    visibleNodes,
                    onBatchComplete = { update ->
                        testingProgressText = t.nodeTestProgress(update.progress)
                        update.tests.forEach { (node, summary) -> nodeTests[node] = summary }
                    },
                )
            batchResult = test
            copiedDelays = false
            testingNode = null
            testingProgressText = null
        }
    }

    fun testGroup(group: ProxyGroupSummary) {
        val candidates = group.proxies
        if (candidates.isEmpty() || testingNode != null) return
        scope.launch {
            testingNode = group.name
            candidates.forEach { nodeTests.remove(it) }
            testingProgressText = t.nodeTestProgress(buildNodeTestProgress(candidates))
            val test =
                runNodeTestsInBatches(
                    candidates,
                    onBatchComplete = { update ->
                        testingProgressText = t.nodeTestProgress(update.progress)
                        update.tests.forEach { (node, summary) -> nodeTests[node] = summary }
                    },
                )
            batchResult = test
            copiedDelays = false
            testingNode = null
            testingProgressText = null
        }
    }

    fun useFastest(group: ProxyGroupSummary) {
        val candidates = group.proxies
        if (candidates.isEmpty() || testingNode != null) return
        loading = true
        copied = false
        scope.launch {
            testingNode = group.name
            candidates.forEach { nodeTests.remove(it) }
            testingProgressText = t.nodeTestProgress(buildNodeTestProgress(candidates))
            val test =
                runNodeTestsInBatches(
                    candidates,
                    onBatchComplete = { update ->
                        testingProgressText = t.nodeTestProgress(update.progress)
                        update.tests.forEach { (node, summary) -> nodeTests[node] = summary }
                    },
                )
            batchResult = test
            val parsed = test.tests
            copiedDelays = false
            val fastest = fastestNode(candidates, parsed)
            action =
                if (fastest == null) {
                    CliResult(false, "node test-all", test.mergedOutput(), t.noFastNode())
                } else {
                    runMagicNet("api select ${shellQuote(group.name)} ${shellQuote(fastest)}")
                }
            result = runMagicNet("api proxies")
            testingNode = null
            testingProgressText = null
            loading = false
        }
    }

    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.proxyGroups())
                Body(snapshot?.let { t.proxyGroupsSummary(it.groups.size) } ?: result?.summary ?: t.notRunYet)
            }
            StatusPill(snapshot?.groups?.size?.toString() ?: t.idle)
        }
        snapshot?.groups?.takeIf { it.isNotEmpty() }?.let { groups ->
            Spacer(Modifier.height(8.dp))
            ProxyGroupRows(
                groups = groups,
                loading = loading,
                testingNode = testingNode,
                testingProgressText = testingProgressText,
                nodeTests = nodeTests,
                onUseFastest = ::useFastest,
                onTestGroup = ::testGroup,
                onTestNode = ::testNode,
                onSelectProxy = ::selectProxy,
            )
        }
        action?.takeIf { it.output.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(500))
        }
        if (nodeTests.isNotEmpty() || batchResult != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficMetricColumn(t.nodeDelayTested(), delayStats.tested.toString(), Modifier.weight(1f))
                TrafficMetricColumn(t.nodeDelayUsable(), delayStats.usable.toString(), Modifier.weight(1f))
                TrafficMetricColumn(t.nodeDelayFastest(), delayStats.fastestDisplay(t.unknown), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficMetricColumn(t.nodeDelayAverage(), delayStats.averageDisplay(t.unknown), Modifier.weight(1f))
                TrafficMetricColumn(t.nodeDelayFailed(), delayStats.failed.toString(), Modifier.weight(1f))
                TrafficMetricColumn(t.nodeDelaySlowest(), delayStats.slowestDisplay(t.unknown), Modifier.weight(1f))
            }
            batchResult?.let { batch ->
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrafficMetricColumn(t.nodeDelayBatches(), batch.batchCount.toString(), Modifier.weight(1f))
                    TrafficMetricColumn(t.nodeDelayBatchOk(), batch.succeededBatches.toString(), Modifier.weight(1f))
                    TrafficMetricColumn(t.nodeDelayBatchFailed(), batch.failedBatches.toString(), Modifier.weight(1f))
                }
                batch.failureSummaries().takeIf { it.isNotEmpty() }?.let { failures ->
                    Spacer(Modifier.height(8.dp))
                    Label(t.nodeBatchFailureReason())
                    failures.forEachIndexed { index, failure ->
                        Body("${index + 1}. $failure")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = ::refresh)
            SmallButton(
                if (testingNode != null) testingProgressText ?: t.testingNode() else t.testVisibleNodes(),
                enabled = !loading && testingNode == null && visibleNodes.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = ::testVisibleNodes,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (copiedDelays) t.copied() else t.copyNodeDelayReport(),
                enabled = delayReport.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                copyPlainText(context, "MagicBox node delays", delayReport)
                copiedDelays = true
            }
            SmallButton(
                t.shareNodeDelayReport(),
                enabled = delayReport.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                sharePlainText(context, "MagicBox node delays", delayReport)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox proxy groups", output)
                copied = true
            }
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                sharePlainText(context, "MagicBox proxy groups", output)
            }
        }
    }
}

fun parseProxyGroupsSnapshot(output: String): ProxyGroupsSnapshot? =
    runCatching {
        val root = JSONObject(output)
        val groups = mutableListOf<ProxyGroupSummary>()
        parseProxyGroupsObject(root.optJSONObject("proxies"), groups)
        parseProxyGroupsObject(root.optJSONObject("providers"), groups)
        if (groups.isEmpty()) parseProxyGroupsObject(root, groups)
        ProxyGroupsSnapshot(groups.distinctBy { it.name }.sortedByDescending { it.count })
    }.getOrNull()

private fun parseProxyGroupsObject(
    source: JSONObject?,
    groups: MutableList<ProxyGroupSummary>,
) {
    if (source == null) return
    val keys = source.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val item = source.optJSONObject(key) ?: continue
        val proxies = item.optJSONArray("proxies")
        val candidates =
            proxies?.let {
                (0 until it.length())
                    .mapNotNull { index -> proxyName(it.opt(index)) }
            }.orEmpty()
        groups +=
            ProxyGroupSummary(
                name = item.optString("name").ifBlank { key },
                type = item.optString("type").ifBlank { "provider" },
                count = proxies?.length() ?: 0,
                now = item.optString("now"),
                proxies = candidates,
            )
    }
}

private fun proxyName(value: Any?): String? =
    when (value) {
        is String -> value
        is JSONObject -> value.optString("name").ifBlank { null }
        else -> null
    }

fun UiText.proxyGroups(): String = if (this === UiText.zh) "代理组状态" else "Proxy groups"

fun UiText.proxyGroupsSummary(count: Int): String =
    if (this === UiText.zh) "$count 个代理组或 provider。" else "$count proxy groups or providers."

fun UiText.testVisibleNodes(): String = if (this === UiText.zh) "测速可见节点" else "Test visible"

fun UiText.useFastNode(): String = if (this === UiText.zh) "使用最快" else "Use fastest"

fun UiText.noFastNode(): String = if (this === UiText.zh) "没有可用测速结果。" else "No usable delay result."

fun UiText.proxyGroupDetail(
    type: String,
    count: Int,
    now: String,
): String =
    if (this === UiText.zh) "$type · $count 个节点 · 当前 $now" else "$type · $count nodes · current $now"
