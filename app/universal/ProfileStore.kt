package com.github.lightjunction.magicbox.reboot

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal class ProfileStore(context: Context) {
    val directory = File(context.noBackupFilesDir, "runtime").apply { mkdirs() }
    private val profile = AtomicFile(File(directory, "profile.json"))
    fun read(): JSONObject? = runCatching { profile.openRead().use { JSONObject(it.bufferedReader().readText()) } }.getOrNull()
    fun names(): List<String> = read()?.optJSONArray("names")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
    fun selected(): String = read()?.let { p -> p.getJSONArray("names").optString(p.optInt("selected")) }.orEmpty()
    fun save(value: JSONObject) {
        val stream = profile.startWrite()
        try { stream.write(value.toString().toByteArray()); profile.finishWrite(stream) }
        catch (error: Exception) { profile.failWrite(stream); throw error }
    }
    fun select(name: String) {
        val value = read() ?: throw BoxFailure("请先导入订阅。")
        val index = names().indexOf(name)
        if (index < 0) throw BoxFailure("节点不在当前集合中。")
        save(value.put("selected", index))
    }
    fun normalized(report: JSONObject): JSONObject {
        if (report.optInt("schema") != 1) throw BoxFailure("订阅解析器协议不兼容。")
        val failed = report.optInt("failed", -1)
        val nodes = report.optJSONArray("outbounds") ?: throw BoxFailure("没有可用节点。")
        if (failed != 0) throw BoxFailure("有 $failed 个节点无法转换。未替换旧订阅，请移除不兼容节点或更换订阅格式。")
        if (nodes.length() == 0 || nodes.length() > 2000) throw BoxFailure("订阅需要包含 1–2000 个可用节点。")
        val names = JSONArray()
        val used = mutableSetOf<String>()
        repeat(nodes.length()) { index ->
            val node = nodes.getJSONObject(index)
            if (node.optString("type") !in setOf("vless", "vmess", "shadowsocks", "trojan", "socks", "hysteria2", "anytls", "tuic")) throw BoxFailure("订阅包含当前内核适配器未验证的协议。")
            val server = node.optString("server")
            if (server.isBlank() || server.any { it.isWhitespace() || it.isISOControl() || it == '/' || it == '\\' } || node.optInt("server_port") !in 1..65535) throw BoxFailure("节点地址或端口无效。")
            rejectFileReferences(node)
            val base = node.optString("tag").filterNot(Char::isISOControl).take(100).ifBlank { "节点 ${index + 1}" }
            var name = base
            var suffix = 2
            while (!used.add(name)) { name = "$base · ${suffix++}" }
            names.put(name)
            node.put("tag", "n${index + 1}")
        }
        return JSONObject().put("nodes", nodes).put("names", names).put("selected", 0)
    }
    private fun rejectFileReferences(value: Any) {
        if (value is JSONObject) value.keys().forEach { key ->
            if (key.endsWith("_path") || key in setOf("detour", "bind_interface", "routing_mark", "executable")) throw BoxFailure("节点包含不允许的本地文件或路由引用。")
            rejectFileReferences(value.get(key))
        }
        if (value is JSONArray) repeat(value.length()) { rejectFileReferences(value.get(it)) }
    }
    fun config(profile: JSONObject, mode: ProxyMode, secret: String): JSONObject {
        val selected = JSONObject(profile.getJSONArray("nodes").getJSONObject(profile.getInt("selected")).toString()).put("tag", "egress")
        val inbounds = JSONArray().put(JSONObject().put("type", "mixed").put("tag", "mixed-in").put("listen", "127.0.0.1").put("listen_port", 2080))
        if (mode == ProxyMode.TUN) inbounds.put(JSONObject("""{
            "type":"tun","tag":"tun-in","interface_name":"magicbox0",
            "address":["172.29.0.1/30","fdfe:dcba:9877::1/126"],
            "auto_route":true,"auto_redirect":true,"strict_route":true,
            "exclude_uid":[0],"stack":"mixed","mtu":1400,
            "route_exclude_address":["10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","100.64.0.0/10","127.0.0.0/8","169.254.0.0/16","224.0.0.0/4","::1/128","fc00::/7","fe80::/10"]
        }"""))
        if (mode == ProxyMode.EBPF) inbounds.put(JSONObject("""{
            "type":"ebpf","tag":"tun-in","mode":"local","network":["tcp","udp"],
            "local":{"dns_mode":"hijack","ipv6":true,"bypass_private_address":true,"exclude_uid":[0]}
        }"""))
        val rules = JSONArray()
        if (mode != ProxyMode.SYSTEM) rules.put(JSONObject("""{"inbound":["tun-in"],"port":53,"action":"hijack-dns"}"""))
        val route = JSONObject().put("final", "egress").put("default_domain_resolver", "bootstrap").put("rules", rules)
        if (mode != ProxyMode.SYSTEM) route.put("auto_detect_interface", true)
        val dns = JSONObject("""{"servers":[
            {"type":"https","tag":"bootstrap","server":"1.1.1.1","server_port":443,"path":"/dns-query","tls":{"enabled":true,"server_name":"cloudflare-dns.com"},"detour":"direct"},
            {"type":"https","tag":"remote","server":"1.1.1.1","server_port":443,"path":"/dns-query","tls":{"enabled":true,"server_name":"cloudflare-dns.com"},"detour":"egress"}
        ],"final":"remote"}""")
        return JSONObject().put("log", JSONObject().put("level", "error")).put("inbounds", inbounds)
            .put("outbounds", JSONArray().put(selected).put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put("dns", dns).put("route", route)
            .put("experimental", JSONObject().put("clash_api", JSONObject().put("external_controller", "127.0.0.1:20790").put("secret", secret)))
    }
}
