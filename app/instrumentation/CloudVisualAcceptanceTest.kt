package com.github.lightjunction.magicbox

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Verify the installed app is visible, not just a WebView behind a system dialog. */
@RunWith(AndroidJUnit4::class)
class CloudVisualAcceptanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun js(scenario: ActivityScenario<CloudActivity>, code: String): String {
        val done = CountDownLatch(1)
        var result = ""
        scenario.onActivity { activity ->
            val view = web(activity.findViewById(android.R.id.content)) ?: error("Missing local WebView")
            view.evaluateJavascript(code) { result = it; done.countDown() }
        }
        assertTrue("JavaScript callback timed out", done.await(10, TimeUnit.SECONDS))
        return result
    }
    @Test fun foregroundScreenshotsHaveExplicitThemeAndNoSystemDialog() {
        val directory = File(context.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        val manifest = JSONArray()
        ActivityScenario.launch(CloudActivity::class.java).use { scenario ->
            var loaded = false
            for (attempt in 0 until 40) {
                if (js(scenario, "typeof CloudUI === 'object'") == "true") { loaded = true; break }
                Thread.sleep(250)
            }
            assertTrue("Packaged UI failed to load", loaded)
            for (theme in listOf("light", "dark")) {
                js(scenario, "if(document.documentElement.dataset.theme!==${JSONObject.quote(theme)})document.getElementById('appearance').click()")
                for (page in listOf("home", "subscriptions", "settings")) {
                    js(scenario, "document.querySelector('[data-page=$page]').click();window.scrollTo(0,0)")
                    Thread.sleep(1100)
                    assertEquals(JSONObject.quote(theme), js(scenario, "document.documentElement.dataset.theme"))
                    assertEquals("false", js(scenario, "document.documentElement.scrollWidth > innerWidth"))
                    assertEquals("0", js(scenario, "CloudUI.metrics().active"))
                    var owner: String? = null
                    for (attempt in 0 until 10) {
                        val active = instrumentation.uiAutomation.rootInActiveWindow
                        owner = active?.packageName?.toString()
                        active?.recycle()
                        if (owner == context.packageName) break
                        Thread.sleep(200)
                    }
                    assertEquals("A dialog or another app obscures the screenshot", context.packageName, owner)
                    val image = instrumentation.uiAutomation.takeScreenshot()
                    assertNotNull("Screenshot capture failed", image)
                    val file = File(directory, "qa-$page-$theme.png")
                    try { file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                    finally { image.recycle() }
                    val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
                    manifest.put(JSONObject().put("file", file.name).put("sha256", sha).put("theme", theme)
                        .put("page", page).put("foreground_package", owner))
                }
            }
            val stats = JSONObject().put("process_pss_kib", android.os.Debug.getPss())
                .put("canvas", org.json.JSONTokener(js(scenario, "CloudUI.metrics()")).nextValue())
            File(directory, "foreground-performance.json").writeText(stats.toString(2))
            js(scenario, "if(document.documentElement.dataset.theme!=='light')document.getElementById('appearance').click();document.querySelector('[data-page=home]').click()")
        }
        File(directory, "qa-manifest.json").writeText(JSONObject().put("package", context.packageName)
            .put("version", BuildConfig.VERSION_NAME).put("screenshots", manifest).toString(2))
    }
}
