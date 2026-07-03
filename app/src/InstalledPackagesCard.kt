package com.github.lightjunction.magicbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InstalledPackagesCard(
    query: String,
    visibleApps: List<InstalledApp>,
    activePackages: Set<String>,
    packagesResult: CliResult?,
    loading: Boolean,
    copied: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAddProxy: (String) -> Unit,
    onAddBypass: (String) -> Unit,
    onOpenInfo: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalUiText.current
    Card(padding = PaddingValues(10.dp)) {
        Label(t.installedPackages)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(query, t.searchPackage, onQueryChange, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SmallButton(t.search, enabled = !loading, onClick = onSearch)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (copied) t.copied() else t.copyInstalledPackages(),
                enabled = visibleApps.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = onCopy,
            )
            SmallButton(
                t.shareInstalledPackages(),
                enabled = visibleApps.isNotEmpty(),
                modifier = Modifier.weight(1f),
                onClick = onShare,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (visibleApps.isEmpty()) {
            Body(packagesResult?.summary ?: t.searchPackage)
        } else {
            visibleApps.take(80).forEach { app ->
                InstalledAppRow(
                    app = app,
                    detail = if (app.packageName in activePackages) t.alreadyInPolicy else t.installedPackage,
                    primaryAction = t.appTarget(AppTarget.Proxy),
                    secondaryAction = t.appTarget(AppTarget.Bypass),
                    tertiaryAction = t.appInfo(),
                    quaternaryAction = t.openApp(),
                    enabled = !loading,
                    onPrimary = { onAddProxy(app.packageName) },
                    onSecondary = { onAddBypass(app.packageName) },
                    onTertiary = { onOpenInfo(app.packageName) },
                    onQuaternary = { onOpenApp(app.packageName) },
                )
                Spacer(Modifier.height(6.dp))
            }
            if (visibleApps.size > 80) Body(t.more(visibleApps.size - 80))
        }
    }
}

fun formatInstalledPackages(apps: List<InstalledApp>): String =
    apps.joinToString("\n") { app -> "${app.packageName}\t${app.label}" }

fun UiText.copyInstalledPackages(): String = if (this === UiText.zh) "复制可见应用" else "Copy visible apps"

fun UiText.shareInstalledPackages(): String = if (this === UiText.zh) "分享可见应用" else "Share visible apps"
