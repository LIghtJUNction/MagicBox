package com.github.lightjunction.magicbox.reboot

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class CommandResult(val code: Int, val text: String) {
    fun requireSuccess(message: String): String {
        if (code != 0) throw BoxFailure(message)
        return text
    }
}

internal object EngineIO {
    suspend fun command(args: List<String>, input: ByteArray? = null, seconds: Long = 15): CommandResult =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            val bytes = ByteArrayOutputStream()
            val reader = Thread {
                runCatching {
                    process.inputStream.use { stream ->
                        val chunk = ByteArray(4096)
                        while (true) {
                            val count = stream.read(chunk)
                            if (count < 0) break
                            synchronized(bytes) {
                                val remaining = 128 * 1024 - bytes.size()
                                if (remaining > 0) bytes.write(chunk, 0, minOf(count, remaining))
                            }
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            val writer = Thread {
                runCatching { process.outputStream.use { if (input != null) it.write(input) } }
            }.apply { isDaemon = true; start() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            try {
                while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    currentCoroutineContext().ensureActive()
                    if (System.nanoTime() > deadline) throw BoxFailure("操作超时，请检查 root 授权或运行状态。")
                }
                reader.join(1000)
                val text = synchronized(bytes) { bytes.toString("UTF-8") }
                CommandResult(process.exitValue(), text)
            } finally {
                if (process.isAlive) process.destroyForcibly()
                runCatching { process.outputStream.close() }
                runCatching { process.inputStream.close() }
                writer.join(300)
                reader.join(300)
            }
        }

    suspend fun root(script: String, input: ByteArray? = null, seconds: Long = 15): CommandResult {
        for (su in listOf("/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/sbin/su", "su")) {
            try { return command(listOf(su, "-c", script), input, seconds) }
            catch (_: IOException) { /* Try only a missing executable, never retry a denied grant. */ }
        }
        throw BoxFailure("未找到 root。请在 KernelSU、Magisk 或 APatch 中授权此版本。")
    }
    suspend fun rootArgs(vararg args: String, seconds: Long = 15): CommandResult =
        root(args.joinToString(" ", transform = InputPolicy::quote), seconds = seconds)

    fun readBounded(stream: InputStream): String {
        val bytes = stream.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                if (out.size() + n > InputPolicy.MAX_BYTES) throw BoxFailure("订阅超过 8 MiB，未导入。")
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        val decoder = Charsets.UTF_8.newDecoder()
        val text = try { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
        catch (_: java.nio.charset.CharacterCodingException) { throw BoxFailure("订阅文件不是有效 UTF-8 文本。") }
        return InputPolicy.validate(text)
    }

    suspend fun subscription(source: String): String = withContext(Dispatchers.IO) {
        val text = InputPolicy.validate(source)
        if (!text.startsWith("https://", true) || text.any(Char::isWhitespace)) return@withContext text
        var uri = try { URI(text) } catch (_: Exception) { throw BoxFailure("订阅地址无效。") }
        repeat(4) { index ->
            if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.userInfo != null) {
                throw BoxFailure("订阅下载仅允许不含用户信息的 HTTPS 地址。")
            }
            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "MagicBox/0.2 sing-box")
                val status = connection.responseCode
                if (status in 300..399) {
                    if (index == 3) throw BoxFailure("订阅重定向次数过多。")
                    val next = connection.getHeaderField("Location") ?: throw BoxFailure("订阅重定向无效。")
                    uri = uri.resolve(next)
                } else {
                    if (status !in 200..299) throw BoxFailure("订阅服务器返回 HTTP $status。")
                    return@withContext readBounded(connection.inputStream)
                }
            } finally { connection.disconnect() }
        }
        throw BoxFailure("订阅下载未完成。")
    }
}
