package com.github.lightjunction.magicbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProxyGroupRows(
    groups: List<ProxyGroupSummary>,
    loading: Boolean,
    testingNode: String?,
    testingProgressText: String?,
    nodeTests: Map<String, String>,
    onUseFastest: (ProxyGroupSummary) -> Unit,
    onTestGroup: (ProxyGroupSummary) -> Unit,
    onTestNode: (String) -> Unit,
    onSelectProxy: (String, String) -> Unit,
) {
    val t = LocalUiText.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groups.take(6).forEach { group ->
            val groupDelayStats = buildNodeDelayStatsForNodes(group.proxies, nodeTests)
            val groupDelaySummary = t.proxyGroupDelaySummary(groupDelayStats, group.proxies.size, t.unknown)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Mono(group.name.take(80))
                        Body(t.proxyGroupDetail(group.type, group.count, group.now.ifBlank { t.unknown }))
                        if (groupDelaySummary.isNotBlank()) {
                            Body(groupDelaySummary)
                        }
                    }
                    StatusPill(group.now.ifBlank { t.unknown }.take(18))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(
                        if (testingNode == group.name) testingProgressText ?: t.testingNode() else t.testGroupNodes(),
                        enabled = !loading && testingNode == null && group.proxies.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        onTestGroup(group)
                    }
                    SmallButton(
                        if (testingNode == group.name) testingProgressText ?: t.testingNode() else t.useFastNode(),
                        enabled = !loading && testingNode == null && group.proxies.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) {
                        onUseFastest(group)
                    }
                }
                group.proxies.take(3).forEach { node ->
                    ProxyNodeRow(
                        node = node,
                        selected = node == group.now,
                        loading = loading,
                        testingNode = testingNode,
                        test = nodeTests[node],
                        onTestNode = onTestNode,
                        onSelect = { onSelectProxy(group.name, node) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyNodeRow(
    node: String,
    selected: Boolean,
    loading: Boolean,
    testingNode: String?,
    test: String?,
    onTestNode: (String) -> Unit,
    onSelect: () -> Unit,
) {
    val t = LocalUiText.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Body(node.take(80))
            test?.let {
                Body(it)
                NodeDelayQualityBadge(nodeDelayQuality(it))
            }
        }
        SmallButton(
            if (testingNode == node) t.testingNode() else t.testNode(),
            enabled = !loading && testingNode == null,
        ) {
            onTestNode(node)
        }
        SmallButton(
            if (selected) t.selectedNode() else t.selectNode(),
            enabled = !loading && !selected,
            onClick = onSelect,
        )
    }
    Spacer(Modifier.height(2.dp))
}

fun UiText.testGroupNodes(): String = if (this === UiText.zh) "测速本组" else "Test group"

@Composable
private fun NodeDelayQualityBadge(quality: NodeDelayQuality) {
    val t = LocalUiText.current
    val background =
        when (quality) {
            NodeDelayQuality.Fast -> MagicPalette.green.copy(alpha = 0.24f)
            NodeDelayQuality.Normal -> MagicPalette.cyan.copy(alpha = 0.2f)
            NodeDelayQuality.Slow -> MagicPalette.orange.copy(alpha = 0.26f)
            NodeDelayQuality.Failed -> MagicPalette.red.copy(alpha = 0.22f)
        }
    val foreground =
        when (quality) {
            NodeDelayQuality.Fast -> MagicPalette.green
            NodeDelayQuality.Normal -> MagicPalette.cyan
            NodeDelayQuality.Slow -> MagicPalette.orange
            NodeDelayQuality.Failed -> MagicPalette.red
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(background)
                .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        BasicText(
            text = t.nodeDelayQualityLabel(quality),
            style = TextStyle(color = foreground, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
}
