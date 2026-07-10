package com.github.lightjunction.magicbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

fun launchInstalledApp(
    context: Context,
    packageName: String,
): Boolean {
    val intent =
        context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
    return runCatching { context.startActivity(intent) }
        .recoverCatching { error ->
            if (error is ActivityNotFoundException) Unit else throw error
        }
        .isSuccess
}
