package com.github.lightjunction.magicbox

data class NetworkSnapshotSummary(
    val interfaces: Int,
    val ipRules: Int,
    val routes: Int,
    val natRules: Int,
)

suspend fun loadNetworkSnapshot(t: UiText): CliResult {
    val topology = runMagicNet("topology")
    if (topology.success && topology.output.isNotBlank()) {
        return topology.copy(summary = t.networkSnapshotLoaded())
    }

    val routes = runMagicNet("sysroute snapshot")
    return if (routes.success) {
        routes.copy(summary = t.routeSnapshotLoaded())
    } else {
        topology.copy(
            output =
                buildString {
                    appendLine(topology.output)
                    appendLine(routes.output)
                }.trim(),
            summary = t.networkSnapshotFailed(),
        )
    }
}

fun parseNetworkSnapshotSummary(output: String): NetworkSnapshotSummary {
    val lines = output.lineSequence().map { it.trim() }.toList()
    return NetworkSnapshotSummary(
        interfaces = countSectionLines(lines, "[interfaces]", "[routes]"),
        ipRules = countSectionLines(lines, "ip rule:", "ip route:"),
        routes = countSectionLines(lines, "ip route:", "[forwarding]"),
        natRules = countSectionLines(lines, "[forwarding]", null),
    )
}

private fun countSectionLines(
    lines: List<String>,
    start: String,
    end: String?,
): Int {
    val startIndex = lines.indexOfFirst { it.equals(start, ignoreCase = true) }
    if (startIndex < 0) return 0
    val endIndex =
        end?.let { marker ->
            lines.drop(startIndex + 1).indexOfFirst { it.equals(marker, ignoreCase = true) }.takeIf { it >= 0 }?.let { startIndex + 1 + it }
        } ?: lines.size
    return lines
        .subList(startIndex + 1, endIndex)
        .count { line -> line.isNotBlank() && !line.startsWith("[") && line != "ip rule:" && line != "ip route:" }
}

fun UiText.networkInterfaces(): String = if (this === UiText.zh) "接口" else "Interfaces"

fun UiText.networkRules(): String = if (this === UiText.zh) "规则" else "Rules"

fun UiText.networkRoutes(): String = if (this === UiText.zh) "路由" else "Routes"

fun UiText.networkNatRules(): String = if (this === UiText.zh) "NAT" else "NAT"
