package com.github.lightjunction.magicbox

data class HealthIssueSummary(
    val warningCount: Int,
    val errorCount: Int,
    val issues: List<HealthEntry>,
) {
    val hasIssues: Boolean = issues.isNotEmpty()
}

data class HealthSeverityCounts(
    val ok: Int,
    val warn: Int,
    val error: Int,
) {
    val total: Int = ok + warn + error
}

enum class HealthEntryFilter {
    Issues,
    All,
}

fun parseHealthEntries(output: String): List<HealthEntry> = output.lineSequence().mapNotNull(::parseHealthEntry).toList()

fun prioritizeHealthEntries(entries: List<HealthEntry>): List<HealthEntry> =
    entries
        .mapIndexed { index, entry -> IndexedHealthEntry(index, entry) }
        .sortedWith(compareBy<IndexedHealthEntry> { it.entry.severity.displayPriority() }.thenBy { it.index })
        .map { it.entry }

fun filterHealthEntries(
    entries: List<HealthEntry>,
    filter: HealthEntryFilter,
): List<HealthEntry> =
    when (filter) {
        HealthEntryFilter.Issues -> entries.filter { it.severity != HealthSeverity.Ok }
        HealthEntryFilter.All -> entries
    }

fun buildHealthSeverityCounts(entries: List<HealthEntry>): HealthSeverityCounts =
    HealthSeverityCounts(
        ok = entries.count { it.severity == HealthSeverity.Ok },
        warn = entries.count { it.severity == HealthSeverity.Warn },
        error = entries.count { it.severity == HealthSeverity.Error },
    )

fun buildHealthIssueSummary(entries: List<HealthEntry>): HealthIssueSummary {
    val issues = prioritizeHealthEntries(entries).filter { it.severity != HealthSeverity.Ok }
    return HealthIssueSummary(
        warningCount = issues.count { it.severity == HealthSeverity.Warn },
        errorCount = issues.count { it.severity == HealthSeverity.Error },
        issues = issues,
    )
}

fun formatHealthIssueSummary(
    summary: HealthIssueSummary,
    text: UiText = UiText.en,
): String =
    buildString {
        appendLine(text.healthIssueReportTitle())
        appendLine("${text.healthIssueReportWarnings()}: ${summary.warningCount}")
        appendLine("${text.healthIssueReportErrors()}: ${summary.errorCount}")
        if (!summary.hasIssues) {
            appendLine("${text.healthIssueReportIssues()}: ${text.healthIssueReportNone()}")
            return@buildString
        }
        summary.issues.take(20).forEachIndexed { index, issue ->
            appendLine("${index + 1}. ${issue.severity.reportName()} ${redactSupportText(issue.title)}")
            val details = redactSupportText(issue.details)
            if (details.isNotBlank()) appendLine("   $details")
        }
        if (summary.issues.size > 20) {
            appendLine(text.healthIssueReportHidden(summary.issues.size - 20))
        }
    }.trim()

fun UiText.healthIssueSummary(summary: HealthIssueSummary): String =
    if (!summary.hasIssues) {
        if (this === UiText.zh) "未发现警告或错误。" else "No warnings or errors."
    } else if (this === UiText.zh) {
        "${summary.warningCount} 个警告，${summary.errorCount} 个错误；可复制摘要用于排查。"
    } else {
        "${summary.warningCount} warnings, ${summary.errorCount} errors; copy the summary for debugging."
    }

fun UiText.copyHealthIssues(): String = if (this === UiText.zh) "复制问题摘要" else "Copy issues"

fun UiText.shareHealthIssues(): String = if (this === UiText.zh) "分享问题摘要" else "Share issues"

fun UiText.healthEntryFilter(filter: HealthEntryFilter): String =
    when (filter) {
        HealthEntryFilter.Issues -> if (this === UiText.zh) "问题" else "Issues"
        HealthEntryFilter.All -> if (this === UiText.zh) "全部" else "All"
    }

fun UiText.healthOkLabel(): String = if (this === UiText.zh) "正常" else "OK"

fun UiText.healthWarnLabel(): String = if (this === UiText.zh) "警告" else "Warn"

fun UiText.healthErrorLabel(): String = if (this === UiText.zh) "错误" else "Error"

private fun UiText.healthIssueReportTitle(): String =
    if (this === UiText.zh) "MagicBox 健康问题摘要" else "MagicBox health issue summary"

private fun UiText.healthIssueReportWarnings(): String = if (this === UiText.zh) "警告" else "warnings"

private fun UiText.healthIssueReportErrors(): String = if (this === UiText.zh) "错误" else "errors"

private fun UiText.healthIssueReportIssues(): String = if (this === UiText.zh) "问题" else "issues"

private fun UiText.healthIssueReportNone(): String = if (this === UiText.zh) "无" else "none"

private fun UiText.healthIssueReportHidden(count: Int): String =
    if (this === UiText.zh) "... 还有 $count 个问题未显示" else "... $count more issues not shown"

private fun HealthSeverity.reportName(): String =
    when (this) {
        HealthSeverity.Ok -> "ok"
        HealthSeverity.Warn -> "warn"
        HealthSeverity.Error -> "error"
    }

private data class IndexedHealthEntry(
    val index: Int,
    val entry: HealthEntry,
)

private fun HealthSeverity.displayPriority(): Int =
    when (this) {
        HealthSeverity.Error -> 0
        HealthSeverity.Warn -> 1
        HealthSeverity.Ok -> 2
    }
