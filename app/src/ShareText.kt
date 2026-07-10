package com.github.lightjunction.magicbox

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

fun sharePlainText(
    context: Context,
    title: String,
    text: String,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(sendIntent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
        .recoverCatching { error ->
            if (error is ActivityNotFoundException) Unit else throw error
        }
}
