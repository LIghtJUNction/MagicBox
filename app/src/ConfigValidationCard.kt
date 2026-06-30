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
import kotlinx.coroutines.launch

@Composable
fun ConfigValidationCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var confirmSync by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    var confirmRepair by remember { mutableStateOf(false) }
    var verification by remember { mutableStateOf<ConfigWriteVerification?>(null) }

    fun runConfigTool(
        args: String,
        verifyAfterWrite: Boolean = false,
    ) {
        loading = true
        copied = false
        confirmSync = false
        confirmApply = false
        confirmRepair = false
        verification = null
        scope.launch {
            result = runMagicNetLong(args)
            if (verifyAfterWrite && result?.success == true) {
                verification = verifyConfigWriteRuntime()
            }
            loading = false
        }
    }

    val output =
        listOfNotNull(
            result?.let { formatToolResult(it) },
            verification?.let { formatConfigWriteVerification(it) },
        ).joinToString("\n\n")
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.configValidation())
                Body(result?.summary ?: t.configValidationSummary())
            }
            StatusPill(if (result?.success == true) t.ok else t.idle)
        }
        result?.takeIf { it.output.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Mono(it.output.take(700))
        }
        verification?.let {
            Spacer(Modifier.height(8.dp))
            Label(t.configWriteVerification())
            Body(t.configWriteVerificationSummary(it))
            Spacer(Modifier.height(6.dp))
            Mono(formatConfigWriteVerification(it).take(700))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.validateConfig(), enabled = !loading, modifier = Modifier.weight(1f)) {
                runConfigTool("config-editor validate sing-box")
            }
            SmallButton(
                if (confirmSync) t.confirm() else t.syncTemplate(),
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) {
                if (confirmSync) runConfigTool("config-editor sync-template sing-box", verifyAfterWrite = true) else confirmSync = true
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (confirmApply) t.confirm() else t.applyConfigNow(),
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) {
                if (confirmApply) runConfigTool("config apply", verifyAfterWrite = true) else confirmApply = true
            }
            SmallButton(
                if (confirmRepair) t.confirm() else t.repairRuntime(),
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) {
                if (confirmRepair) runConfigTool("repair", verifyAfterWrite = true) else confirmRepair = true
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox config validation", output)
                copied = true
            }
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                sharePlainText(context, "MagicBox config validation", output)
            }
        }
    }
}
