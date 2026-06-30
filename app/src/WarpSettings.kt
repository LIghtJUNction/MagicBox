package com.github.lightjunction.magicbox

enum class WarpAction(val cli: String) {
    Enable("enable"),
    Disable("disable"),
}

enum class WarpSelectorAction(val cli: String) {
    Global("global"),
    Rule("rule"),
}

data class WarpStatus(
    val enabled: Boolean,
    val configured: Boolean,
    val tag: String,
    val endpoint: String,
    val addresses: String,
    val allowedIps: String,
)

fun parseWarpStatus(output: String): WarpStatus {
    val values =
        output
            .lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index < 0) null else line.take(index).trim() to line.drop(index + 1).trim()
            }
            .toMap()
    return WarpStatus(
        enabled = values["enabled"] == "1",
        configured = values["configured"] == "1",
        tag = values["tag"].orEmpty().ifBlank { "warp" },
        endpoint = values["endpoint"].orEmpty(),
        addresses = values["addresses"].orEmpty(),
        allowedIps = values["allowed_ips"].orEmpty(),
    )
}

fun parseWarpRouteDomains(output: String): List<String> {
    val domains = mutableListOf<String>()
    var inWarp = false
    output.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) return@forEach
        if (line.endsWith("domain suffixes:", ignoreCase = true)) {
            inWarp = line.equals("warp domain suffixes:", ignoreCase = true)
            return@forEach
        }
        if (inWarp) domains += line
    }
    return domains.distinct()
}
