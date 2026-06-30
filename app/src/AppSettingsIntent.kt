package com.github.lightjunction.magicbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun openAppDetails(
    context: Context,
    packageName: String,
) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .recoverCatching { error ->
            if (error is ActivityNotFoundException) Unit else throw error
        }
}
