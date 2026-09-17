package com.github.lightjunction.magicbox

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.InputDevice
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class CloudDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun call(action: String, data: JSONObject = JSONObject()): JSONObject {
        val latch = CountDownLatch(1)
        var result = JSONObject()
        CloudRuntime.request(context, action, data) { result = it; latch.countDown() }
        assertTrue("Native request timed out: $action", latch.await(90, TimeUnit.SECONDS))
        return result
    }
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWeb(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun js(scenario: ActivityScenario<CloudActivity>, code: String): String {
        val latch = CountDownLatch(1); var result = ""
        scenario.onActivity { activity ->
            val web = findWeb(activity.findViewById(android.R.id.content)) ?: error("WebView missing")
            web.evaluateJavascript(code) { result = it; latch.countDown() }
        }
        assertTrue("JavaScript evaluation timed out", latch.await(10, TimeUnit.SECONDS))
        return result
    }
    private fun ready(scenario: ActivityScenario<CloudActivity>) {
        for (attempt in 0 until 40) {
            if (js(scenario, "typeof window.CloudUI === 'object'") == "true") return
            Thread.sleep(250)
        }
        fail("Local UI did not load")
    }
    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "evidence").apply { mkdirs() }
        val image = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Missing device screenshot", image)
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun localUiAndMotionLifecycle() {
        ActivityScenario.launch(CloudActivity::class.java).use { scenario ->
            ready(scenario); Thread.sleep(800)
            assertEquals("0", js(scenario, "document.querySelectorAll('iframe').length"))
            assertEquals("false", js(scenario, "document.documentElement.scrollWidth > innerWidth"))
            screenshot("home-light")
            js(scenario, "document.getElementById('appearance').click()")
            Thread.sleep(750); screenshot("home-dark")
            js(scenario, "document.querySelector('[data-page= subscriptions]').click()")
            Thread.sleep(750); screenshot("subscriptions-dark")
            js(scenario, "document.querySelector('[data-page= settings]').click()")
            Thread.sleep(750); screenshot("settings-dark")
            js(scenario, "document.getElementById('appearance').click(); document.querySelector('[data-page=home]').click()")
            Thread.sleep(750)
            js(scenario, "document.querySelector('[data-mode=tun]').click()")
            Thread.sleep(150); screenshot("token-split")
            Thread.sleep(850)
            assertEquals("0", js(scenario, "CloudUI.metrics().active"))
            val before = js(scenario, "CloudUI.metrics().frames")
            Thread.sleep(1500)
            assertEquals("Idle UI must not keep animating", before, js(scenario, "CloudUI.metrics().frames"))
            val directory = File(context.getExternalFilesDir(null), "evidence").apply { mkdirs() }
            File(directory,"canvas-metrics.json").writeText(js(scenario,"JSON.stringify(CloudUI.metrics())"))
        }
    }
    @Test fun standaloneProxyMovesRealSocketDataAndStops() {
        assumeTrue(BuildConfig.STANDALONE)
        ActivityScenario.launch(CloudActivity::class.java).use { scenario ->
            ready(scenario)
            val diagnostics = File(context.getExternalFilesDir(null), "evidence").apply { mkdirs() }
            // Fixed non-secret fixtures only; do not add raw runtime diagnostics to production UI.
            val dir = context.applicationInfo.nativeLibraryDir
            File(diagnostics, "components.txt").writeText(
                listOf("libsingbox.so", "libproxylink.so", "libmbprobe.so").joinToString("\n") {
                    val file = File(dir, it)
                    "$it present=${file.isFile} executable=${file.canExecute()} bytes=${file.length()}"
                }
            )
            val converted = boundedProcess(listOf(File(dir, "libproxylink.so").path),
                "http://127.0.0.1:18080#Fixture".toByteArray(), seconds = 15)
            File(diagnostics, "converter-fixture.txt").writeText(converted.toString())
            if (converted.success) {
                val document = safeNodeDocument(cloudJson(converted.stdout))
                val candidate = File(context.cacheDir, "fixture-check.json")
                candidate.writeText(makeCoreConfig(document, "system", "Fixture", "test-only", "/sys/fs/cgroup").toString())
                val checked = boundedProcess(listOf(File(dir, "libsingbox.so").path, "check", "-c", candidate.path), seconds = 15)
                File(diagnostics, "core-check-fixture.txt").writeText(checked.toString())
                candidate.delete()
            }
            ServerSocket(0).use { upstream ->
                val serverDone = CountDownLatch(1)
                val serverError = AtomicReference<Throwable?>(null)
                thread(isDaemon = true, name = "fixture-upstream") {
                    try {
                        upstream.accept().use { socket ->
                            socket.soTimeout = 20000
                            val input = socket.getInputStream().bufferedReader()
                            val request = input.readLine()
                            check(request.startsWith("CONNECT "))
                            while (!input.readLine().isNullOrEmpty()) { }
                            socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                            socket.getOutputStream().flush()
                            check(input.readLine().startsWith("GET "))
                            while (!input.readLine().isNullOrEmpty()) { }
                            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 12\r\nConnection: close\r\n\r\nmagicbox-e2e".toByteArray())
                            socket.getOutputStream().flush()
                        }
                    } catch (error: Throwable) {
                        if (!upstream.isClosed) serverError.set(error)
                    } finally { serverDone.countDown() }
                }
                val imported = call("import",JSONObject().put("text","http://127.0.0.1:${upstream.localPort}#Fixture"))
                assertTrue(imported.toString(), imported.optBoolean("ok"))
                val start = call("start")
                assertTrue(start.toString(), start.optBoolean("ok"))
                try {
                    Socket("127.0.0.1", CLOUD_PROXY_PORT).use { proxy ->
                        proxy.soTimeout = 15000
                        proxy.getOutputStream().write("GET http://example.invalid/acceptance HTTP/1.1\r\nHost: example.invalid\r\nConnection: close\r\n\r\n".toByteArray())
                        proxy.getOutputStream().flush()
                        val response = proxy.getInputStream().bufferedReader().readText()
                        assertTrue("Proxy failed to relay the fixture response", response.contains("magicbox-e2e"))
                    }
                    assertTrue(serverDone.await(5,TimeUnit.SECONDS))
                    assertNull("Upstream fixture failed: ${serverError.get()}", serverError.get())
                    val rejected = call("import",JSONObject().put("text","unknown://not-supported"))
                    assertFalse(rejected.optBoolean("ok"))
                    assertTrue(call("status").getJSONObject("data").optBoolean("running"))
                } finally {
                    assertTrue(call("stop").optBoolean("ok"))
                }
                assertFalse(call("status").getJSONObject("data").optBoolean("running"))
                val invalid = call("import",JSONObject().put("text","unknown://not-supported"))
                assertFalse(invalid.optBoolean("ok"))
                assertEquals(1,call("status").getJSONObject("data").optInt("nodeCount"))
            }
        }
    }
    @Test fun uiEditionCannotBecomeStandalone() {
        assumeTrue(!BuildConfig.STANDALONE)
        val status=call("status").getJSONObject("data")
        assertEquals("ui",status.getString("edition"))
        assertEquals("blocked",status.getString("phase"))
        assertFalse(call("start").optBoolean("ok"))
        assertFalse(File(context.applicationInfo.nativeLibraryDir,"libsingbox.so").exists())
    }
    @Test fun nodePaginationAndSearchDoNotDropLaterNodes() {
        ActivityScenario.launch(CloudActivity::class.java).use { scenario ->
            ready(scenario)
            js(scenario, "CloudUI.suspend()")
            try {
                js(scenario, """CloudNative.update({nodes:Array.from({length:505},(_,i)=>({tag:'node-'+String(i+1).padStart(4,'0'),type:'socks'})),nodeCount:505,selected:''}); document.querySelector('[data-page=subscriptions]').click()""")
                assertEquals("100", js(scenario, "document.querySelectorAll('.node-row').length"))
                js(scenario, "for(let i=0;i<5;i++)document.getElementById('node-next').click()")
                assertEquals("5", js(scenario, "document.querySelectorAll('.node-row').length"))
                assertEquals("true", js(scenario, "document.getElementById('node-list').textContent.includes('node-0505')"))
                js(scenario, "const search=document.getElementById('node-search');search.value='node-0505';search.dispatchEvent(new Event('input'))")
                assertEquals("1", js(scenario, "document.querySelectorAll('.node-row').length"))
                assertEquals("true", js(scenario, "document.getElementById('node-list').textContent.includes('node-0505')"))
                js(scenario, "document.getElementById('node-search').value='';document.getElementById('node-search').dispatchEvent(new Event('input'));CloudNative.update({nodes:[{tag:'<img src=x onerror=alert(1)>',type:'socks'}],nodeCount:1})")
                assertEquals("0", js(scenario, "document.querySelectorAll('#node-list img').length"))
                assertEquals("true", js(scenario, "document.getElementById('node-list').textContent.includes('<img')"))
            } finally { js(scenario, "CloudUI.resume()") }
        }
    }
    @Test fun actualDragReassemblesAndReducedMotionStopsDrawing() {
        ActivityScenario.launch(CloudActivity::class.java).use { scenario ->
            ready(scenario); Thread.sleep(500)
            js(scenario, "document.querySelector('[data-page=home]').click(); if(document.getElementById('reduce-motion').getAttribute('aria-checked')==='true')document.getElementById('reduce-motion').click()")
            var left = 0; var top = 0; var width = 0
            scenario.onActivity { activity ->
                val view = findWeb(activity.findViewById(android.R.id.content))!!
                val position = IntArray(2); view.getLocationOnScreen(position)
                left = position[0]; top = position[1]; width = view.width
            }
            val dimensions = JSONObject(js(scenario, "JSON.stringify({w:innerWidth,y:document.getElementById('cloud').getBoundingClientRect().top+90})").let { org.json.JSONTokener(it).nextValue() as String })
            val scale = width / dimensions.getDouble("w")
            val x = left + width * .4f; val y = top + (dimensions.getDouble("y") * scale).toFloat()
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, nextX: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, nextX, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, x)
            try {
                for (step in 1..8) { Thread.sleep(35); touch(MotionEvent.ACTION_MOVE, x + (step * 8 * scale).toFloat()) }
                Thread.sleep(120)
                assertTrue("Drag did not activate token particles", js(scenario, "CloudUI.metrics().active").toInt() > 0)
                screenshot("token-drag")
            } finally { touch(MotionEvent.ACTION_UP, x + (64 * scale).toFloat()) }
            Thread.sleep(900)
            assertEquals("0", js(scenario, "CloudUI.metrics().active"))
            assertEquals("0", js(scenario, "document.querySelectorAll('.is-dispersed').length"))
            js(scenario, "document.getElementById('reduce-motion').click()")
            val before = js(scenario, "CloudUI.metrics().frames")
            js(scenario, "document.querySelector('[data-mode=tun]').click()")
            Thread.sleep(800)
            assertEquals(before, js(scenario, "CloudUI.metrics().frames"))
            assertEquals("0", js(scenario, "CloudUI.metrics().active"))
            js(scenario, "document.getElementById('reduce-motion').click()")
        }
    }
}
