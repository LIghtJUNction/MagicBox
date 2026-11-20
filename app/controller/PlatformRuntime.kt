package com.github.lightjunction.magicbox.reboot

import android.content.Context
import android.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import com.github.lightjunction.magicbox.parseNodeList

object PlatformRuntime {
    private val runtime by lazy { ModuleRuntime() }
    fun get(context: Context): RuntimePort = runtime
}

private class ModuleRuntime : RuntimePort {
    private val current = MutableStateFlow(BoxState(phase = Phase.PERMISSION, detail = "此版本必须获得 root，并安装启用 MagicNet 模块。", mode = ProxyMode.TUN))
    override val state = current.asStateFlow()
    private val lock = Mutex()
    private val cli = "/data/adb/modules/MagicNet/cli"
    private var authorized = false

    private suspend fun call(vararg args: String, seconds: Long = 20): String =
        EngineIO.rootArgs(cli, *args, seconds = seconds).requireSuccess("MagicNet 未完成操作，请检查模块状态。")

    private fun envelope(raw: String, command: String): JSONObject {
        val value = try { JSONObject(raw.trim()) } catch (_: Exception) { throw BoxFailure("模块返回了无效状态；请更新 MagicNet。") }
        if (value.optInt("schema") != 1 || !value.optBoolean("ok") || value.optString("command") != command) {
            throw BoxFailure("MagicNet 状态协议不兼容，需要支持 schema 1 的版本。")
        }
        return value.getJSONObject("data")
    }

    private suspend fun authorize() {
        if (authorized) return
        val script = "test \"\$(id -u)\" = 0 && " +
            "test \"\$(sed -n 's/^id=//p' /data/adb/modules/MagicNet/module.prop)\" = MagicNet && " +
            "test ! -e /data/adb/modules/MagicNet/disable && test ! -e /data/adb/modules/MagicNet/remove && " +
            "test -x ${InputPolicy.quote(cli)}"
        EngineIO.root(script).requireSuccess("需要 root 与已启用的 MagicNet。请分别为两个包授权。")
        val caps = envelope(call("--json", "capabilities"), "machine.capabilities")
        val commands = caps.optJSONArray("commands") ?: throw BoxFailure("模块未公布机器接口。")
        if ((0 until commands.length()).none { commands.optString(it) == "service.status" }) throw BoxFailure("请更新 MagicNet 后重试。")
        authorized = true
        current.value = current.value.copy(authorized = true)
        updateNodes()
    }
    private suspend fun updateNodes() {
        val nodes = runCatching { parseNodeList(call("node", "list")).filter { it.length <= 160 && it.none(Char::isISOControl) }.take(2000) }.getOrDefault(emptyList())
        val selected = runCatching { call("node", "current").trim().take(160) }.getOrDefault("")
        current.value = current.value.copy(nodes = nodes, selected = selected.takeIf { it in nodes } ?: nodes.firstOrNull().orEmpty())
    }
    private suspend fun snapshot() {
        try {
            val status = envelope(call("--json", "service", "status"), "service.status")
            val lifecycle = status.optString("lifecycle")
            val readiness = status.optJSONObject("readiness")
            val ready = InputPolicy.isReady(1, true, lifecycle, if (readiness?.isNull("overall") == false) readiness.optBoolean("overall") else null)
            val mode = if (status.optJSONObject("transparent")?.optString("effective_type") == "ebpf") ProxyMode.EBPF else ProxyMode.TUN
            current.value = current.value.copy(
                phase = if (ready) Phase.RUNNING else if (lifecycle == "stopped") Phase.IDLE else Phase.ERROR,
                detail = if (ready) "MagicNet 已确认内核 API 与数据面就绪。" else if (lifecycle == "stopped") "模块已连接，代理服务尚未启动。" else "模块尚未证明数据面就绪，请检查 MagicNet 健康状态。",
                mode = mode, authorized = true,
            )
        } catch (error: Exception) {
            current.value = current.value.copy(phase = Phase.ERROR, detail = "无法确认模块当前状态，不会沿用旧的已连接状态。")
            throw error
        }
    }
    override suspend fun refresh(requestPermission: Boolean) = lock.withLock {
        if (!authorized && !requestPermission) return@withLock
        if (requestPermission) authorize()
        snapshot()
    }
    override suspend fun start(mode: ProxyMode) = lock.withLock {
        if (mode == ProxyMode.SYSTEM) throw BoxFailure("MagicNet 提供 TUN/eBPF 数据面；独立系统代理请使用通用版。")
        authorize()
        current.value = current.value.copy(phase = Phase.STARTING, detail = "正在通过模块切换数据面。")
        try {
            call("transparent", "set", if (mode == ProxyMode.TUN) "tun" else "ebpf", seconds = 60)
            call("service", "start", seconds = 60)
            snapshot()
        } catch (error: Exception) {
            runCatching { snapshot() }
            if (current.value.transitioning) current.value = current.value.copy(phase = Phase.ERROR, detail = "启动未完成，请检查模块；未自动回退其他模式。")
            throw error
        }
    }
    override suspend fun stop() = lock.withLock {
        authorize()
        try { call("service", "stop", seconds = 45) } finally { snapshot() }
        Unit
    }
    override suspend fun importText(text: String) = lock.withLock {
        authorize()
        if (current.value.running || current.value.transitioning) throw BoxFailure("请先停止服务再更换订阅。")
        val source = InputPolicy.validate(text)
        val name = "magicbox-${UUID.randomUUID()}.txt"
        val isUrlList = source.lines().filter(String::isNotBlank).all { com.github.lightjunction.magicbox.isValidSubscriptionUrl(it.trim()) }
        val action = if (isUrlList) "apply-subscription" else "apply-subscription-source"
        val quoted = InputPolicy.quote(cli)
        val script = "set -e; $quoted webui payload create subscription '$name' >/dev/null; " +
            "trap \"$quoted webui payload remove subscription '$name' >/dev/null 2>&1\" EXIT; " +
            "while IFS= read -r chunk; do $quoted webui payload append subscription '$name' \"\$chunk\" >/dev/null; done; " +
            "$quoted webui payload $action '$name'"
        val bytes = source.toByteArray(Charsets.UTF_8)
        val chunks = buildString(bytes.size * 4 / 3 + 1024) {
            var offset = 0
            while (offset < bytes.size) {
                val length = minOf(24576, bytes.size - offset)
                append(Base64.encodeToString(bytes, offset, length, Base64.NO_WRAP)).append('\n')
                offset += length
            }
        }
        EngineIO.root(script, chunks.toByteArray(), seconds = 90).requireSuccess("模块未接受订阅；请检查格式或订阅源。")
        updateNodes()
        snapshot()
    }
    override suspend fun select(tag: String) = lock.withLock {
        authorize()
        if (tag !in current.value.nodes) throw BoxFailure("节点不在当前集合中。")
        call("node", "use", tag)
        updateNodes()
    }
}
