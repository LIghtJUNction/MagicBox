package com.github.lightjunction.magicbox

data class SupportReportSummary(
    val ok: Int,
    val needsCheck: Int,
    val needsCheckTitles: List<String>,
) {
    val total: Int = ok + needsCheck
}

fun parseSupportReportSummary(draft: String): SupportReportSummary {
    var ok = 0
    var needsCheck = 0
    val needsCheckTitles = mutableListOf<String>()
    var inSummary = false
    draft
        .lineSequence()
        .map { it.trim() }
        .forEach { line ->
            when {
                line == "### Status summary" -> {
                    inSummary = true
                    return@forEach
                }
                inSummary && line.startsWith("### ") -> {
                    inSummary = false
                    return@forEach
                }
                !inSummary || !line.startsWith("- ") -> return@forEach
            }
            val title = line.removePrefix("- ").substringBeforeLast(":").trim()
            when (line.substringAfterLast(":").trim()) {
                "ok" -> ok += 1
                "needs-check" -> {
                    needsCheck += 1
                    if (title.isNotBlank()) needsCheckTitles += title
                }
            }
        }
    return SupportReportSummary(ok, needsCheck, needsCheckTitles)
}

fun UiText.supportReportOk(count: Int): String = if (this === UiText.zh) "$count 正常" else "$count ok"

fun UiText.supportReportNeedsCheck(count: Int): String = if (this === UiText.zh) "$count 待检查" else "$count needs-check"

fun UiText.supportReportNeedsCheckList(
    names: List<String>,
    hidden: Int,
): String {
    val suffix = if (hidden > 0) " +$hidden" else ""
    return if (this === UiText.zh) {
        "待检查：${names.joinToString(", ")}$suffix"
    } else {
        "Needs check: ${names.joinToString(", ")}$suffix"
    }
}
