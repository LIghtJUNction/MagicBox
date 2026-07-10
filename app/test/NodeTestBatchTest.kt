package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class NodeTestBatchTest {
    @Test
    fun buildNodeTestAllArgsDeduplicatesAndChunksNodes() {
        val args =
            buildNodeTestAllArgs(
                nodes = listOf("a", "b", "a", "", "c", "d'e"),
                batchSize = 2,
            )

        assertEquals(2, args.size)
        assertEquals("node test-all 'a' 'b'", args[0])
        assertEquals("node test-all 'c' 'd'\"'\"'e'", args[1])
    }

    @Test
    fun buildNodeTestAllArgsHandlesEmptyInput() {
        assertEquals(emptyList<String>(), buildNodeTestAllArgs(emptyList()))
    }

    @Test
    fun buildNodeTestAllArgsClampsBatchSize() {
        val args = buildNodeTestAllArgs(listOf("a", "b"), batchSize = 0)

        assertEquals(listOf("node test-all 'a'", "node test-all 'b'"), args)
    }

    @Test
    fun buildNodeTestBatchesKeepsRequestedNodesWithCommand() {
        val batches = buildNodeTestBatches(listOf("a", "b", "c"), batchSize = 2)

        assertEquals(listOf("a", "b"), batches[0].nodes)
        assertEquals("node test-all 'a' 'b'", batches[0].args)
        assertEquals(listOf("c"), batches[1].nodes)
        assertEquals("node test-all 'c'", batches[1].args)
    }

    @Test
    fun buildNodeTestProgressUsesDeduplicatedDefaultBatches() {
        val progress =
            buildNodeTestProgress(
                (1..17).map { "node-$it" } + listOf("node-1", ""),
            )

        assertEquals(17, progress.nodeCount)
        assertEquals(2, progress.batchCount)
        assertEquals(0, progress.completedBatchCount)
    }

    @Test
    fun nodeTestProgressFormatsLocalizedText() {
        val progress = NodeTestProgress(nodeCount = 32, batchCount = 2, completedBatchCount = 1)

        assertEquals("测试中 32 节点 / 1/2 批", UiText.zh.nodeTestProgress(progress))
        assertEquals("Testing 32 nodes / 1/2 batches", UiText.en.nodeTestProgress(progress))
    }

    @Test
    fun nodeBatchFailureReasonFormatsLocalizedText() {
        assertEquals("批次失败原因", UiText.zh.nodeBatchFailureReason())
        assertEquals("Batch failure", UiText.en.nodeBatchFailureReason())
    }

    @Test
    fun mergeNodeTestResultsKeepsLatestParsedResult() {
        val merged =
            mergeNodeTestResults(
                listOf(
                    CliResult(true, "node test-all 'a'", "a=80ms\nnoise\nb=timeout"),
                    CliResult(true, "node test-all 'a'", "a=40ms\nc=120ms"),
                ),
            )

        assertEquals(mapOf("a" to "40ms", "b" to "timeout", "c" to "120ms"), merged)
    }

    @Test
    fun parseNodeTestBatchResultIgnoresUnrequestedMetadata() {
        val parsed =
            parseNodeTestBatchResult(
                NodeTestBatchCommand(nodes = listOf("node-a", "node-b"), args = "node test-all 'node-a' 'node-b'"),
                CliResult(
                    true,
                    "node test-all",
                    "started=true\nnode-a=40ms\nprovider=proxy\nnode-b=timeout",
                ),
            )

        assertEquals(mapOf("node-a" to "40ms", "node-b" to "timeout"), parsed)
    }

    @Test
    fun nodeTestBatchUpdateCarriesProgressAndBatchResults() {
        val update =
            NodeTestBatchUpdate(
                progress = NodeTestProgress(nodeCount = 4, batchCount = 2, completedBatchCount = 1),
                tests = mapOf("node-a" to "40ms"),
            )

        assertEquals(1, update.progress.completedBatchCount)
        assertEquals(mapOf("node-a" to "40ms"), update.tests)
    }

    @Test
    fun runNodeTestsInBatchesReportsEachBatchInOrder() =
        runBlocking {
            val commands = mutableListOf<String>()
            val events = mutableListOf<String>()
            val updates = mutableListOf<NodeTestBatchUpdate>()

            val result =
                runNodeTestsInBatches(
                    nodes = listOf("a", "b", "c"),
                    batchSize = 2,
                    runner = { args ->
                        commands += args
                        CliResult(true, args, if ("'c'" in args) "c=30ms" else "a=80ms\nb=timeout\nmeta=value")
                    },
                    onProgress = { events += "progress:${it.completedBatchCount}" },
                    onBatchComplete = { updates += it },
                )

            assertEquals(listOf("node test-all 'a' 'b'", "node test-all 'c'"), commands)
            assertEquals(listOf("progress:1", "progress:2"), events)
            assertEquals(listOf(1, 2), updates.map { it.progress.completedBatchCount })
            assertEquals(mapOf("a" to "80ms", "b" to "timeout"), updates[0].tests)
            assertEquals(mapOf("c" to "30ms"), updates[1].tests)
            assertEquals(mapOf("a" to "80ms", "b" to "timeout", "c" to "30ms"), result.tests)
        }

    @Test
    fun nodeBatchTestResultMergesNonBlankOutputs() {
        val result =
            NodeBatchTestResult(
                tests = emptyMap(),
                results =
                    listOf(
                        CliResult(true, "a", "a=80ms"),
                        CliResult(false, "b", ""),
                        CliResult(false, "c", "c=timeout"),
                    ),
            )

        assertTrue("a=80ms" in result.mergedOutput())
        assertTrue("c=timeout" in result.mergedOutput())
    }

    @Test
    fun nodeBatchTestResultCountsSuccessfulAndFailedBatches() {
        val result =
            NodeBatchTestResult(
                tests = emptyMap(),
                results =
                    listOf(
                        CliResult(true, "a", "a=80ms"),
                        CliResult(false, "b", "batch failed"),
                        CliResult(true, "c", "c=40ms"),
                    ),
            )

        assertEquals(3, result.batchCount)
        assertEquals(2, result.succeededBatches)
        assertEquals(1, result.failedBatches)
        assertTrue("batch_success: 2" in formatNodeBatchSummary(result))
        assertTrue("batch_failed: 1" in formatNodeBatchSummary(result))
    }

    @Test
    fun formatNodeBatchSummaryIncludesRedactedFailureDetails() {
        val result =
            NodeBatchTestResult(
                tests = emptyMap(),
                results =
                    listOf(
                        CliResult(
                            false,
                            "node test-all 'https://example.invalid/node?token=abc'",
                            "failed url=https://example.invalid/delay?password=secret",
                        ),
                    ),
            )
        val summary = formatNodeBatchSummary(result)

        assertTrue("batch_failure_1_command:" in summary)
        assertTrue("batch_failure_1_summary:" in summary)
        assertTrue("batch_failure_1_output:" in summary)
        assertTrue("<redacted-url>" in summary)
        assertFalse("token=abc" in summary)
        assertFalse("password=secret" in summary)
    }

    @Test
    fun firstFailureSummaryReturnsRedactedFirstFailure() {
        val result =
            NodeBatchTestResult(
                tests = emptyMap(),
                results =
                    listOf(
                        CliResult(false, "a", "url=https://example.invalid/delay?token=abc"),
                        CliResult(false, "b", "second failure"),
                    ),
            )

        assertEquals("url=<redacted-url>", result.firstFailureSummary())
    }

    @Test
    fun failureSummariesReturnTopRedactedFailures() {
        val result =
            NodeBatchTestResult(
                tests = emptyMap(),
                results =
                    listOf(
                        CliResult(false, "a", "url=https://example.invalid/delay?token=abc"),
                        CliResult(false, "b", "second failure"),
                        CliResult(false, "c", "third failure"),
                        CliResult(false, "d", "fourth failure"),
                    ),
            )

        val summaries = result.failureSummaries()

        assertEquals(3, summaries.size)
        assertEquals("url=<redacted-url>", summaries[0])
        assertEquals("second failure", summaries[1])
        assertEquals("third failure", summaries[2])
        assertFalse(summaries.any { "token=abc" in it })
    }

    @Test
    fun formatNodeBatchSummaryLimitsFailureDetails() {
        val result =
            NodeBatchTestResult(
                tests = emptyMap(),
                results = (1..4).map { index -> CliResult(false, "batch-$index", "failed-$index") },
            )
        val summary = formatNodeBatchSummary(result)

        assertTrue("batch_failure_3_output:" in summary)
        assertFalse("batch_failure_4_output:" in summary)
    }

    @Test
    fun formatNodeBatchSummaryHandlesMissingResult() {
        assertEquals("", formatNodeBatchSummary(null))
    }
}
