package com.github.lightjunction.magicbox

fun UiText.currentNode(): String = if (this === UiText.zh) "当前节点" else "Current node"

fun UiText.selectNode(): String = if (this === UiText.zh) "使用" else "Use"

fun UiText.selectedNode(): String = if (this === UiText.zh) "已选" else "Selected"

fun UiText.testNode(): String = if (this === UiText.zh) "测速" else "Test"

fun UiText.testingNode(): String = if (this === UiText.zh) "测试中" else "Testing"

fun UiText.nodeTestProgress(progress: NodeTestProgress): String =
    if (this === UiText.zh) {
        "测试中 ${progress.nodeCount} 节点 / ${progress.completedBatchCount}/${progress.batchCount} 批"
    } else {
        "Testing ${progress.nodeCount} nodes / ${progress.completedBatchCount}/${progress.batchCount} batches"
    }

fun UiText.nodeBatchFailureReason(): String =
    if (this === UiText.zh) "批次失败原因" else "Batch failure"

fun UiText.nodeCurrentFailed(): String =
    if (this === UiText.zh) "未读取到当前节点。" else "Current node was not reported."
