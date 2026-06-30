package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandHistoryCard(
    entries: List<CommandHistoryEntry>,
    loading: Boolean,
    onClear: () -> Unit,
    onRerun: (CommandHistoryEntry) -> Unit,
    onCopy: (CommandHistoryEntry) -> Unit,
    onShare: (CommandHistoryEntry) -> Unit,
    onDelete: (CommandHistoryEntry) -> Unit,
) {
    val t = LocalUiText.current
    var pendingClear by remember { mutableStateOf(false) }
    var pendingDeleteCommand by remember { mutableStateOf<String?>(null) }
    var copiedCommand by remember { mutableStateOf<String?>(null) }
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.commandHistoryTitle())
                Body(t.commandHistorySummary(entries.size))
            }
            SmallButton(
                if (pendingClear) t.cancel() else t.clear(),
                enabled = entries.isNotEmpty() && !loading,
            ) {
                pendingClear = !pendingClear
            }
        }
        if (pendingClear) {
            Spacer(Modifier.height(8.dp))
            Body(t.confirmClearCommandHistory(entries.size))
            Spacer(Modifier.height(8.dp))
            SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                onClear()
                pendingClear = false
            }
        }
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Body(t.noCommandHistory())
        } else {
            entries.forEach { entry ->
                val deletePending = pendingDeleteCommand == entry.command
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Mono(entry.command.take(180))
                            Body(t.commandHistoryDetail(formatCommandHistoryTime(entry.timestampMillis), entry.summary))
                        }
                        SmallButton(
                            t.rerunCommand(),
                            enabled = !loading && canRerunCommand(entry.command),
                        ) {
                            copiedCommand = null
                            onRerun(entry)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton(
                            if (copiedCommand == entry.command) t.copied() else t.copyCommandOutput(),
                            enabled = !loading && entry.output.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            onCopy(entry)
                            copiedCommand = entry.command
                        }
                        SmallButton(
                            t.shareCommandOutput(),
                            enabled = !loading && entry.output.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            onShare(entry)
                        }
                        SmallButton(
                            if (deletePending) t.cancel() else t.deleteCommand(),
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            pendingDeleteCommand = if (deletePending) null else entry.command
                        }
                    }
                    if (deletePending) {
                        Spacer(Modifier.height(6.dp))
                        Body(t.confirmDeleteCommand())
                        Spacer(Modifier.height(6.dp))
                        SmallButton(t.confirm(), enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            onDelete(entry)
                            pendingDeleteCommand = null
                            copiedCommand = null
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

fun formatCommandHistoryTime(timestampMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(timestampMillis))
