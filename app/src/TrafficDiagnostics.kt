package com.github.lightjunction.magicbox

enum class TrafficCommandSafety {
    ReadOnly,
    ManualConfirm,
}

data class TrafficDiagnosticsStatus(
    val commands: Int,
    val succeeded: Int,
    val snapshotCommands: Int,
    val snapshotSucceeded: Int,
)

private val SAFE_TRAFFIC_DIAGNOSTIC_COMMANDS =
    setOf(
        "api stats",
        "health",
        "service status",
        "transparent status",
        "api conns",
        "api groups",
        "app list",
    )

fun trafficCommandSafety(command: String): TrafficCommandSafety =
    if (command in SAFE_TRAFFIC_DIAGNOSTIC_COMMANDS) TrafficCommandSafety.ReadOnly else TrafficCommandSafety.ManualConfirm

fun trafficDiagnosticCommands(plan: TrafficActionPlan): List<String> =
    plan.commands.filter { trafficCommandSafety(it) == TrafficCommandSafety.ReadOnly }.distinct()

fun trafficDiagnosticSnapshotCommands(): List<String> = listOf("api stats", "service status", "health")

fun parseTrafficDiagnosticsStatus(output: String): TrafficDiagnosticsStatus? {
    val commands = output.lineValue("commands") ?: return null
    val succeeded = output.lineValue("succeeded") ?: return null
    val snapshotCommands = output.lineValue("snapshot_commands") ?: return null
    val snapshotSucceeded = output.lineValue("snapshot_succeeded") ?: return null
    return TrafficDiagnosticsStatus(commands, succeeded, snapshotCommands, snapshotSucceeded)
}

suspend fun collectTrafficDiagnostics(plan: TrafficActionPlan): CliResult {
    val commands = trafficDiagnosticCommands(plan)
    if (commands.isEmpty()) {
        return CliResult(false, "traffic diagnostics", "No safe diagnostic commands for ${plan.kind}.")
    }
    val results = commands.map { command -> command to runMagicNetLong(command) }
    val snapshots = trafficDiagnosticSnapshotCommands().map { command -> command to runMagicNetLong(command) }
    val succeeded = results.count { it.second.success }
    val snapshotSucceeded = snapshots.count { it.second.success }
    return CliResult(
        success = succeeded == results.size && snapshotSucceeded == snapshots.size,
        command = "traffic diagnostics: ${commands.joinToString(", ")}",
        output = formatTrafficDiagnosticsOutput(plan, results, snapshots),
        summary = "Traffic diagnostics: $succeeded/${results.size} commands, post-check $snapshotSucceeded/${snapshots.size}.",
    )
}

fun formatTrafficDiagnosticsOutput(
    plan: TrafficActionPlan,
    results: List<Pair<String, CliResult>>,
    snapshots: List<Pair<String, CliResult>>,
): String =
    buildString {
        appendLine("MagicBox traffic diagnostics")
        appendLine("plan: ${plan.kind.name}")
        appendLine("commands: ${results.size}")
        appendLine("succeeded: ${results.count { it.second.success }}")
        appendResults(results)
        appendLine()
        appendLine("post_run_snapshot:")
        appendLine("snapshot_commands: ${snapshots.size}")
        appendLine("snapshot_succeeded: ${snapshots.count { it.second.success }}")
        appendResults(snapshots)
    }.trim()

private fun StringBuilder.appendResults(results: List<Pair<String, CliResult>>) {
    results.forEach { (command, result) ->
        appendLine()
        appendLine("## $command")
        appendLine("success: ${result.success}")
        val output = redactSupportText(result.output).trim()
        if (output.isBlank()) appendLine("(empty)") else appendLine(output)
    }
}

private fun String.lineValue(key: String): Int? =
    lineSequence()
        .firstOrNull { it.startsWith("$key:") }
        ?.substringAfter(":")
        ?.trim()
        ?.toIntOrNull()
