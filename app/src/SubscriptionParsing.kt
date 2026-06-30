package com.github.lightjunction.magicbox

import android.util.Base64
import java.nio.charset.StandardCharsets

private const val MAX_SUBSCRIPTION_URLS = 12

data class SubscriptionInput(
    val valid: List<String>,
    val invalid: List<String>,
    val duplicates: List<String>,
    val overflow: List<String>,
)

fun parseSubscriptionSummary(output: String): SubscriptionSummary {
    val sources = mutableListOf<SubscriptionSource>()
    var primaryConfigured = false
    val linePattern = Regex("""^([A-Za-z0-9_-]+)(?:\.(\d+))?=(.*)$""")

    output.lineSequence().forEach { raw ->
        val line = raw.trim()
        val match = linePattern.find(line) ?: return@forEach
        val target = match.groupValues[1]
        if (!target.equals("sing-box", ignoreCase = true) && !target.equals("singbox", ignoreCase = true)) {
            return@forEach
        }
        val value = match.groupValues[3].trim()
        val indexText = match.groupValues.getOrNull(2).orEmpty()
        if (indexText.isBlank()) {
            primaryConfigured = value.isNotBlank()
        } else {
            sources.add(
                SubscriptionSource(
                    target = "sing-box",
                    index = indexText.toIntOrNull() ?: (sources.size + 1),
                    configured = value.isNotBlank(),
                ),
            )
        }
    }

    return SubscriptionSummary(sources.sortedBy { it.index }, primaryConfigured)
}

fun parseSubscriptionInput(input: String): SubscriptionInput {
    val tokens =
        input
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    val seen = linkedSetOf<String>()
    val valid = mutableListOf<String>()
    val invalid = mutableListOf<String>()
    val duplicates = mutableListOf<String>()
    val overflow = mutableListOf<String>()
    tokens.forEach { token ->
        when {
            !isValidSubscriptionUrl(token) -> invalid += token
            token in seen -> duplicates += token
            valid.size >= MAX_SUBSCRIPTION_URLS -> overflow += token
            else -> {
                seen += token
                valid += token
            }
        }
    }
    return SubscriptionInput(valid, invalid, duplicates, overflow)
}

suspend fun saveSingBoxSubscriptions(
    input: SubscriptionInput,
    t: UiText,
): CliResult {
    if (input.valid.isEmpty()) {
        return CliResult(false, "$MAGICNET_CLI sub set-file sing-box <redacted>", appendSubscriptionInputReport(t.noValidSubscriptions(), input, t))
    }
    val payload = Base64.encodeToString(input.valid.joinToString(separator = "\n", postfix = "\n").toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    val result = runMagicNet("sub set-file sing-box $payload")
    val output = appendSubscriptionInputReport(result.output, input, t)
    return result.copy(
        command = "$MAGICNET_CLI sub set-file sing-box <redacted>",
        output = output,
        summary = if (result.success) t.subscriptionSaved(input.valid.size) else result.summary,
    )
}

private fun isValidSubscriptionUrl(value: String): Boolean =
    (value.startsWith("http://") || value.startsWith("https://")) && value.none { it.isWhitespace() }

private fun appendSubscriptionInputReport(
    output: String,
    input: SubscriptionInput,
    t: UiText,
): String =
    buildString {
        if (output.isNotBlank()) appendLine(redactSupportText(output.trim()))
        appendSubscriptionList(t.invalidSubscriptionsLabel(), input.invalid)
        appendSubscriptionList(t.duplicateSubscriptionsLabel(), input.duplicates)
        appendSubscriptionList(t.overflowSubscriptionsLabel(MAX_SUBSCRIPTION_URLS), input.overflow)
    }.trim()

private fun StringBuilder.appendSubscriptionList(
    label: String,
    values: List<String>,
) {
    if (values.isEmpty()) return
    appendLine()
    appendLine("$label: ${values.take(8).joinToString(", ") { redactSupportText(it) }}")
}
