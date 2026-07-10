package com.github.lightjunction.magicbox

data class ConfigWriteVerification(
    val service: CliResult,
    val health: CliResult,
) {
    val success: Boolean = service.success && health.success
}

suspend fun verifyConfigWriteRuntime(): ConfigWriteVerification =
    ConfigWriteVerification(
        service = runMagicNet("service status"),
        health = runMagicNet("health"),
    )

fun formatConfigWriteVerification(verification: ConfigWriteVerification): String =
    buildString {
        appendLine("post_write_verification:")
        appendLine("service_status=${if (verification.service.success) "ok" else "failed"}")
        appendLine("health=${if (verification.health.success) "ok" else "failed"}")
        appendLine("verified=${verification.success}")
        appendLine()
        appendLine("service:")
        appendLine(redactSupportText(verification.service.summary))
        appendLine()
        appendLine("health:")
        appendLine(redactSupportText(verification.health.summary))
    }.trim()

fun UiText.configWriteVerification(): String =
    if (this === UiText.zh) "写入后校验" else "Post-write verification"

fun UiText.configWriteVerificationSummary(verification: ConfigWriteVerification): String =
    if (verification.success) {
        if (this === UiText.zh) "已触发写入，并通过 service status 与 health 校验。" else "Write triggered and verified with service status and health."
    } else {
        if (this === UiText.zh) "写入已触发，但运行时校验仍有失败项；请查看输出。" else "Write was triggered, but runtime verification still has failures."
    }
