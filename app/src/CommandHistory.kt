package com.github.lightjunction.magicbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val COMMAND_HISTORY_PREF = "magicbox_command_history"
private const val COMMAND_HISTORY_KEY = "entries"
private const val COMMAND_HISTORY_LIMIT = 8

data class CommandHistoryEntry(
    val command: String,
    val summary: String,
    val output: String,
    val timestampMillis: Long,
)

fun loadCommandHistory(context: Context): List<CommandHistoryEntry> {
    val raw =
        context
            .getSharedPreferences(COMMAND_HISTORY_PREF, Context.MODE_PRIVATE)
            .getString(COMMAND_HISTORY_KEY, null)
            ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            CommandHistoryEntry(
                command = item.optString("command"),
                summary = item.optString("summary"),
                output = item.optString("output"),
                timestampMillis = item.optLong("timestampMillis"),
            )
        }.filter { it.command.isNotBlank() }
    }.getOrDefault(emptyList())
}

fun appendCommandHistory(
    context: Context,
    result: CliResult,
): List<CommandHistoryEntry> {
    val entry = CommandHistoryEntry(result.command, result.summary, result.output, System.currentTimeMillis())
    val next =
        (listOf(entry) + loadCommandHistory(context).filterNot { it.command == entry.command })
            .take(COMMAND_HISTORY_LIMIT)
    saveCommandHistory(context, next)
    return next
}

fun clearCommandHistory(context: Context): List<CommandHistoryEntry> {
    saveCommandHistory(context, emptyList())
    return emptyList()
}

fun removeCommandHistory(
    context: Context,
    target: CommandHistoryEntry,
): List<CommandHistoryEntry> {
    val next = loadCommandHistory(context).filterNot {
        it.timestampMillis == target.timestampMillis && it.command == target.command
    }
    saveCommandHistory(context, next)
    return next
}

fun commandHistoryArgs(command: String): String? =
    command
        .takeIf { it.startsWith("$MAGICNET_CLI ") }
        ?.removePrefix("$MAGICNET_CLI ")
        ?.trim()
        ?.takeIf { it.isNotBlank() && Regex("""[A-Za-z0-9_./: -]+""").matches(it) }
        ?.takeIf { "<" !in it && "redacted" !in it.lowercase() }

fun canRerunCommand(command: String): Boolean = commandHistoryArgs(command) != null

private fun saveCommandHistory(
    context: Context,
    entries: List<CommandHistoryEntry>,
) {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject()
                .put("command", entry.command)
                .put("summary", entry.summary)
                .put("output", entry.output)
                .put("timestampMillis", entry.timestampMillis),
        )
    }
    context.getSharedPreferences(COMMAND_HISTORY_PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(COMMAND_HISTORY_KEY, array.toString())
        .apply()
}
