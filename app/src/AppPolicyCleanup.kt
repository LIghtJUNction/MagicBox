package com.github.lightjunction.magicbox

data class AppPolicyCleanupResult(
    val result: CliResult,
    val removedAny: Boolean,
    val removed: Int = 0,
    val failed: Int = 0,
)

suspend fun removeStaleAppPolicies(
    target: AppTarget,
    packageNames: List<String>,
    t: UiText,
): AppPolicyCleanupResult {
    val result = removeAppPolicies(target, packageNames, t)
    return result.copy(
        result =
            result.result.copy(
                command = "$MAGICNET_CLI app remove <stale> ${target.cli}",
                summary = t.staleCleanupSummary(result.removed, result.failed),
            ),
    )
}

suspend fun removeAppPolicies(
    target: AppTarget,
    packageNames: List<String>,
    t: UiText,
): AppPolicyCleanupResult {
    if (packageNames.isEmpty()) {
        return AppPolicyCleanupResult(
            CliResult(true, "$MAGICNET_CLI app remove <package> ${target.cli}", t.noStalePackages()),
            false,
        )
    }
    val failed = mutableListOf<String>()
    val output =
        buildString {
            packageNames.forEach { pkg ->
                val result = runMagicNet("app remove $pkg ${target.cli}")
                appendLine(result.command)
                appendLine(result.summary)
                if (result.output.isNotBlank() && result.output != result.summary) appendLine(result.output)
                appendLine()
                if (!result.success) failed += pkg
            }
        }
    val removed = packageNames.size - failed.size
    return AppPolicyCleanupResult(
        CliResult(
            success = failed.isEmpty(),
            command = "$MAGICNET_CLI app remove <package> ${target.cli}",
            output = appendStaleCleanupReport(output, failed, t),
            summary = t.appPolicyRemoveSummary(removed, failed.size),
        ),
        removedAny = removed > 0,
        removed = removed,
        failed = failed.size,
    )
}

private fun appendStaleCleanupReport(
    output: String,
    failed: List<String>,
    t: UiText,
): String =
    buildString {
        if (output.isNotBlank()) appendLine(output.trim())
        if (failed.isNotEmpty()) {
            appendLine()
            appendLine("${t.failedPackagesLabel()}: ${failed.take(12).joinToString(", ")}")
        }
    }.trim()
