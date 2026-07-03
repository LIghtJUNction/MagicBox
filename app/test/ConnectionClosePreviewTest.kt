package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionClosePreviewTest {
    @Test
    fun buildConnectionClosePreviewSortsByTrafficAndCapsAtEight() {
        val matches = (1..10).map { index -> connection("id-$index", totalBytes = index.toLong()) }

        val preview = buildConnectionClosePreview(matches)

        assertEquals(10, preview.totalMatches)
        assertEquals(8, preview.candidates.size)
        assertEquals("id-10", preview.candidates.first().id)
        assertEquals("id-3", preview.candidates.last().id)
        assertEquals(52L, preview.totalBytes)
        assertTrue(preview.truncated)
    }

    @Test
    fun buildConnectionClosePreviewUsesRequestedSafeLimit() {
        val matches = listOf(connection("small", 1), connection("big", 9), connection("mid", 4))

        val preview = buildConnectionClosePreview(matches, limit = 2)

        assertEquals(listOf("big", "mid"), preview.candidates.map { it.id })
        assertEquals(13L, preview.totalBytes)
        assertTrue(preview.truncated)
    }

    @Test
    fun buildConnectionClosePreviewClampsInvalidLimit() {
        val matches = listOf(connection("a", 2), connection("b", 1))

        val preview = buildConnectionClosePreview(matches, limit = 0)

        assertEquals(listOf("a"), preview.candidates.map { it.id })
        assertEquals(1, preview.candidates.size)
        assertTrue(preview.truncated)
    }

    @Test
    fun buildConnectionClosePreviewMarksCompletePreview() {
        val matches = listOf(connection("a", 2), connection("b", 1))

        val preview = buildConnectionClosePreview(matches)

        assertEquals(2, preview.candidates.size)
        assertFalse(preview.truncated)
    }

    @Test
    fun connectionTopClosePreviewFormatsLocalizedText() {
        assertEquals(
            "将关闭当前流量最高的 3 条连接，当前合计 9 KB。",
            UiText.zh.connectionTopClosePreview(3, "9 KB"),
        )
        assertEquals(
            "Will close the current top 3 connections by traffic, totaling 9 KB.",
            UiText.en.connectionTopClosePreview(3, "9 KB"),
        )
    }

    @Test
    fun formatConnectionClosePlanIncludesPreviewDetails() {
        val preview =
            ConnectionClosePreview(
                totalMatches = 4,
                candidates = listOf(connection("video.example", 4096), connection("chat.example", 1024)),
            )

        val report = formatConnectionClosePlan(ConnectionClosePlanKind.Top, "", preview)

        assertTrue("MagicBox connection close plan: top" in report)
        assertTrue("command: api close-top 2" in report)
        assertTrue("matches: 4" in report)
        assertTrue("closing: 2" in report)
        assertTrue("truncated: true" in report)
        assertTrue("total_bytes: 5.0 KB" in report)
        assertTrue("video.example" in report)
        assertTrue("chat.example" in report)
    }

    @Test
    fun formatConnectionClosePlanSupportsSingleConnection() {
        val preview =
            ConnectionClosePreview(
                totalMatches = 1,
                candidates = listOf(connection("tcp://single.example", 4096)),
            )

        val report = formatConnectionClosePlan(ConnectionClosePlanKind.Single, "", preview)

        assertTrue("MagicBox connection close plan: single" in report)
        assertTrue("command: api close 'tcp://single.example'" in report)
        assertTrue("matches: 1" in report)
        assertTrue("closing: 1" in report)
        assertTrue("tcp://single.example" in report)
        assertEquals("MagicBox single connection close plan", closePlanShareTitle(ConnectionClosePlanKind.Single))
    }

    @Test
    fun formatConnectionClosePlanRedactsSensitiveValues() {
        val target =
            connection(
                id = "https://example.invalid/video?token=abc",
                totalBytes = 2048,
            ).copy(detail = "RuleSet media password=secret")
        val preview = ConnectionClosePreview(totalMatches = 1, candidates = listOf(target))

        val report =
            formatConnectionClosePlan(
                ConnectionClosePlanKind.Matching,
                "https://example.invalid/search?token=abc",
                preview,
                command = "api close-matching 'https://example.invalid/search?token=abc'",
        )

        assertTrue("MagicBox connection close plan: matching" in report)
        assertTrue("command: api close-matching '" in report)
        assertTrue("query: <redacted-url>" in report)
        assertTrue("<redacted-url>" in report)
        assertFalse("token=abc" in report)
        assertFalse("password=secret" in report)
    }

    @Test
    fun singleConnectionCloseTextFormatsLocalizedText() {
        assertEquals("将关闭这条连接，当前累计流量 4 KB。", UiText.zh.confirmCloseSingleConnection("4 KB"))
        assertEquals("Will close this connection, currently totaling 4 KB.", UiText.en.confirmCloseSingleConnection("4 KB"))
    }

    private fun connection(
        id: String,
        totalBytes: Long,
    ): ConnectionTarget =
        ConnectionTarget(
            id = id,
            label = id,
            network = "tcp",
            rule = "RuleSet",
            rulePayload = "media",
            chain = "proxy > jp",
            detail = "tcp · RuleSet · media · proxy > jp",
            transfer = "$totalBytes B up / 0 B down",
            totalBytes = totalBytes,
        )
}
