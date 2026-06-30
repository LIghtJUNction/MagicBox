package com.github.lightjunction.magicbox

fun buildIssueDraft(
    module: CliResult?,
    subscription: CliResult?,
    health: CliResult?,
    routes: CliResult?,
    lastCommand: CliResult?,
    lastCommandTitle: String = "Last tool command",
    nodes: CliResult? = null,
    currentNode: CliResult? = null,
    dns: CliResult? = null,
    warp: CliResult? = null,
): String {
    val statusSummary =
        supportStatusSummary(
            "Module bridge" to module,
            "Subscription" to subscription,
            "Nodes" to nodes,
            "Current node" to currentNode,
            "DNS" to dns,
            "WARP" to warp,
            "Health" to health,
            "Routes" to routes,
            lastCommandTitle to lastCommand,
        )
    fun section(title: String, result: CliResult?, limit: Int = 1200): String =
        buildString {
            appendLine("### $title")
            appendLine("status: ${if (result?.success == true) "ok" else "needs-check"}")
            result?.command?.let { appendLine("command: ${redactSupportText(it)}") }
            appendLine()
            appendLine(redactSupportText(result?.output.orEmpty()).take(limit).ifBlank { "(empty)" })
        }.trim()

    return """
        ## MagicBox Support Report

        ### App
        MagicBox ${BuildConfig.VERSION_NAME}

        ### Status summary
        $statusSummary

        ${section("Module bridge", module)}

        ${section("Subscription", subscription)}

        ${section("Nodes", nodes, limit = 900)}

        ${section("Current node", currentNode, limit = 600)}

        ${section("DNS", dns, limit = 900)}

        ${section("WARP", warp, limit = 900)}

        ${section("Health", health)}

        ${section("Routes", routes)}

        ${section(lastCommandTitle, lastCommand, limit = 1800)}
    """.trimIndent()
}

private fun supportStatusSummary(vararg sections: Pair<String, CliResult?>): String =
    sections.joinToString("\n") { (title, result) ->
        "- $title: ${if (result?.success == true) "ok" else "needs-check"}"
    }

fun redactSupportText(value: String): String =
    value
        .replace(Regex("""https?://[^\s]+"""), "<redacted-url>")
        .replace(Regex("""(?i)(bearer\s+)([^\s]+)"""), "$1<redacted>")
        .replace(
            Regex("""(?i)(["']?[\w.-]*(?:password|passwd|token|secret|key|uuid)[\w.-]*["']?\s*[:=]\s*)("[^"]*"|'[^']*'|[^,\s]+)"""),
            "$1<redacted>",
        )
        .let { partiallyRedacted ->
            Regex("""\S+""").replace(partiallyRedacted) { match ->
                val lower = match.value.lowercase()
                if (
                    lower.contains("password") ||
                    lower.contains("passwd") ||
                    lower.contains("token") ||
                    lower.contains("secret") ||
                    lower.contains("uuid") ||
                    lower.contains("authorization")
                ) {
                    "<redacted-secret>"
                } else {
                    match.value
                }
            }
        }
