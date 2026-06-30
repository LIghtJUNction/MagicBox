package com.github.lightjunction.magicbox

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class SupportReportRefresh(
    val runtime: ToolsRuntimeSnapshot,
    val support: CliResult,
    val draft: String,
)

suspend fun refreshSupportReport(): SupportReportRefresh =
    coroutineScope {
        val runtimeJob = async { loadToolsRuntimeSnapshot() }
        val supportJob = async { collectSupportOutput() }
        val runtime = runtimeJob.await()
        val support = supportJob.await()
        SupportReportRefresh(
            runtime = runtime,
            support = support,
            draft =
                buildIssueDraft(
                    module = runtime.module,
                    subscription = runtime.subscription,
                    health = runtime.health,
                    routes = runtime.routes,
                    lastCommand = support,
                    lastCommandTitle = supportReportTitle(support),
                    nodes = runtime.nodes,
                    currentNode = runtime.currentNode,
                    dns = runtime.dns,
                    warp = runtime.warp,
                ),
        )
    }

private suspend fun collectSupportOutput(): CliResult {
    val support = runMagicNetLong("support bundle")
    if (support.success) return support
    val diagnose = runMagicNetLong("diagnose")
    return CliResult(
        success = diagnose.success,
        command = "${support.command} || ${diagnose.command}",
        output =
            buildString {
                appendLine("support bundle failed:")
                appendLine(support.output.ifBlank { "(empty)" })
                appendLine()
                appendLine("diagnose fallback:")
                appendLine(diagnose.output.ifBlank { "(empty)" })
            }.trim(),
    )
}

private fun supportReportTitle(result: CliResult): String =
    if (result.command.contains("diagnose")) "Support bundle / diagnose" else "Support bundle"
