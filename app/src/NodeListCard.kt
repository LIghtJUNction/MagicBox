package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun NodeListCard(
    result: CliResult?,
    currentResult: CliResult?,
    loading: Boolean,
    copied: Boolean,
    onReload: () -> Unit,
    onUseNode: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    val scope = rememberCoroutineScope()
    val nodeTests = remember { mutableStateMapOf<String, CliResult>() }
    var testingNode by remember { mutableStateOf<String?>(null) }
    val nodes = result?.takeIf { it.success }?.let { parseNodeList(it.output) }.orEmpty()
    val current = currentResult?.takeIf { it.success }?.let { parseCurrentNode(it.output) }.orEmpty()
    fun testNode(node: String) {
        testingNode = node
        scope.launch {
            nodeTests[node] = runMagicNetLong("node test ${shellQuote(node)}")
            testingNode = null
        }
    }

    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.nodes())
                Body(nodeListSummary(t, result, nodes.size))
            }
            StatusPill(if (nodes.isNotEmpty()) "${nodes.size}" else t.idle)
        }
        if (nodes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Body("${t.currentNode()}: ${current.ifBlank { t.nodeCurrentFailed() }}")
            Spacer(Modifier.height(8.dp))
            nodes.take(8).forEach { node ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Mono(node.take(120))
                        nodeTests[node]?.let { test ->
                            Body(parseNodeTestSummary(test.output).ifBlank { test.summary })
                            if (!test.success && test.output.isNotBlank()) {
                                Mono(redactSupportText(test.output).take(240))
                            }
                        }
                    }
                    SmallButton(
                        if (testingNode == node) t.testingNode() else t.testNode(),
                        enabled = !loading && testingNode == null,
                        onClick = { testNode(node) },
                    )
                    SmallButton(
                        if (node == current) t.selectedNode() else t.selectNode(),
                        enabled = !loading && node != current,
                        onClick = { onUseNode(node) },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            if (nodes.size > 8) {
                Spacer(Modifier.height(4.dp))
                Body(t.more(nodes.size - 8))
            }
        } else if (result?.success == false && result.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(result.output.take(700))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
            SmallButton(
                if (copied) t.copied() else t.copyReport(),
                enabled = !loading && nodes.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = onCopy,
            )
            SmallButton(t.shareReport(), enabled = !loading && nodes.isNotEmpty(), modifier = Modifier.weight(1f), onClick = onShare)
        }
    }
}

private fun nodeListSummary(
    t: UiText,
    result: CliResult?,
    count: Int,
): String =
    when {
        result == null -> t.loadingNodes()
        !result.success -> result.summary
        count == 0 -> t.noNodes()
        else -> t.nodeCount(count)
    }
