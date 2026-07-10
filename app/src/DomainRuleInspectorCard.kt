package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DomainRuleInspectorCard(
    domain: String,
    summary: RouteSummary?,
    loading: Boolean,
    onAddToBucket: (RuleBucket) -> Unit,
) {
    val t = LocalUiText.current
    val context = LocalContext.current
    val clean = domain.trim().lowercase()
    val valid = isSafeDomain(clean)
    val matches =
        if (valid && summary != null) {
            RuleBucket.entries.filter { bucket -> clean in summary.forBucket(bucket) }
        } else {
            emptyList()
        }
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.domainInspector())
                Body(domainInspectorSummary(t, clean, valid, summary, matches))
            }
            StatusPill(
                when {
                    clean.isBlank() -> t.idle
                    !valid -> t.fail
                    matches.isNotEmpty() -> t.ready
                    summary != null -> t.ok
                    else -> t.idle
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleBucket.entries.take(2).forEach { bucket ->
                SmallButton(t.ruleBucket(bucket), enabled = !loading && valid && bucket !in matches, modifier = Modifier.weight(1f)) {
                    onAddToBucket(bucket)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleBucket.entries.drop(2).forEach { bucket ->
                SmallButton(t.ruleBucket(bucket), enabled = !loading && valid && bucket !in matches, modifier = Modifier.weight(1f)) {
                    onAddToBucket(bucket)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SmallButton(t.copyReport(), enabled = valid, modifier = Modifier.fillMaxWidth()) {
            copyPlainText(context, "MagicBox domain", clean)
        }
    }
}

private fun domainInspectorSummary(
    t: UiText,
    domain: String,
    valid: Boolean,
    summary: RouteSummary?,
    matches: List<RuleBucket>,
): String =
    when {
        domain.isBlank() -> t.domainInspectorEmpty()
        !valid -> t.invalidDomain(domain)
        matches.isNotEmpty() -> t.domainInspectorInBuckets(matches.joinToString(", ") { t.ruleBucket(it) })
        summary != null -> t.domainInspectorNotListed()
        else -> t.domainInspectorUnknown()
    }
