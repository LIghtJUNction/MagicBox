package com.github.lightjunction.magicbox.reboot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

object PlatformRuntime {
    @Volatile private var instance: NativeRuntime? = null
    fun get(context: Context): NativeRuntime = instance ?: synchronized(this) {
        instance ?: NativeRuntime(context.applicationContext).also { instance = it }
    }
}

class NativeRuntime internal constructor(private val context: Context) : RuntimePort {
    private val store = ProfileStore(context)
    private val current = MutableStateFlow(BoxState(nodes = store.names(), selected = store.selected(), detail = if (store.names().isEmpty()) "添加订阅，开始连接。" else "内置内核已准备好。"))
    override val state = current.asStateFlow()
    private val mutex = Mutex()
    private var process: Process? = null
    private var rootMode = false
    private var secret = ""
    private val pidFile = File(store.directory, "core.pid")
    private fun binary(name: String): String {
        val file = File(context.applicationInfo.nativeLibraryDir, name)
        if (!file.isFile || !file.canExecute()) throw BoxFailure("此安装包缺少兼容的内置组件，请使用完整通用版。")
        return file.absolutePath
    }
    override suspend fun start(mode: ProxyMode) {
        if (current.value.running || current.value.transitioning) return
        if (store.read() == null) throw BoxFailure("请先在订阅页导入节点。")
        binary("libsingbox.so")
        current.value = current.value.copy(phase = Phase.STARTING, mode = mode, detail = "正在验证配置与运行条件。")
        try { context.startForegroundService(Intent(context, ProxyRuntimeService::class.java).setAction("connect").putExtra("mode", mode.name)) }
        catch (error: Exception) { current.value = current.value.copy(phase = Phase.ERROR, detail = "系统未允许启动前台代理服务。"); throw error }
    }
    override suspend fun stop() {
        context.startService(Intent(context, ProxyRuntimeService::class.java).setAction("disconnect"))
    }
    internal suspend fun connect(mode: ProxyMode) = mutex.withLock {
        try {
            disconnectLocked()
            current.value = current.value.copy(phase = Phase.STARTING, mode = mode, detail = "正在建立连接，不会提前显示成功。")
            rootMode = mode != ProxyMode.SYSTEM
            secret = UUID.randomUUID().toString()
            val core = binary("libsingbox.so")
            if (rootMode) {
                if (EngineIO.rootArgs("id", "-u").requireSuccess("请在 root 管理器中授权 MagicBox。").trim() != "0") throw BoxFailure("没有获得 root 权限。")
                EngineIO.root("test ! -e /data/adb/modules/MagicNet/module.prop || test -e /data/adb/modules/MagicNet/disable")
                    .requireSuccess("通用版 root 模式不能与已启用的 MagicNet 同时接管。请停用模块，或使用 UI 版。")
                if (mode == ProxyMode.EBPF) {
                    val probe = EngineIO.rootArgs(core, "tools", "ebpf", "status", "--mode", "local", "--network", "tcp,udp", "--json", seconds = 15)
                    val data = runCatching { JSONObject(probe.text) }.getOrNull()
                    if (probe.code != 0 || data?.optJSONObject("summary")?.optInt("required_failures", -1) != 0 || data.optString("result") != "supported") throw BoxFailure("设备未通过 eBPF 能力检查；没有回退成 TUN。")
                }
            }
            val profile = store.read() ?: throw BoxFailure("请先导入订阅。")
            val config = File(store.directory, "active.json")
            config.writeText(store.config(profile, mode, secret).toString())
            if (rootMode) EngineIO.rootArgs(core, "check", "-c", config.absolutePath).requireSuccess("内核未接受该模式或订阅配置。")
            else EngineIO.command(listOf(core, "check", "-c", config.absolutePath)).requireSuccess("内核未接受订阅配置。")
            pidFile.writeText("")
            process = if (rootMode) {
                // The root guardian watches the exact app process start time. If Android kills
                // the app, the core receives TERM and can restore its routes/BPF attachments.
                val script = """
                    umask 077
                    stamp() { line=${'$'}(cat /proc/${'$'}1/stat 2>/dev/null) || return 1; rest=${'$'}{line##*) }; set -- ${'$'}rest; shift 19; printf '%s' "${'$'}1"; }
                    owner=${android.os.Process.myPid()}
                    original=${'$'}(stamp "${'$'}owner") || exit 1
                    child=''
                    cleanup() { [ -z "${'$'}child" ] || { kill -TERM "${'$'}child" 2>/dev/null || :; wait "${'$'}child" 2>/dev/null || :; }; }
                    trap cleanup EXIT HUP INT TERM
                    ${InputPolicy.quote(core)} run -c ${InputPolicy.quote(config.absolutePath)} >/dev/null 2>&1 &
                    child=${'$'}!
                    printf '%s' "${'$'}child" > ${InputPolicy.quote(pidFile.absolutePath)}
                    while kill -0 "${'$'}child" 2>/dev/null && [ "${'$'}(stamp "${'$'}owner")" = "${'$'}original" ]; do sleep 2; done
                """.trimIndent()
                val su = listOf("/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/sbin/su").firstOrNull { File(it).canExecute() } ?: "su"
                ProcessBuilder(su, "-c", script).redirectErrorStream(true).start()
            } else ProcessBuilder(core, "run", "-c", config.absolutePath).redirectErrorStream(true).start()
            val owned = process!!
            Thread { runCatching { owned.inputStream.use { it.copyTo(java.io.OutputStream.nullOutputStream()) } } }.apply { isDaemon = true; start() }
            repeat(40) {
                if (!owned.isAlive) throw BoxFailure("代理内核未能启动；可能存在端口、内核或权限冲突。")
                if (ready(mode)) {
                    current.value = current.value.copy(phase = Phase.RUNNING, mode = mode, detail = if (mode == ProxyMode.SYSTEM) "HTTP / SOCKS5 · 127.0.0.1:2080。尚需在 Wi-Fi 或应用内配置代理。" else if (mode == ProxyMode.TUN) "已确认内核 API 与 magicbox0 接口就绪。" else "已确认本内核持有并挂载全部本地 cgroup 钩子；不包含热点接管。")
                    return@withLock
                }
                delay(150)
            }
            throw BoxFailure("未取得数据面就绪证据，已停止本次启动。")
        } catch (error: Exception) {
            withContext(NonCancellable) { runCatching { disconnectLocked() } }
            current.value = current.value.copy(phase = Phase.ERROR, detail = (error as? BoxFailure)?.message ?: "启动失败；请检查运行环境。")
            if (error is CancellationException) throw error
        }
    }
    private suspend fun ready(mode: ProxyMode): Boolean {
        if (process?.isAlive != true || secret.isBlank()) return false
        val api = withContext(Dispatchers.IO) {
            val connection = URL("http://127.0.0.1:20790/version").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 800; connection.readTimeout = 800
                connection.setRequestProperty("Authorization", "Bearer $secret")
                connection.responseCode == 200 && connection.inputStream.use { JSONObject(it.bufferedReader().readText()).optString("version").isNotBlank() }
            } catch (_: Exception) { false } finally { connection.disconnect() }
        }
        if (!api) return false
        return when (mode) {
            ProxyMode.SYSTEM -> true
            ProxyMode.TUN -> EngineIO.root("test -d /sys/class/net/magicbox0", seconds = 3).code == 0
            ProxyMode.EBPF -> {
                val pid = runCatching { pidFile.readText().trim().toInt() }.getOrNull() ?: return false
                if (pid <= 1) return false
                EngineIO.rootArgs(binary("libebpfcheck.so"), pid.toString(), seconds = 3).code == 0
            }
        }
    }
    override suspend fun refresh(requestPermission: Boolean) = mutex.withLock {
        if (current.value.running && !ready(current.value.mode)) {
            runCatching { disconnectLocked() }
            current.value = current.value.copy(phase = Phase.ERROR, detail = "连接已失去就绪状态，不会继续显示已连接。")
        }
    }
    internal suspend fun disconnect() = mutex.withLock { disconnectLocked() }
    private suspend fun disconnectLocked() {
        val child = process
        if (child != null) {
            current.value = current.value.copy(phase = Phase.STOPPING, detail = "正在停止内核并恢复网络。")
            if (rootMode) {
                val pid = runCatching { pidFile.readText().trim().toInt() }.getOrNull()
                if (pid != null && pid > 1) {
                    val core = InputPolicy.quote(binary("libsingbox.so"))
                    EngineIO.root("if [ \"\$(readlink /proc/$pid/exe)\" = $core ]; then kill -TERM $pid; fi", seconds = 5)
                        .requireSuccess("无法停止本次 root 内核，请通过 root 管理器检查。")
                }
            } else child.destroy()
            withContext(Dispatchers.IO) { child.waitFor(4, TimeUnit.SECONDS) }
            if (child.isAlive) throw BoxFailure("内核仍未退出，请从设置页重试停止；不要启动另一套透明代理。")
        }
        process = null
        pidFile.delete()
        File(store.directory, "active.json").delete()
        secret = ""
        current.value = current.value.copy(phase = Phase.IDLE, detail = "已断开。你的订阅仍保留在设备上。")
    }
    override suspend fun importText(text: String) = mutex.withLock {
        if (current.value.running || current.value.transitioning || process?.isAlive == true) throw BoxFailure("请先断开连接再更换订阅。")
        val raw = EngineIO.subscription(text)
        val source = File.createTempFile("subscription-", ".txt", store.directory)
        val output = File(store.directory, "parsed-${UUID.randomUUID()}.json")
        val validation = File.createTempFile("check-", ".json", store.directory)
        try {
            source.writeText(raw)
            EngineIO.command(listOf(binary("libproxylink.so"), source.absolutePath, output.absolutePath), seconds = 30)
                .requireSuccess("未识别到可转换节点。支持 Clash/Mihomo、sing-box、Xray、SIP008、URI 与 Base64；未知格式不会被当作成功。")
            if (!output.isFile || output.length() > InputPolicy.MAX_BYTES) throw BoxFailure("解析结果过大或不完整。")
            val profile = store.normalized(JSONObject(output.readText()))
            validation.writeText(store.config(profile, ProxyMode.SYSTEM, "validation-only").toString())
            EngineIO.command(listOf(binary("libsingbox.so"), "check", "-c", validation.absolutePath), seconds = 15)
                .requireSuccess("订阅可解析，但当前内核不能执行；旧配置未修改。")
            store.save(profile)
            current.value = current.value.copy(nodes = store.names(), selected = store.selected(), phase = Phase.IDLE, detail = "订阅已验证并保存在本机。")
        } finally { source.delete(); output.delete(); validation.delete() }
    }
    override suspend fun select(tag: String) = mutex.withLock {
        if (current.value.running || current.value.transitioning) throw BoxFailure("请先断开再切换节点。")
        store.select(tag)
        current.value = current.value.copy(selected = tag)
    }
}

class ProxyRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var runtime: NativeRuntime
    override fun onCreate() {
        super.onCreate()
        runtime = PlatformRuntime.get(applicationContext)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("proxy", "代理运行状态", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(this, 0, Intent(this, CloudActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ProxyRuntimeService::class.java).setAction("disconnect"), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "proxy").setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("MagicBox · 代理服务").setContentText("查看真实连接状态，或停止代理").setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).setOngoing(true).build()
        startForeground(2080, notification)
        scope.launch {
            if (intent?.action == "connect") {
                val mode = runCatching { ProxyMode.valueOf(intent.getStringExtra("mode").orEmpty()) }.getOrDefault(ProxyMode.SYSTEM)
                runtime.connect(mode)
                if (!runtime.state.value.running) stopSelf(startId)
            } else {
                try { runtime.disconnect(); stopSelf(startId) } catch (_: Exception) { /* Keep foreground stop action available. */ }
            }
        }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { scope.launch { runCatching { runtime.disconnect() }; stopSelf() } }
    override fun onDestroy() {
        scope.cancel()
        // Root guardian independently handles process death. Normal service destruction
        // performs bounded cleanup without blocking Android's main thread.
        CoroutineScope(Dispatchers.IO).launch { runCatching { runtime.disconnect() } }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
