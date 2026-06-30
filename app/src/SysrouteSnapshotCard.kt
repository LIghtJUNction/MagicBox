package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
fun SysrouteSnapshotCard() {
    val t = LocalUiText.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<CliResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    fun runSysroute(action: String) {
        loading = true
        copied = false
        scope.launch {
            result = runMagicNet("sysroute $action")
            loading = false
        }
    }

    val output = result?.let { formatToolResult(it) }.orEmpty()
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.systemRoutes())
                Body(result?.summary ?: t.systemRoutesSummary())
            }
            StatusPill(
                when (result?.success) {
                    true -> t.ok
                    false -> t.fail
                    null -> if (loading) t.busy else t.idle
                },
            )
        }
        if (!result?.output.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Mono(result?.output.orEmpty().take(1100))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.routeSnapshot(), enabled = !loading, modifier = Modifier.weight(1f)) {
                runSysroute("snapshot")
            }
            SmallButton(t.routeList(), enabled = !loading, modifier = Modifier.weight(1f)) {
                runSysroute("list")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(if (copied) t.copied() else t.copyReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox system routes", output)
                copied = true
            }
            SmallButton(t.shareReport(), enabled = output.isNotBlank(), modifier = Modifier.weight(1f)) {
                sharePlainText(context, "MagicBox system routes", output)
            }
        }
    }
}

fun UiText.systemRoutes(): String = if (this === UiText.zh) "系统路由" else "System routes"

fun UiText.systemRoutesSummary(): String =
    if (this === UiText.zh) {
        "读取真实 ip rule 和全表路由，用于排查流量路径。"
    } else {
        "Read real ip rules and all routing tables for traffic-path debugging."
    }

fun UiText.routeSnapshot(): String = if (this === UiText.zh) "快照" else "Snapshot"

fun UiText.routeList(): String = if (this === UiText.zh) "列表" else "List"
