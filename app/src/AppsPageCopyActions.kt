package com.github.lightjunction.magicbox

import android.content.Context

fun copyVisibleAppPolicyPackages(
    context: Context,
    packages: List<String>,
): Boolean {
    if (packages.isEmpty()) return false
    copyPlainText(context, "MagicBox app policy", packages.joinToString("\n"))
    return true
}

fun shareVisibleAppPolicyPackages(
    context: Context,
    packages: List<String>,
) {
    if (packages.isEmpty()) return
    sharePlainText(context, "MagicBox app policy", packages.joinToString("\n"))
}

fun copyInstalledApps(
    context: Context,
    apps: List<InstalledApp>,
): Boolean {
    if (apps.isEmpty()) return false
    copyPlainText(context, "MagicBox installed apps", formatInstalledPackages(apps))
    return true
}

fun shareInstalledApps(
    context: Context,
    apps: List<InstalledApp>,
) {
    if (apps.isEmpty()) return
    sharePlainText(context, "MagicBox installed apps", formatInstalledPackages(apps))
}

