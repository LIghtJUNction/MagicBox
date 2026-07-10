package com.github.lightjunction.magicbox

private const val NODE_TEST_BATCH_SIZE = 16

data class NodeTestBatchCommand(
    val nodes: List<String>,
    val args: String,
)

data class NodeBatchTestResult(
    val tests: Map<String, String>,
    val results: List<CliResult>,
) {
    val batchCount: Int = results.size
    val succeededBatches: Int = results.count { it.success }
    val failedBatches: Int = results.count { !it.success }
    val failedResults: List<CliResult> = results.filter { !it.success }

    fun mergedOutput(): String =
        results
            .map { it.output.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
}

fun formatNodeBatchSummary(result: NodeBatchTestResult?): String {
    if (result == null) return ""
    return buildString {
        appendLine("batches: ${result.batchCount}")
        appendLine("batch_success: ${result.succeededBatches}")
        appendLine("batch_failed: ${result.failedBatches}")
        result.failedResults.take(3).forEachIndexed { index, failed ->
            appendLine("batch_failure_${index + 1}_command: ${redactSupportText(failed.command)}")
            appendLine("batch_failure_${index + 1}_summary: ${redactSupportText(failed.summary)}")
            val output = redactSupportText(failed.output).lineSequence().take(3).joinToString(" ").take(240)
            appendLine("batch_failure_${index + 1}_output: ${output.ifBlank { "(empty)" }}")
        }
    }.trim()
}

fun NodeBatchTestResult.firstFailureSummary(): String =
    failureSummaries(limit = 1).firstOrNull().orEmpty()

fun NodeBatchTestResult.failureSummaries(limit: Int = 3): List<String> =
    failedResults
        .take(limit.coerceAtLeast(0))
        .mapNotNull { result ->
            val summary = result.summary.ifBlank { result.output }
            redactSupportText(summary)
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.take(160)
        }

fun UiText.nodeDelayBatches(): String = if (this === UiText.zh) "批次" else "Batches"

fun UiText.nodeDelayBatchOk(): String = if (this === UiText.zh) "批次成功" else "Batch OK"

fun UiText.nodeDelayBatchFailed(): String = if (this === UiText.zh) "批次失败" else "Batch failed"

data class NodeTestProgress(
    val nodeCount: Int,
    val batchCount: Int,
    val completedBatchCount: Int = 0,
)

data class NodeTestBatchUpdate(
    val progress: NodeTestProgress,
    val tests: Map<String, String>,
)

fun buildNodeTestAllArgs(
    nodes: List<String>,
    batchSize: Int = NODE_TEST_BATCH_SIZE,
): List<String> = buildNodeTestBatches(nodes, batchSize).map { it.args }

fun buildNodeTestBatches(
    nodes: List<String>,
    batchSize: Int = NODE_TEST_BATCH_SIZE,
): List<NodeTestBatchCommand> {
    val size = batchSize.coerceAtLeast(1)
    return nodes
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .chunked(size)
        .map { batch ->
            NodeTestBatchCommand(
                nodes = batch,
                args = "node test-all ${batch.joinToString(" ") { shellQuote(it) }}",
            )
        }
}

fun buildNodeTestProgress(
    nodes: List<String>,
    batchSize: Int = NODE_TEST_BATCH_SIZE,
): NodeTestProgress =
    buildNodeTestBatches(nodes, batchSize).let { batches ->
        NodeTestProgress(
            nodeCount = batches.sumOf { it.nodes.size },
            batchCount = batches.size,
        )
    }

fun mergeNodeTestResults(results: List<CliResult>): Map<String, String> =
    results
        .flatMap { parseNodeTestAll(it.output).entries }
        .associate { it.key to it.value }

fun parseNodeTestBatchResult(
    batch: NodeTestBatchCommand,
    result: CliResult,
): Map<String, String> {
    val requested = batch.nodes.toSet()
    return parseNodeTestAll(result.output)
        .filterKeys { it in requested }
}

suspend fun runNodeTestsInBatches(
    nodes: List<String>,
    batchSize: Int = NODE_TEST_BATCH_SIZE,
    runner: suspend (String) -> CliResult = ::runMagicNetLong,
    onProgress: ((NodeTestProgress) -> Unit)? = null,
    onBatchComplete: ((NodeTestBatchUpdate) -> Unit)? = null,
): NodeBatchTestResult {
    val results = mutableListOf<CliResult>()
    val tests = mutableMapOf<String, String>()
    val batches = buildNodeTestBatches(nodes, batchSize)
    val nodeCount = batches.sumOf { it.nodes.size }
    batches.forEachIndexed { index, batch ->
        val result = runner(batch.args)
        val batchTests = parseNodeTestBatchResult(batch, result)
        results += result
        tests += batchTests
        val progress =
            NodeTestProgress(
                nodeCount = nodeCount,
                batchCount = batches.size,
                completedBatchCount = index + 1,
            )
        onProgress?.invoke(progress)
        onBatchComplete?.invoke(NodeTestBatchUpdate(progress = progress, tests = batchTests))
    }
    return NodeBatchTestResult(
        tests = tests,
        results = results,
    )
}
