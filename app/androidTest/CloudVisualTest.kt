package com.github.lightjunction.magicbox

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250) // Let SurfaceFlinger present the actual requested frame.
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun realNativeScreensAreCapturedWithoutFakeNetworkData() {
        compose.onNodeWithText("MagicBox").assertIsDisplayed()
        SystemClock.sleep(800) // Wait for the system splash to leave the surface.
        capture("${BuildConfig.FLAVOR}-home")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Token 字符云").performClick()
        compose.mainClock.advanceTimeBy(180)
        capture("${BuildConfig.FLAVOR}-token-split")
        compose.mainClock.advanceTimeBy(900)
        capture("${BuildConfig.FLAVOR}-token-reformed")
        compose.onNodeWithContentDescription("Token 字符云").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(32)
        capture("${BuildConfig.FLAVOR}-token-drag")
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("订阅", useUnmergedTree = true).performClick()
        capture("${BuildConfig.FLAVOR}-subscriptions")
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        capture("${BuildConfig.FLAVOR}-settings")
        compose.onNodeWithText("动态效果 · Token 云集合").performClick()
        capture("${BuildConfig.FLAVOR}-reduced-motion")
    }
}
