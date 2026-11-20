package com.github.lightjunction.magicbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** UI edition is a client, never a second runtime or a shadow copy of module state. */
internal class ModuleBackend(private val context: Context) : CloudBackend {
    override val root = RootTransport()
    private var compatible = false
    private var ebpf = false
    private var currentMode = "tun"
    private var notice = "需要 Root + MagicNet"
    private var nodes = JSONArray()
    private var selected = ""
    private var lastNodeRead = 0L

    private fun command(value: String, input: ByteArray = byteArrayOf(), seconds: Long = 20): ProcessResult {
        requireCloud(root.authorized && compatible, "请先授权 Root，并确认 MagicNet 的版本兼容。")
        return root.run("'$MAGICNET_CLI' $value", input, seconds)
    }
    private fun data(command: String, name: String): JSONObject {
        val result = command(command)
        requireCloud(result.success, "读取 MagicNet 状态失败，未沿用上一次的连接状态。")
        return machineData(result.stdout, name)
    }
    private fun write(command: String, seconds: Long = 60) {
        requireCloud(command(command, seconds = seconds).success, "MagicNet 未能完成此操作，请检查模块诊断。")
        lastNodeRead = 0L
    }
    override fun authorize() {
        compatible = false; ebpf = false; nodes = JSONArray(); selected = ""
        super.authorize()
        val exists = root.run("test -x '$MAGICNET_CLI' && test ! -f '/data/adb/modules/MagicNet/disable' && test ! -f '/data/adb/modules/MagicNet/remove'")
        requireCloud(exists.success, "未找到已启用的 MagicNet 模块。此 UI 版不能独立运行。")
        val response = root.run("'$MAGICNET_CLI' --json capabilities")
        val caps = machineData(response.stdout, "machine.capabilities")
        val commands = caps.optJSONArray("commands") ?: JSONArray()
        requireCloud(response.success && (0 until commands.length()).any { commands.optString(it) == "service.status" },
            "MagicNet 不支持所需的机器接口，请先更新模块。")
        compatible = true
        val probe = root.run("'/data/adb/modules/MagicNet/bin/sing-box' tools ebpf status --mode local --network tcp,udp --json", seconds = 15)
        ebpf = probe.success && runCatching { cloudJson(probe.stdout).optString("result") == "supported" }.getOrDefault(false)
        notice = "MagicNet 已连接"
        status()
    }
    override fun status(): JSONObject {
        var phase = "blocked"
        var running = false
        if (root.authorized && compatible) {
            try {
                val data = data("--json service status", "service.status")
                val process = data.optJSONObject("core")?.optJSONObject("sing_box")?.optString("process_state")
                running = process == "running"
                currentMode = data.optJSONObject("transparent")?.optString("configured_mode")?.takeIf { it in setOf("tun", "ebpf") } ?: "tun"
                phase = when {
                    process == "stopped" -> "idle"
                    process != "running" -> "unknown"
                    data.optJSONObject("readiness")?.opt("overall") == true -> "ready"
                    else -> "error"
                }
                notice = when (phase) {
                    "ready" -> "已连接"
                    "idle" -> ""
                    "error" -> "数据通道未就绪"
                    else -> "状态未知"
                }
                if (System.currentTimeMillis() - lastNodeRead > 15000) readNodes(running)
            } catch (_: Exception) {
                phase = "unknown"; notice = "状态未知"
            }
        }
        return baseState(context, root.authorized, currentMode).put("phase", phase).put("running", running)
            .put("message", notice).put("selected", selected).put("nodes", nodes).put("nodeCount", nodes.length())
            .put("modes", JSONArray()
                .put(modeEntry("system", false, "MagicNet 的透明代理接口只支持 TUN / eBPF；独立系统代理请使用通用版。"))
                .put(modeEntry("tun", root.authorized && compatible, "需要 Root 和已启用的 MagicNet。"))
                .put(modeEntry("ebpf", root.authorized && compatible && ebpf, "模块内核与设备必须通过 eBPF 能力检查。")))
    }
    private fun readNodes(running: Boolean) {
        lastNodeRead = System.currentTimeMillis()
        val config = root.run("cat '/data/adb/modules/MagicNet/.config/sing-box/config.json'")
        if (!config.success) { nodes = JSONArray(); selected = ""; return }
        val doc = runCatching { cloudJson(config.stdout) }.getOrNull()
        if (doc == null) { nodes = JSONArray(); selected = ""; return }
        nodes = JSONArray(nodeChoices(doc).take(5000).map { JSONObject().put("tag", it.optString("tag")).put("type", it.optString("type")) })
        if (running) {
            val result = command("api proxies", seconds = 8)
            val proxy = runCatching { cloudJson(result.stdout).optJSONObject("proxies")?.optJSONObject("proxy") }.getOrNull()
            selected = if (result.success) proxy?.optString("now").orEmpty() else ""
        } else { selected = "" }
    }
    override fun start() {
        val before = data("--json service status", "service.status")
        val process = before.optJSONObject("core")?.optJSONObject("sing_box")?.optString("process_state")
        requireCloud(process == "stopped", "模块状态不是已停止；请先确认并停止现有代理。")
        requireCloud(root.run("test ! -e /sys/class/net/magicnet0").success, "检测到其他透明代理，请先停止通用版或其他代理应用。")
        write("service start sing-box")
        val after = data("--json service status", "service.status")
        requireCloud(after.optJSONObject("readiness")?.opt("overall") == true,
            "启动命令已执行，但模块尚未报告完整就绪；请查看诊断，不会显示连接成功。")
    }
    override fun stop() {
        write("service stop sing-box")
        val after = data("--json service status", "service.status")
        requireCloud(after.optJSONObject("core")?.optJSONObject("sing_box")?.optString("process_state") == "stopped",
            "模块尚未确认核心已停止。")
    }
    override fun mode(value: String) {
        requireCloud(value == "tun" || value == "ebpf" && ebpf, "模块未通过此模式的能力检查。")
        val before = data("--json service status", "service.status")
        requireCloud(before.optJSONObject("core")?.optJSONObject("sing_box")?.optString("process_state") == "stopped",
            "请先停止模块代理，再切换透明代理方式。")
        write("transparent set $value")
        val after = data("--json transparent status", "transparent.status")
        requireCloud(after.optString("configured_mode") == value, "模块未确认新的连接方式。")
        currentMode = value
    }
    override fun importText(value: String) {
        val bytes = validateInput(value)
        val name = "magicbox-${UUID.randomUUID()}.txt"
        val create = command("webui payload create subscription $name")
        val path = "/data/adb/modules/MagicNet/.tmp/webui-subscription/$name"
        requireCloud(create.success && create.stdout.trim() == path, "模块不支持安全订阅导入，请更新 MagicNet。")
        try {
            // The namespace and file were created/secured by MagicNet. Data travels
            // on stdin, never in argv, diagnostics, the WebView URL or shared storage.
            val result = root.run("test -f ${shellQuote(path)} && test ! -L ${shellQuote(path)} && cat > ${shellQuote(path)}", bytes)
            requireCloud(result.success, "写入私有订阅暂存文件失败。")
            val urls = value.trim().lineSequence().filter { it.isNotBlank() }.toList()
            val action = if (urls.all { it.startsWith("https://") }) "apply-subscription" else "apply-subscription-source"
            write("webui payload $action $name", seconds = 75)
            notice = "订阅已更新"
        } finally { runCatching { command("webui payload remove subscription $name") } }
    }
    override fun refresh() { write("sub update sing-box", seconds = 75) }
    override fun select(tag: String) {
        requireCloud((0 until nodes.length()).any { nodes.getJSONObject(it).optString("tag") == tag }, "请选择模块中实际存在的节点。")
        write("node use ${shellQuote(tag)}")
        readNodes(true)
        requireCloud(selected == tag, "模块未确认节点切换，未更新界面中的已选节点。")
    }
    override fun diagnose(): String {
        requireCloud(selected.isNotBlank(), "请先选择模块中的当前节点。")
        val result = command("node test ${shellQuote(selected)}", seconds = 25)
        requireCloud(selected.isNotBlank() && result.success, "当前节点验证失败；请在高级管理中查看模块诊断。")
        return "当前节点已通过 MagicNet 的实际延迟测试。"
    }
}
