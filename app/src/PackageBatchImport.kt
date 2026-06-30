package com.github.lightjunction.magicbox

private const val MAX_PACKAGE_BATCH_IMPORT = 60

data class PackageBatch(
    val valid: List<String>,
    val invalid: List<String>,
    val duplicates: List<String>,
    val overflow: List<String>,
)

data class PackageBatchImportResult(
    val result: CliResult,
    val wroteAny: Boolean,
)

fun parsePackageBatch(input: String): PackageBatch {
    val tokens =
        input
            .split(Regex("""[\s,;]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    val seen = linkedSetOf<String>()
    val valid = mutableListOf<String>()
    val invalid = mutableListOf<String>()
    val duplicates = mutableListOf<String>()
    val overflow = mutableListOf<String>()
    tokens.forEach { token ->
        when {
            !isSafePackage(token) -> invalid += token
            token in seen -> duplicates += token
            valid.size >= MAX_PACKAGE_BATCH_IMPORT -> overflow += token
            else -> {
                seen += token
                valid += token
            }
        }
    }
    return PackageBatch(valid, invalid, duplicates, overflow)
}

suspend fun addPackageBatch(
    target: AppTarget,
    batch: PackageBatch,
    existingPackages: Set<String>,
    t: UiText,
): PackageBatchImportResult {
    val existing = batch.valid.filter { it in existingPackages }
    val writable = batch.valid.filterNot { it in existingPackages }
    if (writable.isEmpty()) {
        return PackageBatchImportResult(
            CliResult(true, "$MAGICNET_CLI app add-many ${target.cli}", appendPackageBatchReport(t.noWritablePackages(), batch, existing, t)),
            false,
        )
    }
    val result = runMagicNet("app add-many ${target.cli} ${writable.joinToString(" ")}")
    if (!result.success) {
        return PackageBatchImportResult(
            result.copy(output = appendPackageBatchReport(result.output, batch, existing, t)),
            false,
        )
    }
    return PackageBatchImportResult(
        result.copy(
            summary = t.packageBulkImportSummary(writable.size, batch.invalid.size + batch.duplicates.size + batch.overflow.size + existing.size),
            output = appendPackageBatchReport(result.output, batch, existing, t),
        ),
        true,
    )
}

fun appendPackageBatchReport(
    output: String,
    batch: PackageBatch,
    existing: List<String>,
    t: UiText,
): String =
    buildString {
        if (output.isNotBlank()) appendLine(output.trim())
        appendPackageList(t.invalidPackagesLabel(), batch.invalid)
        appendPackageList(t.duplicatePackagesLabel(), batch.duplicates)
        appendPackageList(t.existingPackagesLabel(), existing)
        appendPackageList(t.overflowPackagesLabel(MAX_PACKAGE_BATCH_IMPORT), batch.overflow)
    }.trim()

private fun StringBuilder.appendPackageList(
    label: String,
    values: List<String>,
) {
    if (values.isEmpty()) return
    appendLine()
    appendLine("$label: ${values.take(12).joinToString(", ")}")
}
