package com.github.lightjunction.magicbox

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class StandaloneBackend(private val context: Context) : CloudBackend {
    override val root = RootTransport()
    private val directory = File(context.filesDir, "runtime").apply { mkdirs() }
    private val profileFile = AtomicFile(File(directory, "profile.json"))
    private val nativeDir = context.applicationInfo.nativeLibraryDir
    private val core = File(nativeDir, "libsingbox.so")
    private val converter = File(nativeDir, "libproxylink.so")
    private val helper = File(nativeDir, "libmbprobe.so")
    private val config = File(directory, "runtime.json")
    private var profile = runCatching { profileFile.openRead().use { cloudJson(String(it.readLimited(CLOUD_OUTPUT_LIMIT), Charsets.UTF_8)) } }
        .getOrElse { JSONObject().put("mode", "system") }
    private var child: Process? = null
    private var corePid = 0
    private var phase = "idle"
    private var message = ""
    private var secret = ""
    private var cgroup = "/sys/fs/cgroup"
    private var ebpf = false
    private var tun = false
    private var lastEvidence = 0L
    private var dataReady = false
    private val selected get() = profile.optString("selected")
    private val currentMode get() = profile.optString("mode", "system")
    private fun componentsReady() = core.canExecute() && converter.canExecute() && helper.canExecute()

    override fun authorize() {
        tun = false; ebpf = false
        super.authorize()
        tun = root.run("test -c /dev/net/tun").success
        for (candidate in listOf("/sys/fs/cgroup", "/dev/cg2_bpf")) {
            if (root.run("test -f ${shellQuote("$candidate/cgroup.controllers")}").success) { cgroup = candidate; break }
        }
        ebpf = probeEbpf()
        message = "Root 已授权"
    }

    private fun probeEbpf(): Boolean {
        if (!root.authorized || !core.canExecute()) return false
        val result = root.run("${shellQuote(core.path)} tools ebpf status --mode local --network tcp,udp --cgroup ${shellQuote(cgroup)} --json", seconds = 15)
        val report = runCatching { cloudJson(result.stdout) }.getOrNull()
        return result.success && report?.optString("result") == "supported" &&
            report.optJSONObject("summary")?.optInt("required_failures", -1) == 0
    }

    override fun status(): JSONObject {
        val process = child
        if (process != null && !process.isAlive) {
            child = null; dataReady = false; phase = "error"
            message = "代理核心已退出，请检查节点配置、权限与设备兼容性。"
        }
        if (child != null && System.currentTimeMillis() - lastEvidence > 10000) {
            lastEvidence = System.currentTimeMillis()
            dataReady = localApiReady() && when (currentMode) {
                "tun" -> root.run("ip link show magicnet0").success
                "ebpf" -> root.run("${shellQuote(helper.path)} $corePid ${shellQuote(core.path)} ${shellQuote(cgroup)}").success
                else -> true
            }
            if (!dataReady) { phase = "error"; message = "进程仍在，但数据通道未就绪；请断开后重试。" }
        }
        val doc = profile.optJSONObject("nodes")
        val choices = doc?.let { runCatching { nodeChoices(it) }.getOrDefault(emptyList()) } ?: emptyList()
        val result = baseState(context, root.authorized, currentMode)
            .put("phase", if (!componentsReady()) "blocked" else phase)
            .put("running", child != null)
            .put("message", if (!componentsReady()) "此 APK 缺少当前架构的完整组件，不能启动代理。" else message)
            .put("selected", selected).put("nodeCount", choices.size)
            .put("nodes", JSONArray(choices.map { JSONObject().put("tag", it.optString("tag")).put("type", it.optString("type")) }))
            .put("modes", JSONArray().put(modeEntry("system", componentsReady()))
                .put(modeEntry("tun", root.authorized && tun, "需要 Root 和可用的 TUN 设备；请先申请权限。"))
                .put(modeEntry("ebpf", ebpf, "需要 Root、支持 eBPF 的内核和可验证的设备能力。")))
        return result
    }

    override fun mode(value: String) {
        requireCloud(child == null, "请先断开，再切换连接方式。")
        requireCloud(value == "system" || value == "tun" && root.authorized && tun || value == "ebpf" && ebpf,
            "当前设备尚未通过此模式的能力检查。")
        val next = JSONObject(profile.toString()).put("mode", value)
        commit(next); message = if (value == "system") "系统代理" else value.uppercase()
    }

    override fun importText(value: String) {
        requireCloud(child == null, "请先断开，再更新订阅，避免中断正在使用的连接。")
        requireCloud(componentsReady(), "内置组件不完整，无法转换订阅。")
        val input = validateInput(value)
        val source = value.trim().takeIf { it.startsWith("https://") && !it.contains('\n') }.orEmpty()
        val bytes = if (source.isNotEmpty()) downloadSubscription(source) else input
        val converted = boundedProcess(listOf(converter.path), bytes, seconds = 30)
        requireCloud(converted.success, "无法解析此订阅。原配置未变。")
        val rawDocument = cloudJson(converted.stdout)
        val skipped = rawDocument.optJSONObject("_magicbox")?.optInt("skipped", 0) ?: 0
        val document = safeNodeDocument(rawDocument)
        val choices = nodeChoices(document)
        val chosen = selected.takeIf { old -> choices.any { it.optString("tag") == old } } ?: choices.first().getString("tag")
        // Validate without privileged inbounds before committing the canonical profile.
        checkConfig(makeCoreConfig(document, "system", chosen, UUID.randomUUID().toString(), cgroup), privileged = false)
        commit(JSONObject(profile.toString()).put("nodes", document).put("selected", chosen).put("source", source))
        message = if (skipped > 0) "已导入 ${choices.size} · ${skipped} 未兼容" else "已导入 ${choices.size} 个节点"
    }

    override fun refresh() {
        val source = profile.optString("source")
        requireCloud(source.isNotBlank(), "当前内容来自文件或粘贴，请重新导入；没有可更新的订阅链接。")
        importText(source)
    }

    override fun select(tag: String) {
        requireCloud(child == null, "请先断开，再选择节点。")
        val nodes = profile.optJSONObject("nodes") ?: throw CloudFailure("尚未导入节点。")
        requireCloud(nodeChoices(nodes).any { it.optString("tag") == tag }, "节点不存在。")
        commit(JSONObject(profile.toString()).put("selected", tag))
        message = ""
    }

    override fun start() {
        requireCloud(child == null, "代理已经在运行；请先断开。")
        requireCloud(componentsReady(), "内置组件不完整或当前架构不受支持。")
        val document = profile.optJSONObject("nodes") ?: throw CloudFailure("请先导入节点。")
        if (currentMode != "system") {
            requireCloud(root.authorized, "此模式需要 Root 权限。")
            val module = root.run("if test -x '$MAGICNET_CLI'; then '$MAGICNET_CLI' --json service status; else printf absent; fi", seconds = 10)
            requireCloud(module.success, "无法确认 MagicNet 是否运行；请先检查模块状态。")
            if (module.stdout.trim() != "absent") {
                val data = machineData(module.stdout, "service.status")
                requireCloud(data.optJSONObject("core")?.optJSONObject("sing_box")?.optString("process_state") == "stopped",
                    "MagicNet 正在运行或状态不确定。两个版本可同时安装，但不能同时接管全设备流量。")
            }
            requireCloud(root.run("test ! -e /sys/class/net/magicnet0").success, "已有透明代理网卡，请先停止其他代理。")
            if (currentMode == "ebpf") requireCloud(probeEbpf(), "eBPF 能力检查未通过。")
        }
        for (port in listOf(CLOUD_PROXY_PORT, CLOUD_API_PORT)) {
            runCatching { ServerSocket(port, 1, InetAddress.getByName("127.0.0.1")).use {} }
                .getOrElse { throw CloudFailure("本地端口 $port 被占用，请先停止冲突的服务。") }
        }
        secret = UUID.randomUUID().toString()
        checkConfig(makeCoreConfig(document, currentMode, selected, secret, cgroup), currentMode != "system")
        phase = "starting"; message = "正在验证配置与本地接口。"
        val args = listOf(helper.path, "supervise", core.path, config.path, directory.path)
        val process = try { if (currentMode == "system") ProcessBuilder(args).start()
            else root.spawn("exec " + args.joinToString(" ") { shellQuote(it) }) }
        catch (_: Exception) { phase = "error"; message = "无法创建核心进程，请检查组件与 Root 策略。"; throw CloudFailure(message) }
        child = process
        val latch = CountDownLatch(1)
        var pidText = ""
        thread(isDaemon = true, name = "cloud-core-owner") {
            runCatching { pidText = process.inputStream.bufferedReader().readLine().orEmpty() }
            latch.countDown()
        }
        try {
            requireCloud(latch.await(10, TimeUnit.SECONDS), "核心启动超时。")
            corePid = pidText.toIntOrNull() ?: throw CloudFailure("无法确认核心进程所有权。")
            var ready = false
            for (attempt in 0 until 30) {
                if (!process.isAlive) break
                if (localApiReady()) { ready = true; break }
                Thread.sleep(200)
            }
            requireCloud(ready, "核心未能启动；可能是节点配置、Root 策略或网络接口冲突。")
            if (currentMode == "ebpf") requireCloud(root.run("${shellQuote(helper.path)} $corePid ${shellQuote(core.path)} ${shellQuote(cgroup)}").success,
                "eBPF 程序未被当前核心正确持有并挂载，启动已回滚。")
            if (currentMode == "tun") requireCloud(root.run("ip link show magicnet0").success, "TUN 网卡未建立，启动已回滚。")
            phase = "ready"; dataReady = true; lastEvidence = System.currentTimeMillis()
            message = if (currentMode == "system") "HTTP / SOCKS · 127.0.0.1:$CLOUD_PROXY_PORT。需在 Wi-Fi 或应用中手动设置。"
                else "${currentMode.uppercase()} 数据通道已建立；可在偏好中验证出口。"
        } catch (error: Exception) {
            stop(); phase = "error"; message = (error as? CloudFailure)?.message ?: "核心启动失败，已清理本次会话。"
            throw CloudFailure(message)
        }
    }

    override fun stop() {
        val process = child ?: return
        phase = "stopping"
        runCatching { process.outputStream.write("stop\n".toByteArray()); process.outputStream.flush(); process.outputStream.close() }
        if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroy(); process.waitFor(2, TimeUnit.SECONDS) }
        requireCloud(!process.isAlive, "核心监督进程尚未退出；未报告断开成功。")
        child = null; corePid = 0; dataReady = false; phase = "idle"; message = ""
    }

    override fun diagnose(): String {
        requireCloud(child?.isAlive == true && localApiReady(), "请先建立连接。")
        val ok = verifyInternet(CLOUD_PROXY_PORT)
        phase = if (ok) "verified" else "ready"
        message = if (ok) "已通过当前代理收到 HTTPS 204 响应。" else "本地代理在运行，但外网验证失败；请检查节点或稍后重试。"
        return message
    }

    private fun localApiReady(): Boolean {
        val connection = URL("http://127.0.0.1:$CLOUD_API_PORT/version").openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
        return try {
            connection.connectTimeout = 500; connection.readTimeout = 500
            connection.setRequestProperty("Authorization", "Bearer $secret")
            connection.responseCode == 200 && JSONObject(connection.inputStream.use { String(it.readLimited(8192)) }).optString("version").isNotBlank()
        } catch (_: Exception) { false } finally { connection.disconnect() }
    }

    private fun checkConfig(value: JSONObject, privileged: Boolean) {
        val candidate = File(directory, "candidate.json")
        candidate.outputStream().use { it.write(value.toString().toByteArray()); it.fd.sync() }
        candidate.setReadable(false, false); candidate.setReadable(true, true)
        candidate.setWritable(false, false); candidate.setWritable(true, true)
        try {
            val args = listOf(core.path, "check", "-c", candidate.path)
            val checked = if (privileged) root.run(args.joinToString(" ") { shellQuote(it) }, seconds = 20)
                else boundedProcess(args, seconds = 20)
            requireCloud(checked.success, "sing-box 配置校验失败，原有订阅保持不变。")
            requireCloud(candidate.renameTo(config), "无法原子替换运行配置。")
        } finally { candidate.delete() }
    }

    private fun commit(value: JSONObject) {
        val stream = profileFile.startWrite()
        try { stream.write(value.toString().toByteArray()); profileFile.finishWrite(stream); profile = value }
        catch (error: Exception) { profileFile.failWrite(stream); throw error }
    }
}
