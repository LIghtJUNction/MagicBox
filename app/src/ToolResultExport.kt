package com.github.lightjunction.magicbox

fun formatToolResult(result: CliResult): String =
    buildString {
        appendLine("command: ${redactSupportText(result.command)}")
        appendLine("success: ${result.success}")
        appendLine("summary: ${redactSupportText(result.summary)}")
        val output = redactSupportText(result.output).trim()
        if (output.isNotBlank()) {
            appendLine()
            appendLine(output)
        }
    }.trim()

fun formatCommandHistoryEntry(entry: CommandHistoryEntry): String =
    buildString {
        appendLine("command: ${redactSupportText(entry.command)}")
        appendLine("summary: ${redactSupportText(entry.summary)}")
        val output = redactSupportText(entry.output).trim()
        if (output.isNotBlank()) {
            appendLine()
            appendLine(output)
        }
    }.trim()
