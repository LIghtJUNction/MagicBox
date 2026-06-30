package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeDelayReportTest {
    @Test
    fun buildNodeDelayEntriesSortsFastestFirstAndUnknownLast() {
        val entries =
            buildNodeDelayEntries(
                mapOf(
                    "node-c" to "timeout",
                    "node-a" to "120ms",
                    "node-b" to "32ms",
                ),
            )

        assertEquals(listOf("node-b", "node-a", "node-c"), entries.map { it.node })
        assertEquals(32, entries[0].delayMillis)
        assertEquals(null, entries[2].delayMillis)
    }

    @Test
    fun buildNodeDelayStatsCountsUsableNodesAndFastest() {
        val stats =
            buildNodeDelayStats(
                mapOf(
                    "node-a" to "120ms",
                    "node-b" to "timeout",
                    "node-c" to "45ms",
                ),
            )

        assertEquals(3, stats.tested)
        assertEquals(2, stats.usable)
        assertEquals("node-c", stats.fastest?.node)
        assertEquals(45, stats.fastest?.delayMillis)
        assertEquals(1, stats.fastestCount)
        assertEquals(82, stats.averageMillis)
        assertEquals("node-a", stats.slowest?.node)
        assertEquals(1, stats.failed)
        assertEquals(2, stats.fast)
        assertEquals(0, stats.normal)
        assertEquals(0, stats.slow)
    }

    @Test
    fun buildNodeDelayStatsHandlesEmptyAndAllUnusableResults() {
        val empty = buildNodeDelayStats(emptyMap())
        val unusable =
            buildNodeDelayStats(
                mapOf(
                    "node-a" to "timeout",
                    "node-b" to "failed",
                ),
            )

        assertEquals(0, empty.tested)
        assertEquals(0, empty.usable)
        assertEquals(null, empty.fastest)
        assertEquals(0, empty.fastestCount)
        assertEquals(null, empty.averageMillis)
        assertEquals(null, empty.slowest)
        assertEquals(0, empty.failed)
        assertEquals(0, empty.fast)
        assertEquals(0, empty.normal)
        assertEquals(0, empty.slow)
        assertEquals(2, unusable.tested)
        assertEquals(0, unusable.usable)
        assertEquals(null, unusable.fastest)
        assertEquals(0, unusable.fastestCount)
        assertEquals(null, unusable.averageMillis)
        assertEquals(null, unusable.slowest)
        assertEquals(2, unusable.failed)
        assertEquals(0, unusable.fast)
        assertEquals(0, unusable.normal)
        assertEquals(0, unusable.slow)
    }

    @Test
    fun buildNodeDelayStatsCountsFastestTies() {
        val stats =
            buildNodeDelayStats(
                mapOf(
                    "node-a" to "30ms",
                    "node-b" to "30ms",
                    "node-c" to "52ms",
                ),
            )

        assertEquals("node-a", stats.fastest?.node)
        assertEquals(2, stats.fastestCount)
        assertTrue("+1" in stats.fastestDisplay("unknown"))
        assertEquals("52ms", stats.slowestDisplay("unknown").substringAfterLast(" "))
    }

    @Test
    fun buildNodeDelayStatsForNodesFiltersOtherGroups() {
        val stats =
            buildNodeDelayStatsForNodes(
                nodes = listOf("group-a-fast", "group-a-timeout", "group-a-untested"),
                tests =
                    mapOf(
                        "group-a-fast" to "30ms",
                        "group-a-timeout" to "timeout",
                        "group-b-fast" to "12ms",
                    ),
            )

        assertEquals(2, stats.tested)
        assertEquals(1, stats.usable)
        assertEquals(1, stats.failed)
        assertEquals("group-a-fast", stats.fastest?.node)
    }

    @Test
    fun nodeDelayQualityClassifiesBoundaries() {
        assertEquals(NodeDelayQuality.Fast, nodeDelayQuality("120ms"))
        assertEquals(NodeDelayQuality.Normal, nodeDelayQuality("121ms"))
        assertEquals(NodeDelayQuality.Normal, nodeDelayQuality("250ms"))
        assertEquals(NodeDelayQuality.Slow, nodeDelayQuality("251ms"))
        assertEquals(NodeDelayQuality.Failed, nodeDelayQuality("timeout"))
    }

    @Test
    fun proxyGroupDelaySummaryUsesGroupCountsOnly() {
        val stats =
            buildNodeDelayStats(
                mapOf(
                    "node-a" to "30ms",
                    "node-c" to "260ms",
                    "node-b" to "timeout",
                ),
            )

        assertEquals(
            "本组已测 3/4 · 可用 2 · 快 1 · 慢 1 · 最快 30ms · 平均 145ms",
            UiText.zh.proxyGroupDelaySummary(stats, total = 4, unknown = "未知"),
        )
        assertEquals("", UiText.en.proxyGroupDelaySummary(buildNodeDelayStats(emptyMap()), total = 4, unknown = "Unknown"))
    }

    @Test
    fun formatNodeDelayReportIncludesFastestAndRedactsSecrets() {
        val report =
            formatNodeDelayReport(
                mapOf(
                    "https://example.invalid/node?token=abc" to "url=https://example.invalid/delay?password=secret 18ms",
                    "plain-node" to "65ms",
                ),
            )

        assertTrue("fastest:" in report)
        assertTrue("fastest_count: 1" in report)
        assertTrue("usable: 2" in report)
        assertTrue("failed: 0" in report)
        assertTrue("average_ms: 41" in report)
        assertTrue("fast: 2" in report)
        assertTrue("normal: 0" in report)
        assertTrue("slow: 0" in report)
        assertTrue("slowest:" in report)
        assertTrue("<redacted-url>" in report)
        assertFalse("token=abc" in report)
        assertFalse("password=secret" in report)
        assertTrue("plain-node" in report)
    }

    @Test
    fun parseNodeDelayMillisAcceptsSpacedAndDecoratedDelayValues() {
        assertEquals(120, parseNodeDelayMillis("120 ms"))
        assertEquals(95, parseNodeDelayMillis("delay=95ms jitter=4ms"))
        assertEquals(null, parseNodeDelayMillis("timeout"))
    }

    @Test
    fun nodeDelayEntryDisplayLabelRedactsNodeAndSummary() {
        val label =
            NodeDelayEntry(
                node = "https://example.invalid/node?token=abc",
                summary = "url=https://example.invalid/delay?password=secret 18ms",
                delayMillis = 18,
            ).displayLabel()

        assertTrue("<redacted-url>" in label)
        assertFalse("token=abc" in label)
        assertFalse("password=secret" in label)
    }
}
