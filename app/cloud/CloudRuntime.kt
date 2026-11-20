package com.github.lightjunction.magicbox

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object CloudRuntime {
    private val worker = ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, ArrayBlockingQueue(4),
        { work -> Thread(work, "magicbox-runtime").apply { isDaemon = true } })
    private var backend: CloudBackend? = null
    @Volatile private var generation = 0L
    private fun backend(context: Context): CloudBackend = backend ?: (if (BuildConfig.STANDALONE)
        StandaloneBackend(context.applicationContext) else ModuleBackend(context.applicationContext)).also { backend = it }

    fun request(context: Context, action: String, payload: JSONObject, done: (JSONObject) -> Unit) {
        try {
            worker.execute {
                val service = Intent(context, CloudService::class.java)
                val result = try {
                    val runtime = backend(context)
                    var message = ""
                    when (action) {
                        "status" -> Unit
                        "authorize" -> runtime.authorize()
                        "start" -> {
                            requireCloud(!runtime.status().optBoolean("running", false), "代理已经在运行。")
                            generation++
                            if (BuildConfig.STANDALONE) context.startForegroundService(service.putExtra("generation", generation))
                            try { runtime.start() } catch (error: Exception) {
                                if (BuildConfig.STANDALONE) context.stopService(service)
                                throw error
                            }
                        }
                        "stop" -> {
                            runtime.stop()
                            if (BuildConfig.STANDALONE) context.stopService(service)
                        }
                        "mode" -> runtime.mode(payload.optString("mode"))
                        "import" -> runtime.importText(payload.optString("text"))
                        "refreshSubscription" -> runtime.refresh()
                        "select" -> runtime.select(payload.optString("tag"))
                        "diagnose" -> { message = runtime.diagnose() }
                        else -> throw CloudFailure("不支持的操作。")
                    }
                    val state = runtime.status()
                    if (action == "status") JSONObject().put("ok", true).put("data", state)
                    else JSONObject().put("ok", true).put("data", JSONObject().put("state", state)
                        .put("message", message.ifBlank { if (action == "import") state.optString("message") else "" }))
                } catch (error: Exception) {
                    JSONObject().put("ok", false).put("message", (error as? CloudFailure)?.message ?: "本地操作失败。请检查权限、组件与网络，原订阅不会被静默覆盖。")
                }
                done(result)
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            done(JSONObject().put("ok", false).put("message", "有操作尚未完成，请稍后重试。"))
        }
    }
    fun serviceDestroyed(context: Context, session: Long) {
        if (!BuildConfig.STANDALONE || session == 0L) return
        try {
            worker.execute {
                if (generation == session) runCatching { backend(context).stop() }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { /* Native stdin lifetime remains the final guard. */ }
    }
}
