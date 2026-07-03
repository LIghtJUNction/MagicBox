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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun BackupCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var safetyCode by remember { mutableStateOf("") }
    var restorePayload by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun backupArgs(action: String): String {
        val code = safetyCode.trim()
        return if (code.isBlank()) "backup $action" else "backup $action ${shellQuote(code)}"
    }

    fun exportBackup() {
        loading = true
        copied = false
        confirmRestore = false
        scope.launch {
            result = runMagicNet(backupArgs("export"))
            loading = false
        }
    }

    fun restoreBackup() {
        val payload = restorePayload.trim()
        if (payload.isBlank()) {
            result = CliResult(false, "$MAGICNET_CLI backup restore-file", t.backupPayloadMissing())
            confirmRestore = false
            return
        }
        loading = true
        copied = false
        confirmRestore = false
        scope.launch {
            val file = File(context.cacheDir, "magicnet-restore-backup.txt")
            runCatching { file.writeText(payload) }
                .onFailure { result = CliResult(false, "$MAGICNET_CLI backup restore-file", it.message ?: "write failed") }
                .onSuccess {
                    val code = safetyCode.trim().ifBlank { "-" }
                    result = runMagicNet("backup restore-file ${shellQuote(code)} ${shellQuote(file.absolutePath)}")
                    file.delete()
                }
            loading = false
        }
    }

    val output = result?.output.orEmpty()
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.backup())
                Body(t.backupSummary())
            }
            StatusPill(if (result?.success == true) t.ok else t.idle)
        }
        Spacer(Modifier.height(8.dp))
        TextInput(
            value = safetyCode,
            placeholder = t.backupSafetyCodePlaceholder(),
            onValueChange = {
                safetyCode = it
                copied = false
            },
        )
        Spacer(Modifier.height(8.dp))
        MultiLineTextInput(
            value = restorePayload,
            placeholder = t.backupPayloadPlaceholder(),
            onValueChange = {
                restorePayload = it
                confirmRestore = false
            },
        )
        if (output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(output.take(700))
        }
        if (confirmRestore) {
            Spacer(Modifier.height(8.dp))
            Body(t.confirmRestoreBackup())
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.exportBackup(), enabled = !loading, modifier = Modifier.weight(1f), onClick = ::exportBackup)
            SmallButton(
                if (confirmRestore) t.confirm() else t.restoreBackup(),
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) {
                if (confirmRestore) restoreBackup() else confirmRestore = true
            }
        }
        if (confirmRestore) {
            Spacer(Modifier.height(8.dp))
            SmallButton(t.cancel(), enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                confirmRestore = false
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox backup", output)
                copied = true
            }
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                sharePlainText(context, "MagicBox backup", output)
            }
        }
    }
}
