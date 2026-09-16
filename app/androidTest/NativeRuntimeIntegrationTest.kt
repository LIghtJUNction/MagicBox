package com.github.lightjunction.magicbox

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.github.lightjunction.magicbox.reboot.*
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/** Exercises the real installed ELF binaries and Android foreground-service path. */
class NativeRuntimeIntegrationTest {
    @get:Rule val activity = ActivityScenarioRule(CloudActivity::class.java)

    @Test fun standaloneRoutesRealTrafficAndCleansUp() = runBlocking {
        assumeFalse(BuildConfig.UI_ONLY)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime: RuntimePort = PlatformRuntime.get(context)
        val origin = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val proof = "magicbox-android-native-routing-proof"
        val responder = Thread {
            runCatching {
                origin.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${proof.length}\r\nConnection: close\r\n\r\n$proof").toByteArray())
                }
            }
        }.apply { isDaemon = true; start() }
        val upstreamPort = ServerSocket(0).use { it.localPort }
        val core = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")
        val config = File.createTempFile("upstream-", ".json", context.cacheDir)
        config.writeText("""{"log":{"level":"error"},"inbounds":[{"type":"mixed","listen":"127.0.0.1","listen_port":$upstreamPort}],"outbounds":[{"type":"direct","tag":"direct"}],"route":{"final":"direct"}}""")
        var upstream: Process? = null
        try {
            assertTrue("installed core executable", core.canExecute())
            upstream = ProcessBuilder(core.absolutePath, "run", "-c", config.absolutePath).redirectErrorStream(true).start()
            val child = upstream
            Thread {
                runCatching { child.inputStream.use { stream -> val buffer = ByteArray(4096); while (stream.read(buffer) >= 0) {} } }
            }.apply { isDaemon = true; start() }
            withTimeout(10000) {
                while (!runCatching { Socket("127.0.0.1", upstreamPort).use { true } }.getOrDefault(false)) delay(100)
            }
            runtime.importText("socks://127.0.0.1:$upstreamPort#offline-android-fixture")
            val names = runtime.state.value.nodes
            assertEquals(1, names.size)
            try { runtime.importText("unknown://credential-not-for-logs"); fail("invalid format accepted") }
            catch (_: BoxFailure) { assertEquals(names, runtime.state.value.nodes) }
            runtime.start(ProxyMode.SYSTEM)
            withTimeout(15000) {
                while (!runtime.state.value.running) {
                    assertNotEquals(runtime.state.value.detail, Phase.ERROR, runtime.state.value.phase)
                    delay(100)
                }
            }
            Socket("127.0.0.1", 2080).use { socket ->
                socket.soTimeout = 5000
                val target = "127.0.0.1:${origin.localPort}"
                socket.getOutputStream().write("GET http://$target/proof HTTP/1.1\r\nHost: $target\r\nConnection: close\r\n\r\n".toByteArray())
                val response = socket.getInputStream().bufferedReader().readText()
                assertTrue(response, response.contains("200 OK") && response.contains(proof))
            }
            runtime.stop()
            withTimeout(10000) { while (runtime.state.value.phase != Phase.IDLE) delay(100) }
            assertFalse("local listener leaked", runCatching { Socket("127.0.0.1", 2080).use { true } }.getOrDefault(false))
            File(context.getExternalFilesDir(null), "native-routing-proof.txt").writeText(
                "PASS: installed Android parser, atomic failed-import preservation, foreground core start, two-hop real local proxy request, authenticated readiness, stop and listener cleanup.\nROOT TUN/EBPF NOT TESTED.\n"
            )
        } finally {
            runCatching { runtime.stop() }
            upstream?.let { child -> child.destroy(); if (!child.waitFor(4, TimeUnit.SECONDS)) child.destroyForcibly() }
            origin.close()
            responder.join(1000)
            config.delete()
        }
    }
}
