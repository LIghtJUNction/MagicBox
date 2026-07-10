package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RuntimeRuleSetsCard(
    result: CliResult?,
    summary: RuntimeRuleSummary?,
    loading: Boolean,
    copied: Boolean,
    onReload: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    val output = result?.output.orEmpty().trim()
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.runtimeRuleSets)
                Body(
                    when {
                        result == null -> t.notRunYet
                        result.success && summary != null -> t.runtimeRuleSummary(summary.total)
                        else -> result.summary
                    },
                )
            }
            StatusPill(
                when {
                    result == null -> t.idle
                    !result.success -> t.fail
                    summary?.total == 0 -> t.warn
                    else -> t.ok
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            result?.success == false && output.isNotBlank() -> Mono(output.take(900))
            summary == null -> Body(t.notRunYet)
            summary.ruleSets.isEmpty() -> Body(t.noRuntimeRuleSets())
            else -> {
                summary.ruleSets.take(16).forEach { ruleSet ->
                    DomainRow(ruleSet)
                    Spacer(Modifier.height(5.dp))
                }
                if (summary.total > 16) Body(t.more(summary.total - 16))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
            SmallButton(
                if (copied) t.copied() else t.copyReport(),
                enabled = output.isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = onCopy,
            )
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f), onClick = onShare)
        }
    }
}

fun UiText.noRuntimeRuleSets(): String =
    if (this === UiText.zh) "当前没有检测到运行时规则集。" else "No runtime rule sets detected."
