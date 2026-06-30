package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthIssueSummaryTest {
    @Test
    fun buildHealthIssueSummaryKeepsOnlyWarningsAndErrors() {
        val entries =
            listOf(
                HealthEntry(HealthSeverity.Ok, "Core", "running"),
                HealthEntry(HealthSeverity.Warn, "DNS", "fallback active"),
                HealthEntry(HealthSeverity.Error, "TUN", "missing interface"),
            )

        val summary = buildHealthIssueSummary(entries)

        assertTrue(summary.hasIssues)
        assertEquals(1, summary.warningCount)
        assertEquals(1, summary.errorCount)
        assertEquals(listOf("TUN", "DNS"), summary.issues.map { it.title })
    }

    @Test
    fun buildHealthIssueSummaryReportsNoIssuesForOkEntries() {
        val summary = buildHealthIssueSummary(listOf(HealthEntry(HealthSeverity.Ok, "Core", "running")))

        assertFalse(summary.hasIssues)
        assertEquals(0, summary.warningCount)
        assertEquals(0, summary.errorCount)
    }

    @Test
    fun formatHealthIssueSummaryRedactsSensitiveDetails() {
        val summary =
            buildHealthIssueSummary(
                listOf(
                    HealthEntry(
                        HealthSeverity.Error,
                        "Subscription",
                        "url=https://example.invalid/sub?token=abc password=secret",
                    ),
                ),
            )

        val report = formatHealthIssueSummary(summary)

        assertTrue("Subscription" in report)
        assertTrue("<redacted-url>" in report)
        assertFalse("token=abc" in report)
        assertFalse("password=secret" in report)
    }

    @Test
    fun formatHealthIssueSummaryReportsNoIssues() {
        val report = formatHealthIssueSummary(buildHealthIssueSummary(emptyList()))

        assertTrue("issues: none" in report)
    }

    @Test
    fun parseHealthEntriesIgnoresUnmatchedLines() {
        val entries =
            parseHealthEntries(
                """
                [OK] Core: running
                noisy line
                [warn] DNS: fallback active
                [error] TUN
                """.trimIndent(),
            )

        assertEquals(3, entries.size)
        assertEquals(HealthSeverity.Ok, entries[0].severity)
        assertEquals("Core", entries[0].title)
        assertEquals("running", entries[0].details)
        assertEquals(HealthSeverity.Warn, entries[1].severity)
        assertEquals(HealthSeverity.Error, entries[2].severity)
        assertEquals("TUN", entries[2].title)
    }

    @Test
    fun prioritizeHealthEntriesShowsErrorsAndWarningsFirstWithoutReorderingPeers() {
        val entries =
            listOf(
                HealthEntry(HealthSeverity.Ok, "Core", "running"),
                HealthEntry(HealthSeverity.Warn, "DNS", "fallback active"),
                HealthEntry(HealthSeverity.Ok, "Proxy", "ready"),
                HealthEntry(HealthSeverity.Error, "TUN", "missing"),
                HealthEntry(HealthSeverity.Warn, "WARP", "disabled"),
            )

        val prioritized = prioritizeHealthEntries(entries)

        assertEquals(listOf("TUN", "DNS", "WARP", "Core", "Proxy"), prioritized.map { it.title })
    }

    @Test
    fun buildHealthIssueSummaryUsesTheSamePriorityOrderAsThePanel() {
        val entries =
            listOf(
                HealthEntry(HealthSeverity.Warn, "DNS", "fallback active"),
                HealthEntry(HealthSeverity.Error, "TUN", "missing"),
                HealthEntry(HealthSeverity.Warn, "WARP", "disabled"),
            )

        val summary = buildHealthIssueSummary(entries)

        assertEquals(listOf("TUN", "DNS", "WARP"), summary.issues.map { it.title })
    }

    @Test
    fun filterHealthEntriesCanFocusOnlyIssues() {
        val entries =
            listOf(
                HealthEntry(HealthSeverity.Error, "TUN", "missing"),
                HealthEntry(HealthSeverity.Warn, "DNS", "fallback active"),
                HealthEntry(HealthSeverity.Ok, "Core", "running"),
            )

        assertEquals(listOf("TUN", "DNS"), filterHealthEntries(entries, HealthEntryFilter.Issues).map { it.title })
        assertEquals(listOf("TUN", "DNS", "Core"), filterHealthEntries(entries, HealthEntryFilter.All).map { it.title })
    }

    @Test
    fun buildHealthSeverityCountsCountsEverySeverity() {
        val counts =
            buildHealthSeverityCounts(
                listOf(
                    HealthEntry(HealthSeverity.Ok, "Core", "running"),
                    HealthEntry(HealthSeverity.Ok, "Proxy", "ready"),
                    HealthEntry(HealthSeverity.Warn, "DNS", "fallback active"),
                    HealthEntry(HealthSeverity.Error, "TUN", "missing"),
                ),
            )

        assertEquals(2, counts.ok)
        assertEquals(1, counts.warn)
        assertEquals(1, counts.error)
        assertEquals(4, counts.total)
    }
}
