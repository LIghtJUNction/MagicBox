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
fun AppPackageInspectorCard(
    packageName: String,
    installedPackageNames: Set<String>?,
    summary: AppSummary?,
    loading: Boolean,
    onAddProxy: () -> Unit,
    onAddBypass: () -> Unit,
) {
    val t = LocalUiText.current
    val context = LocalContext.current
    val clean = packageName.trim()
    val installed = installedPackageNames?.contains(clean)
    val inProxy = summary?.proxy?.contains(clean) == true
    val inBypass = summary?.bypass?.contains(clean) == true
    val valid = isSafePackage(clean)
    Card(padding = PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Label(t.packageInspector())
                Body(packageInspectorSummary(t, clean, valid, installed, inProxy, inBypass))
            }
            StatusPill(
                when {
                    clean.isBlank() -> t.idle
                    !valid -> t.fail
                    inProxy || inBypass -> t.ready
                    installed == true -> t.ok
                    installed == false -> t.warn
                    else -> t.idle
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.appTarget(AppTarget.Proxy), enabled = !loading && valid && !inProxy, modifier = Modifier.weight(1f), onClick = onAddProxy)
            SmallButton(t.appTarget(AppTarget.Bypass), enabled = !loading && valid && !inBypass, modifier = Modifier.weight(1f), onClick = onAddBypass)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(t.copyReport(), enabled = valid, modifier = Modifier.weight(1f)) {
                copyPlainText(context, "MagicBox package", clean)
            }
            SmallButton(t.appInfo(), enabled = valid, modifier = Modifier.weight(1f)) {
                openAppDetails(context, clean)
            }
            SmallButton(t.openApp(), enabled = valid && installed == true, modifier = Modifier.weight(1f)) {
                launchInstalledApp(context, clean)
            }
        }
    }
}

private fun packageInspectorSummary(
    t: UiText,
    packageName: String,
    valid: Boolean,
    installed: Boolean?,
    inProxy: Boolean,
    inBypass: Boolean,
): String =
    when {
        packageName.isBlank() -> t.packageInspectorEmpty()
        !valid -> t.invalidPackage(packageName)
        inProxy -> t.packageInspectorInPolicy(t.appTarget(AppTarget.Proxy))
        inBypass -> t.packageInspectorInPolicy(t.appTarget(AppTarget.Bypass))
        installed == true -> t.packageInspectorInstalled()
        installed == false -> t.packageInspectorNotInstalled()
        else -> t.packageInspectorUnknown()
    }
