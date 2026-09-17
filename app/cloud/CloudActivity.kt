package com.github.lightjunction.magicbox

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

class CloudActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var frame: android.widget.FrameLayout
    private val filesWorker = Executors.newSingleThreadExecutor()
    private var alive = true
    private val origin = "https://app.magicbox.invalid"
    private val allowedFiles = mapOf("/index.html" to "text/html", "/cloud.js" to "application/javascript", "/cloud.css" to "text/css")
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) filesWorker.execute {
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readLimited(CLOUD_INPUT_LIMIT), Charsets.UTF_8) }
                    ?: throw CloudFailure("无法读取文件。")
                CloudRuntime.request(applicationContext, "import", JSONObject().put("text", text)) { result ->
                    runOnUiThread {
                        if (result.optBoolean("ok")) evaluate("CloudNative.update(${result.getJSONObject("data").getJSONObject("state")})")
                        val message = if (result.optBoolean("ok")) "文件已导入。" else result.optString("message")
                        evaluate("CloudNative.notice(${JSONObject.quote(message)})")
                    }
                }
            } catch (_: Exception) { runOnUiThread { evaluate("CloudNative.notice('文件无法读取或超过 2 MiB。')") } }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 247, 242)
        window.navigationBarColor = android.graphics.Color.rgb(247, 247, 242)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        web = WebView(this)
        web.setBackgroundColor(android.graphics.Color.rgb(247, 247, 242))
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                val uri = request?.url
                val mime = allowedFiles[uri?.path]
                if (uri?.scheme == "https" && uri.host == "app.magicbox.invalid" && uri.port == -1 &&
                    uri.userInfo == null && uri.query == null && request?.method == "GET" && mime != null) {
                    return WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"), assets.open("cloud${uri.path}"))
                }
                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
            }
        }
        web.addJavascriptInterface(Bridge(), "MagicBridge")
        frame = android.widget.FrameLayout(this).apply { addView(web) }
        frame.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else { view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom) }
            insets
        }
        setContentView(frame)
        web.loadUrl("$origin/index.html")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.CloudUI ? CloudUI.back() : false") { result -> if (result != "true") finish() }
            }
        })
    }

    private inner class Bridge {
        @JavascriptInterface fun request(id: String, action: String, payload: String) {
            if (!id.matches(Regex("[0-9]{1,12}")) || payload.length > CLOUD_INPUT_LIMIT + 4096) return
            val parsed = runCatching { cloudJson(payload) }.getOrNull() ?: return
            if (action in setOf("file", "about", "advanced", "appearance")) {
                runOnUiThread {
                    if (!alive || isFinishing) return@runOnUiThread
                    try {
                        when (action) {
                            "appearance" -> {
                                val dark = parsed.optString("theme") == "dark"
                                val color = if (dark) android.graphics.Color.rgb(20,25,29) else android.graphics.Color.rgb(247,247,242)
                                frame.setBackgroundColor(color); web.setBackgroundColor(color)
                                window.statusBarColor = color; window.navigationBarColor = color
                                window.decorView.systemUiVisibility = if (dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            }
                            "file" -> picker.launch(arrayOf("text/*", "application/json", "application/octet-stream", "application/yaml"))
                            "advanced" -> {
                                requireCloud(!BuildConfig.STANDALONE, "通用版不控制外部模块。")
                                startActivity(Intent(this@CloudActivity, MainActivity::class.java))
                            }
                            "about" -> AlertDialog.Builder(this@CloudActivity).setTitle("MagicBox ${BuildConfig.VERSION_NAME}")
                                .setMessage("Token 云集合\n\nMagicBox：AGPL-3.0\nMagicNet：MIT\nsing-box：GPL-3.0-or-later\nProxylink：GPL-3.0\n\n通用版固定 MagicNet a94682e4 的内核源码与 44929c09 的 Proxylink。组件完整许可随 APK 附带。")
                                .setPositiveButton("源代码") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MAGICBOX_RELEASES_URL.removeSuffix("/releases")))) }
                                .setNegativeButton("关闭", null).show()
                        }
                        reply(id, JSONObject().put("ok", true).put("data", JSONObject()))
                    } catch (_: Exception) { reply(id, JSONObject().put("ok", false).put("message", "无法打开此功能。")) }
                }
                return
            }
            if (action == "start" && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && BuildConfig.STANDALONE) {
                runOnUiThread { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 27) }
            }
            CloudRuntime.request(applicationContext, action, parsed) { result -> runOnUiThread { reply(id, result) } }
        }
    }
    private fun reply(id: String, result: JSONObject) = evaluate("CloudNative.result(${JSONObject.quote(id)},$result)")
    private fun evaluate(code: String) {
        if (alive && !isFinishing) web.evaluateJavascript(code.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"), null)
    }
    override fun onResume() { super.onResume(); if (::web.isInitialized) { web.onResume(); evaluate("window.CloudUI && CloudUI.resume()") } }
    override fun onPause() { if (::web.isInitialized) { evaluate("window.CloudUI && CloudUI.suspend()"); web.onPause() }; super.onPause() }
    override fun onDestroy() {
        alive = false; filesWorker.shutdownNow()
        web.removeJavascriptInterface("MagicBridge"); web.stopLoading(); web.destroy()
        super.onDestroy()
    }
}
