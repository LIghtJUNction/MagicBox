package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficActionPlanTest {
    @Test
    fun buildTrafficActionPlanCollectsSamplesWhenEmpty() {
        val plan =
            buildTrafficActionPlan(
                samples = emptyList(),
                sampleInterval = TrafficSampleInterval.Balanced,
                nowMillis = 10_000L,
            )

        assertEquals(TrafficActionKind.CollectSamples, plan.kind)
        assertEquals(TrafficInsightLevel.Waiting, plan.level)
        assertEquals(listOf("api stats"), plan.commands)
    }

    @Test
    fun buildTrafficActionPlanInspectsSpikeWithConnectionCommands() {
        val samples =
            (0 until 11).map { index ->
                LiveStats(up = 4 * 1024f, down = 4 * 1024f, timestampMillis = 1_000L + index * 1_000L)
            } + LiveStats(up = 2 * 1024 * 1024f, down = 2 * 1024 * 1024f, timestampMillis = 12_000L)

        val plan =
            buildTrafficActionPlan(
                samples = samples,
                sampleInterval = TrafficSampleInterval.Balanced,
                alertThreshold = TrafficAlertThreshold.Sensitive,
                nowMillis = 13_000L,
            )

        assertEquals(TrafficActionKind.InspectSpike, plan.kind)
        assertEquals(TrafficInsightLevel.Warning, plan.level)
        assertTrue("api conns" in plan.commands)
        assertTrue("api close-top 3" in plan.commands)
    }

    @Test
    fun formatTrafficActionPlanIncludesCommandsAndMetrics() {
        val plan =
            TrafficActionPlan(
                kind = TrafficActionKind.ReduceSustainedTraffic,
                level = TrafficInsightLevel.Warning,
                commands = listOf("api conns", "api close-top 3"),
                sampleCount = 4,
                latestRate = 2048f,
                recentAverage = 1024f,
                windowMillis = 5_000L,
            )

        val report = formatTrafficActionPlan(plan)

        assertTrue("MagicBox traffic action plan" in report)
        assertTrue("kind: ReduceSustainedTraffic" in report)
        assertTrue("samples: 4" in report)
        assertTrue("window: 5s" in report)
        assertTrue("latest_rate: 2.0 KB/s" in report)
        assertTrue("1. api conns" in report)
        assertTrue("safety: ReadOnly" in report)
        assertTrue("2. api close-top 3" in report)
        assertTrue("safety: ManualConfirm" in report)
    }

    @Test
    fun trafficCommandSafetyMarksReadOnlyAndManualCommands() {
        assertEquals(TrafficCommandSafety.ReadOnly, trafficCommandSafety("api conns"))
        assertEquals(TrafficCommandSafety.ReadOnly, trafficCommandSafety("app list"))
        assertEquals(TrafficCommandSafety.ManualConfirm, trafficCommandSafety("api close-top 3"))
        assertEquals(TrafficCommandSafety.ManualConfirm, trafficCommandSafety("transparent set tun"))
    }

    @Test
    fun trafficDiagnosticCommandsExcludeDestructiveCloseCommands() {
        val plan =
            TrafficActionPlan(
                kind = TrafficActionKind.InspectSpike,
                level = TrafficInsightLevel.Warning,
                commands = listOf("api conns", "api close-top 3", "api groups", "api close-all"),
                sampleCount = 3,
                latestRate = 4096f,
                recentAverage = 1024f,
                windowMillis = 2_000L,
            )

        assertEquals(listOf("api conns", "api groups"), trafficDiagnosticCommands(plan))
    }

    @Test
    fun trafficDiagnosticCommandsKeepReadOnlyStatusCommandsDistinct() {
        val plan =
            TrafficActionPlan(
                kind = TrafficActionKind.RefreshStaleSamples,
                level = TrafficInsightLevel.Notice,
                commands = listOf("api stats", "health", "health", "service status", "transparent status"),
                sampleCount = 2,
                latestRate = 0f,
                recentAverage = 0f,
                windowMillis = 1_000L,
            )

        assertEquals(
            listOf("api stats", "health", "service status", "transparent status"),
            trafficDiagnosticCommands(plan),
        )
    }

    @Test
    fun trafficDiagnosticSnapshotCommandsUseFixedReadOnlyRuntimeChecks() {
        assertEquals(listOf("api stats", "service status", "health"), trafficDiagnosticSnapshotCommands())
        assertTrue(trafficDiagnosticSnapshotCommands().all { trafficCommandSafety(it) == TrafficCommandSafety.ReadOnly })
    }

    @Test
    fun formatTrafficDiagnosticsOutputIncludesPostRunSnapshotAndRedactsOutput() {
        val plan =
            TrafficActionPlan(
                kind = TrafficActionKind.InspectSpike,
                level = TrafficInsightLevel.Warning,
                commands = listOf("api conns"),
                sampleCount = 3,
                latestRate = 4096f,
                recentAverage = 1024f,
                windowMillis = 2_000L,
            )

        val report =
            formatTrafficDiagnosticsOutput(
                plan = plan,
                results = listOf("api conns" to CliResult(true, "api conns", "conn url=https://example.invalid/?token=abc")),
                snapshots =
                    listOf(
                        "api stats" to CliResult(true, "api stats", "up=1 down=2"),
                        "service status" to CliResult(false, "service status", "not running"),
                    ),
            )

        assertTrue("MagicBox traffic diagnostics" in report)
        assertTrue("post_run_snapshot:" in report)
        assertTrue("snapshot_commands: 2" in report)
        assertTrue("snapshot_succeeded: 1" in report)
        assertTrue("<redacted-url>" in report)
        assertTrue("not running" in report)
    }

    @Test
    fun parseTrafficDiagnosticsStatusReadsDiagnosticAndSnapshotCounts() {
        val status =
            parseTrafficDiagnosticsStatus(
                """
                MagicBox traffic diagnostics
                commands: 2
                succeeded: 1
                post_run_snapshot:
                snapshot_commands: 3
                snapshot_succeeded: 2
                """.trimIndent(),
            )

        assertEquals(TrafficDiagnosticsStatus(2, 1, 3, 2), status)
        assertEquals(null, parseTrafficDiagnosticsStatus("commands: 2"))
    }

    @Test
    fun trafficActionKindCommandSafetyMatrixStaysExplicit() {
        val expected =
            mapOf(
                TrafficActionKind.CollectSamples to listOf(TrafficCommandSafety.ReadOnly),
                TrafficActionKind.RefreshStaleSamples to
                    listOf(
                        TrafficCommandSafety.ReadOnly,
                        TrafficCommandSafety.ReadOnly,
                        TrafficCommandSafety.ReadOnly,
                        TrafficCommandSafety.ReadOnly,
                    ),
                TrafficActionKind.InspectSpike to
                    listOf(TrafficCommandSafety.ReadOnly, TrafficCommandSafety.ManualConfirm, TrafficCommandSafety.ReadOnly),
                TrafficActionKind.ReduceSustainedTraffic to
                    listOf(
                        TrafficCommandSafety.ReadOnly,
                        TrafficCommandSafety.ManualConfirm,
                        TrafficCommandSafety.ReadOnly,
                        TrafficCommandSafety.ReadOnly,
                    ),
                TrafficActionKind.InspectUploadHeavy to
                    listOf(TrafficCommandSafety.ReadOnly, TrafficCommandSafety.ReadOnly, TrafficCommandSafety.ReadOnly),
                TrafficActionKind.InspectDownloadHeavy to
                    listOf(TrafficCommandSafety.ReadOnly, TrafficCommandSafety.ReadOnly, TrafficCommandSafety.ManualConfirm),
                TrafficActionKind.KeepMonitoring to listOf(TrafficCommandSafety.ReadOnly, TrafficCommandSafety.ReadOnly),
            )

        expected.forEach { (kind, safety) ->
            val plan =
                TrafficActionPlan(
                    kind = kind,
                    level = TrafficInsightLevel.Ok,
                    commands = trafficActionCommands(kind),
                    sampleCount = 2,
                    latestRate = 0f,
                    recentAverage = 0f,
                    windowMillis = 1_000L,
                )

            assertEquals("Unexpected command safety for $kind", safety, plan.commands.map(::trafficCommandSafety))
        }
    }

    @Test
    fun trafficActionPlanTextFormatsLocalizedLabels() {
        assertEquals("处置建议", UiText.zh.trafficActionPlan())
        assertEquals("Action plan", UiText.en.trafficActionPlan())
        assertEquals("复制建议", UiText.zh.copyTrafficActionPlan())
        assertEquals("Share plan", UiText.en.shareTrafficActionPlan())
        assertEquals("运行诊断", UiText.zh.runTrafficDiagnostics())
        assertEquals("Copy diagnostics", UiText.en.copyTrafficDiagnostics())
        assertEquals("只读", UiText.zh.trafficCommandSafety(TrafficCommandSafety.ReadOnly))
        assertEquals("Confirm", UiText.en.trafficCommandSafety(TrafficCommandSafety.ManualConfirm))
    }
}
