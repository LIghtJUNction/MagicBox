package com.github.lightjunction.magicbox

private const val MAX_DOMAIN_BATCH_IMPORT = 80

data class DomainBatch(
    val valid: List<String>,
    val invalid: List<String>,
    val duplicates: List<String>,
    val overflow: List<String>,
)

fun parseDomainBatch(input: String): DomainBatch {
    val tokens =
        input
            .split(Regex("""[\s,;]+"""))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
    val seen = linkedSetOf<String>()
    val valid = mutableListOf<String>()
    val invalid = mutableListOf<String>()
    val duplicates = mutableListOf<String>()
    val overflow = mutableListOf<String>()
    tokens.forEach { token ->
        when {
            !isSafeDomain(token) -> invalid += token
            token in seen -> duplicates += token
            valid.size >= MAX_DOMAIN_BATCH_IMPORT -> overflow += token
            else -> {
                seen += token
                valid += token
            }
        }
    }
    return DomainBatch(valid, invalid, duplicates, overflow)
}

suspend fun addDomainBatch(
    bucket: RuleBucket,
    batch: DomainBatch,
    existingDomains: Set<String>,
    t: UiText,
): CliResult {
    val existing = batch.valid.filter { it in existingDomains }
    val writable = batch.valid.filterNot { it in existingDomains }
    if (writable.isEmpty()) {
        return CliResult(true, "$MAGICNET_CLI route add-domain ${bucket.cli}", appendDomainBatchReport(t.noWritableDomains(), batch, existing, emptyList(), t))
    }
    val failed = mutableListOf<String>()
    val output =
        buildString {
            writable.forEach { domain ->
                val result = runMagicNet("route add-domain ${bucket.cli} $domain")
                appendLine(result.command)
                appendLine(result.summary)
                if (result.output.isNotBlank() && result.output != result.summary) appendLine(result.output)
                appendLine()
                if (!result.success) failed += domain
            }
        }
    return CliResult(
        success = failed.isEmpty(),
        command = "$MAGICNET_CLI route add-domain ${bucket.cli} ${writable.joinToString(" ")}",
        output = appendDomainBatchReport(output, batch, existing, failed, t),
        summary = t.domainBulkImportSummary(writable.size - failed.size, batch.invalid.size + batch.duplicates.size + batch.overflow.size + existing.size + failed.size),
    )
}

fun appendDomainBatchReport(
    output: String,
    batch: DomainBatch,
    existing: List<String>,
    failed: List<String>,
    t: UiText,
): String =
    buildString {
        if (output.isNotBlank()) appendLine(output.trim())
        appendDomainList(t.invalidDomainsLabel(), batch.invalid)
        appendDomainList(t.duplicateDomainsLabel(), batch.duplicates)
        appendDomainList(t.existingDomainsLabel(), existing)
        appendDomainList(t.failedDomainsLabel(), failed)
        appendDomainList(t.overflowDomainsLabel(MAX_DOMAIN_BATCH_IMPORT), batch.overflow)
    }.trim()

private fun StringBuilder.appendDomainList(
    label: String,
    values: List<String>,
) {
    if (values.isEmpty()) return
    appendLine()
    appendLine("$label: ${values.take(12).joinToString(", ")}")
}
