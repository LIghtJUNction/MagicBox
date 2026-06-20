package com.github.lightjunction.magicbox

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val label: String,
    val packageName: String,
)

suspend fun loadInstalledApps(
    context: Context,
    query: String,
): List<InstalledApp> =
    withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val cleanQuery = query.trim()
        pm.installedApplications()
            .asSequence()
            .filter { app -> app.packageName != context.packageName }
            .filter { app -> pm.getLaunchIntentForPackage(app.packageName) != null || app.isUserApp() }
            .map { app ->
                InstalledApp(
                    label = app.loadLabel(pm)?.toString()?.ifBlank { app.packageName } ?: app.packageName,
                    packageName = app.packageName,
                )
            }
            .filter { app ->
                cleanQuery.isBlank() ||
                    app.label.contains(cleanQuery, ignoreCase = true) ||
                    app.packageName.contains(cleanQuery, ignoreCase = true)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .take(180)
            .toList()
    }

suspend fun loadInstalledAppsFromPackages(
    context: Context,
    output: String,
    query: String,
): List<InstalledApp> =
    withContext(Dispatchers.Default) {
        packageOutputToInstalledApps(context, output, query)
    }

fun packageOutputToInstalledApps(
    context: Context,
    output: String,
    query: String,
): List<InstalledApp> {
    val pm = context.packageManager
    val cleanQuery = query.trim()
    return output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && isSafePackage(it) }
        .map { pkg -> installedAppForPackage(pm, pkg) }
        .filter { app ->
            cleanQuery.isBlank() ||
                app.label.contains(cleanQuery, ignoreCase = true) ||
                app.packageName.contains(cleanQuery, ignoreCase = true)
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        .take(180)
        .toList()
}

private fun installedAppForPackage(
    pm: PackageManager,
    packageName: String,
): InstalledApp {
    val label =
        runCatching { pm.applicationInfo(packageName).loadLabel(pm)?.toString() }
            .getOrNull()
            ?.ifBlank { packageName }
            ?: packageName
    return InstalledApp(label = label, packageName = packageName)
}

private fun PackageManager.installedApplications(): List<ApplicationInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getInstalledApplications(0)
    }

private fun PackageManager.applicationInfo(packageName: String): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getApplicationInfo(packageName, 0)
    }

private fun ApplicationInfo.isUserApp(): Boolean =
    flags and ApplicationInfo.FLAG_SYSTEM == 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

@Composable
fun InstalledAppRow(
    app: InstalledApp,
    detail: String,
    primaryAction: String,
    secondaryAction: String,
    enabled: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    val colors = LocalMagicTheme.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.control)
                .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageIcon(app.packageName)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = app.label,
                style = TextStyle(color = MagicPalette.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = app.packageName,
                style = TextStyle(color = MagicPalette.muted, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = detail,
                style = TextStyle(color = MagicPalette.muted.copy(alpha = 0.78f), fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallButton(primaryAction, enabled = enabled, onClick = onPrimary)
        Spacer(Modifier.width(6.dp))
        SmallButton(secondaryAction, enabled = enabled, onClick = onSecondary)
    }
}

@Composable
private fun PackageIcon(packageName: String) {
    val context = LocalContext.current
    val colors = LocalMagicTheme.current
    AndroidView(
        modifier =
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.controlSelected),
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setPadding(2, 2, 2, 2)
            }
        },
        update = { view ->
            val drawable =
                runCatching { context.packageManager.getApplicationIcon(packageName) }
                    .getOrElse { context.applicationInfo.loadIcon(context.packageManager) }
            view.setImageDrawable(drawable)
        },
    )
}
