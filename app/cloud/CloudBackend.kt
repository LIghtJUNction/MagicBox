package com.github.lightjunction.magicbox

import android.content.Context
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL

internal interface CloudBackend {
    val root: RootTransport
    fun status(): JSONObject
    fun authorize() { requireCloud(root.authorize(), "Root 未授权。请在 KernelSU / Magisk 中允许当前版本的应用。") }
    fun start()
    fun stop()
    fun mode(value: String)
    fun importText(value: String)
    fun refresh()
    fun select(tag: String)
    fun diagnose(): String
}

internal fun baseState(context: Context, root: Boolean, mode: String): JSONObject = JSONObject()
    .put("edition", if (BuildConfig.STANDALONE) "universal" else "ui")
    .put("packageName", context.packageName).put("version", BuildConfig.VERSION_NAME)
    .put("root", root).put("mode", mode).put("nodes", JSONArray()).put("nodeCount", 0)
    .put("powerSave", (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode)

internal fun modeEntry(id: String, available: Boolean, reason: String = "") = JSONObject()
    .put("id", id).put("available", available).put("reason", reason)

internal fun validateInput(value: String): ByteArray {
    val bytes = value.trim().toByteArray(Charsets.UTF_8)
    requireCloud(bytes.isNotEmpty() && bytes.size <= CLOUD_INPUT_LIMIT && !value.contains('\u0000'),
        "输入为空、含无效字符或超过 2 MiB。")
    return bytes
}

/** HTTPS only; no downgrade, userinfo or implicit use of another local proxy. */
internal fun downloadSubscription(value: String): ByteArray {
    var uri = runCatching { URI(value) }.getOrNull() ?: throw CloudFailure("订阅地址无效。")
    repeat(4) {
        requireCloud(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.fragment == null,
            "订阅地址必须是有效的 HTTPS 链接，且不能包含地址栏认证信息。")
        val connection = uri.toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "MagicBox/${BuildConfig.VERSION_NAME} sing-box")
            connection.setRequestProperty("Accept-Encoding", "identity")
            when (connection.responseCode) {
                in 200..299 -> return connection.inputStream.use { it.readLimited(CLOUD_INPUT_LIMIT) }
                301, 302, 303, 307, 308 -> {
                    val target = connection.getHeaderField("Location") ?: throw CloudFailure("订阅重定向无效。")
                    uri = uri.resolve(target)
                }
                else -> throw CloudFailure("订阅服务器返回 HTTP ${connection.responseCode}，原配置保持不变。")
            }
        } finally { connection.disconnect() }
    }
    throw CloudFailure("订阅重定向过多。")
}

internal fun verifyInternet(port: Int): Boolean {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
    val connection = URL("https://www.gstatic.com/generate_204").openConnection(proxy) as HttpURLConnection
    return try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8000; connection.readTimeout = 8000
        connection.responseCode == 204
    } catch (_: Exception) { false } finally { connection.disconnect() }
}
