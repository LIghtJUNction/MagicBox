package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficInsightTest {
    @Test
    fun buildTrafficInsightUsesSensitiveThresholdForSustainedTraffic() {
        val samples =
            listOf(
                LiveStats(up = 160 * 1024f, down = 160 * 1024f, timestampMillis = 1_000L),
                LiveStats(up = 170 * 1024f, down = 170 * 1024f, timestampMillis = 6_000L),
            )

        val insight =
            buildTrafficInsight(
                samples = samples,
                sampleInterval = TrafficSampleInterval.Balanced,
                alertThreshold = TrafficAlertThreshold.Sensitive,
                nowMillis = 7_000L,
            )

        assertEquals(TrafficInsightKind.SustainedHigh, insight.kind)
        assertEquals(TrafficInsightLevel.Warning, insight.level)
    }

    @Test
    fun buildTrafficInsightKeepsSameTrafficStableWithHeavyThreshold() {
        val samples =
            listOf(
                LiveStats(up = 160 * 1024f, down = 160 * 1024f, timestampMillis = 1_000L),
                LiveStats(up = 170 * 1024f, down = 170 * 1024f, timestampMillis = 6_000L),
            )

        val insight =
            buildTrafficInsight(
                samples = samples,
                sampleInterval = TrafficSampleInterval.Balanced,
                alertThreshold = TrafficAlertThreshold.Heavy,
                nowMillis = 7_000L,
            )

        assertEquals(TrafficInsightKind.Stable, insight.kind)
        assertEquals(TrafficInsightLevel.Ok, insight.level)
    }

    @Test
    fun buildTrafficInsightDetectsStaleSamplesBeforeRateWarnings() {
        val samples =
            listOf(
                LiveStats(up = 2 * 1024 * 1024f, down = 2 * 1024 * 1024f, timestampMillis = 1_000L),
                LiveStats(up = 2 * 1024 * 1024f, down = 2 * 1024 * 1024f, timestampMillis = 6_000L),
            )

        val insight =
            buildTrafficInsight(
                samples = samples,
                sampleInterval = TrafficSampleInterval.Balanced,
                alertThreshold = TrafficAlertThreshold.Sensitive,
                nowMillis = 30_000L,
            )

        assertEquals(TrafficInsightKind.Stale, insight.kind)
        assertEquals(TrafficInsightLevel.Notice, insight.level)
    }

    @Test
    fun formatTrafficInsightIncludesSelectedThreshold() {
        val insight =
            buildTrafficInsight(
                samples = listOf(LiveStats(up = 0f, down = 0f, timestampMillis = 1_000L)),
                sampleInterval = TrafficSampleInterval.Balanced,
                alertThreshold = TrafficAlertThreshold.Heavy,
                nowMillis = 2_000L,
            )

        val report = formatTrafficInsight(insight, sampleCount = 1, alertThreshold = TrafficAlertThreshold.Heavy)

        check("alert_threshold: heavy" in report)
        check("sustained_threshold:" in report)
        check("spike_floor:" in report)
        check("profile:" in report)
        check("trend:" in report)
        check("up_share:" in report)
        check("down_share:" in report)
        check("volatility:" in report)
    }

    @Test
    fun buildTrafficInsightCarriesTrafficProfileIntoReport() {
        val samples =
            listOf(
                LiveStats(up = 12 * 1024f, down = 180 * 1024f, timestampMillis = 1_000L),
                LiveStats(up = 10 * 1024f, down = 200 * 1024f, timestampMillis = 6_000L),
                LiveStats(up = 8 * 1024f, down = 220 * 1024f, timestampMillis = 11_000L),
            )

        val insight =
            buildTrafficInsight(
                samples = samples,
                sampleInterval = TrafficSampleInterval.Balanced,
                alertThreshold = TrafficAlertThreshold.Heavy,
                nowMillis = 12_000L,
            )
        val report = formatTrafficInsight(insight, sampleCount = samples.size)

        assertEquals(TrafficInsightKind.DownloadHeavy, insight.kind)
        assertEquals(TrafficProfileState.DownloadHeavy, insight.profile.state)
        check("profile: downloadheavy" in report)
        check("down_share:" in report)
    }
}
