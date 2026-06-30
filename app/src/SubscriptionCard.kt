package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SubscriptionCard(
    result: CliResult?,
    draft: String,
    loading: Boolean,
    copied: Boolean,
    saved: Boolean,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onUpdateSingBox: () -> Unit,
    onUpdateAll: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    val summary = result?.takeIf { it.success }?.let { parseSubscriptionSummary(it.output) }
    Card(padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.subscription)
                Body(subscriptionSummaryText(t, result, summary))
            }
            StatusPill(
                when {
                    result == null -> t.idle
                    result.success && summary?.configuredCount.orZero() > 0 -> t.ok
                    result.success && summary?.primaryConfigured == true -> t.ok
                    result.success -> t.warn
                    else -> t.fail
                },
            )
        }
        if (summary != null) {
            Spacer(Modifier.height(8.dp))
            subscriptionSourceLines(t, summary).take(4).forEach { line ->
                Body(line)
            }
        } else if (result?.success == false && result.output.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(redactSupportText(result.output).take(700))
        }
        Spacer(Modifier.height(8.dp))
        Label(t.subscriptionSources())
        Spacer(Modifier.height(6.dp))
        MultiLineTextInput(
            value = draft,
            placeholder = t.subscriptionInputPlaceholder(),
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (saved) t.done else t.saveSubscriptions(),
                enabled = !loading && draft.isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = onSave,
            )
            SmallButton(t.reload, enabled = !loading, modifier = Modifier.weight(1f), onClick = onReload)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.updateSub, enabled = !loading, modifier = Modifier.weight(1f), onClick = onUpdateSingBox)
            SmallButton(t.updateAll, enabled = !loading, modifier = Modifier.weight(1f), onClick = onUpdateAll)
        }
        if (result?.output?.isNotBlank() == true) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(
                    if (copied) t.copied() else t.copyReport(),
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                    onClick = onCopy,
                )
                SmallButton(
                    t.shareReport(),
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                    onClick = onShare,
                )
            }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun subscriptionSummaryText(
    t: UiText,
    result: CliResult?,
    summary: SubscriptionSummary?,
): String =
    when {
        result == null -> t.loadingSubscription
        !result.success -> result.summary
        summary == null -> result.summary
        summary.configuredCount > 0 -> t.subscriptionConfigured(summary.configuredCount)
        summary.primaryConfigured -> t.subscriptionConfigured(1)
        else -> t.subscriptionEmpty()
    }

private fun subscriptionSourceLines(
    t: UiText,
    summary: SubscriptionSummary,
): List<String> =
    if (summary.sources.isEmpty() && summary.primaryConfigured) {
        listOf(t.subscriptionPrimaryConfigured())
    } else {
        summary.sources.map { source ->
            t.subscriptionSourceLine(source.index, source.configured)
        }
    }
