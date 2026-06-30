package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionCloseControls(
    loading: Boolean,
    topCloseCount: String,
    topClosePreview: ConnectionClosePreview,
    confirmCloseTop: Boolean,
    query: String,
    matchedConnections: List<ConnectionTarget>,
    confirmCloseMatched: Boolean,
    canCloseAll: Boolean,
    confirmCloseAll: Boolean,
    activeClosePlan: Pair<ConnectionClosePlanKind, String>?,
    copiedClosePlan: Boolean,
    onTopCloseCountChange: (String) -> Unit,
    onRequestTopClose: () -> Unit,
    onConfirmTopClose: () -> Unit,
    onRequestMatchedClose: () -> Unit,
    onConfirmMatchedClose: () -> Unit,
    onRequestCloseAll: () -> Unit,
    onConfirmCloseAll: () -> Unit,
    onCopyClosePlan: (ConnectionClosePlanKind, String) -> Unit,
    onShareClosePlan: (ConnectionClosePlanKind, String) -> Unit,
) {
    val t = LocalUiText.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(
            topCloseCount,
            t.closeTopCountPlaceholder(),
            { onTopCloseCountChange(it.filter(Char::isDigit).take(1)) },
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
    if (topClosePreview.candidates.isNotEmpty()) {
        Body(t.connectionTopClosePreview(topClosePreview.candidates.size, formatBytes(topClosePreview.totalBytes)))
        if (confirmCloseTop) {
            topClosePreview.candidates.take(3).forEach { candidate ->
                Mono(candidate.label.take(80))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton(
            if (confirmCloseTop) t.confirm() else t.closeTopConnections(),
            enabled = !loading && topClosePreview.candidates.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) {
            if (confirmCloseTop) onConfirmTopClose() else onRequestTopClose()
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallButton(
            if (confirmCloseMatched) t.confirm() else t.closeMatchedConnections(),
            enabled = !loading && query.isNotBlank() && matchedConnections.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) {
            if (confirmCloseMatched) onConfirmMatchedClose() else onRequestMatchedClose()
        }
        SmallButton(
            if (confirmCloseAll) t.confirm() else t.closeConns,
            enabled = !loading && canCloseAll,
            modifier = Modifier.weight(1f),
        ) {
            if (confirmCloseAll) onConfirmCloseAll() else onRequestCloseAll()
        }
    }
    activeClosePlan?.let { (kind, plan) ->
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (copiedClosePlan) t.copied() else t.copyClosePlan(),
                enabled = plan.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                onCopyClosePlan(kind, plan)
            }
            SmallButton(
                t.shareClosePlan(),
                enabled = plan.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                onShareClosePlan(kind, plan)
            }
        }
    }
}

fun UiText.closeTopConnections(): String = if (this === UiText.zh) "关闭高流量" else "Close top"

fun UiText.closeTopCountPlaceholder(): String = if (this === UiText.zh) "数量 1-8" else "Count 1-8"

fun UiText.closedTopConnections(count: Int): String =
    if (this === UiText.zh) "已关闭 $count 条高流量连接。" else "Closed $count high-traffic connections."

fun UiText.connectionTopClosePreview(
    closing: Int,
    bytes: String,
): String =
    if (this === UiText.zh) {
        "将关闭当前流量最高的 $closing 条连接，当前合计 $bytes。"
    } else {
        "Will close the current top $closing connections by traffic, totaling $bytes."
    }

fun UiText.closeMatchedConnections(): String = if (this === UiText.zh) "关闭匹配" else "Close matches"

fun UiText.copyClosePlan(): String = if (this === UiText.zh) "复制关闭计划" else "Copy close plan"

fun UiText.shareClosePlan(): String = if (this === UiText.zh) "分享关闭计划" else "Share close plan"

fun UiText.connectionClosePreview(
    closing: Int,
    matched: Int,
    bytes: String,
): String =
    if (this === UiText.zh) {
        "将关闭按流量排序的 $closing/$matched 条匹配连接，当前合计 $bytes。"
    } else {
        "Will close $closing/$matched matching connections by traffic, currently totaling $bytes."
    }
