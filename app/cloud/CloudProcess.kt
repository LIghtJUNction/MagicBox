package com.github.lightjunction.magicbox

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal data class ProcessResult(val code: Int, val stdout: String, val stderr: String, val complete: Boolean) {
    val success get() = complete && code == 0
}
private val ROOT_EXECUTABLES = listOf("/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/sbin/su", "su")

/** Bounded stdout/stderr, concurrent stdin, and one deadline for the child process. */
internal fun boundedProcess(args: List<String>, input: ByteArray = byteArrayOf(), seconds: Long = 12,
    limit: Int = CLOUD_OUTPUT_LIMIT): ProcessResult {
    val child = try { ProcessBuilder(args).start() } catch (_: Exception) {
        return ProcessResult(-1, "", "unavailable", false)
    }
    val truncated = AtomicBoolean(false)
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    fun reader(stream: java.io.InputStream, buffer: ByteArrayOutputStream) = thread(isDaemon = true, name = "cloud-pipe") {
        runCatching {
            stream.use {
                val chunk = ByteArray(4096)
                while (true) {
                    val n = it.read(chunk)
                    if (n < 0) break
                    val accepted = n.coerceAtMost((limit - buffer.size()).coerceAtLeast(0))
                    buffer.write(chunk, 0, accepted)
                    if (accepted < n) truncated.set(true)
                }
            }
        }.onFailure { truncated.set(true) }
    }
    val out = reader(child.inputStream, stdout)
    val err = reader(child.errorStream, stderr)
    val writer = thread(isDaemon = true, name = "cloud-input") {
        runCatching { child.outputStream.use { it.write(input) } }.onFailure { truncated.set(true) }
    }
    val finished = child.waitFor(seconds, TimeUnit.SECONDS)
    if (!finished) {
        child.destroy()
        if (!child.waitFor(500, TimeUnit.MILLISECONDS)) child.destroyForcibly()
    }
    writer.join(500); out.join(500); err.join(500)
    val complete = finished && !truncated.get() && !out.isAlive && !err.isAlive && !writer.isAlive
    if (!complete) {
        runCatching { child.outputStream.close() }; runCatching { child.inputStream.close() }; runCatching { child.errorStream.close() }
    }
    return ProcessResult(if (finished) child.exitValue() else -1, if (complete) stdout.toString("UTF-8") else "", if (complete) stderr.toString("UTF-8") else "incomplete", complete)
}

internal class RootTransport {
    private var prefix: List<String>? = null
    val authorized get() = prefix != null
    fun authorize(): Boolean {
        prefix = null
        for (candidate in ROOT_EXECUTABLES) {
            val standard = listOf(candidate, "-c")
            val namespace = listOf(candidate, "-M", "-c")
            var result = boundedProcess(namespace + "id -u", seconds = 35, limit = 8192)
            var selected = namespace
            if (result.stderr.contains("option", true) || result.stdout.contains("option", true)) {
                selected = standard
                result = boundedProcess(standard + "id -u", seconds = 35, limit = 8192)
            }
            if (result.success && result.stdout.trim() == "0") { prefix = selected; return true }
            if (result.stderr != "unavailable") return false
        }
        return false
    }
    fun run(command: String, input: ByteArray = byteArrayOf(), seconds: Long = 12): ProcessResult {
        val args = prefix ?: throw CloudFailure("请先授予 Root 权限。")
        return boundedProcess(args + command, input, seconds)
    }
    fun spawn(command: String): Process {
        val args = prefix ?: throw CloudFailure("请先授予 Root 权限。")
        return ProcessBuilder(args + command).start()
    }
}
