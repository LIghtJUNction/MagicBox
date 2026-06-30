package com.github.lightjunction.magicbox

data class DomainPolicyCleanupResult(
    val result: CliResult,
    val removedAny: Boolean,
)

suspend fun removeDomainsFromBucket(
    bucket: RuleBucket,
    domains: List<String>,
    t: UiText,
): DomainPolicyCleanupResult {
    if (domains.isEmpty()) {
        return DomainPolicyCleanupResult(
            CliResult(true, "$MAGICNET_CLI route remove-domain ${bucket.cli} <domain>", t.noDomains(t.ruleBucket(bucket).lowercase())),
            false,
        )
    }
    val failed = mutableListOf<String>()
    val output =
        buildString {
            domains.forEach { domain ->
                val result = runMagicNet("route remove-domain ${bucket.cli} $domain")
                appendLine(result.command)
                appendLine(result.summary)
                if (result.output.isNotBlank() && result.output != result.summary) appendLine(result.output)
                appendLine()
                if (!result.success) failed += domain
            }
        }
    val removed = domains.size - failed.size
    return DomainPolicyCleanupResult(
        CliResult(
            success = failed.isEmpty(),
            command = "$MAGICNET_CLI route remove-domain ${bucket.cli} <domain>",
            output = appendDomainRemovalReport(output, failed, t),
            summary = t.domainPolicyRemoveSummary(removed, failed.size),
        ),
        removedAny = removed > 0,
    )
}

private fun appendDomainRemovalReport(
    output: String,
    failed: List<String>,
    t: UiText,
): String =
    buildString {
        if (output.isNotBlank()) appendLine(output.trim())
        if (failed.isNotEmpty()) {
            appendLine()
            appendLine("${t.failedDomainRemovalsLabel()}: ${failed.take(12).joinToString(", ")}")
        }
    }.trim()
