package com.github.lightjunction.magicbox

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.github.lightjunction.magicbox.reboot.CloudActivity
import java.io.File
import org.junit.Rule
import org.junit.Test

class CloudVisualTest {
    @get:Rule val compose = createAndroidComposeRule<CloudActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun realNativeScreensAreCapturedWithoutFakeNetworkData() {
        capture("${BuildConfig.FLAVOR}-home")
        compose.onNodeWithText("订阅", useUnmergedTree = true).performClick()
        capture("${BuildConfig.FLAVOR}-subscriptions")
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        capture("${BuildConfig.FLAVOR}-settings")
        compose.onNodeWithText("动态效果 · Token 云集合").performClick()
        capture("${BuildConfig.FLAVOR}-reduced-motion")
    }
}
