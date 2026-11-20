package com.github.lightjunction.magicbox

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

internal const val CLOUD_INPUT_LIMIT = 2 * 1024 * 1024
internal const val CLOUD_OUTPUT_LIMIT = 8 * 1024 * 1024
internal const val CLOUD_PROXY_PORT = 2080
internal const val CLOUD_API_PORT = 29090
internal const val CLOUD_SELECTOR = "__magicbox_proxy__"
internal const val CLOUD_DIRECT = "__magicbox_direct__"
internal const val CLOUD_DNS = "__magicbox_dns__"
internal class CloudFailure(message: String) : Exception(message)
internal fun requireCloud(value: Boolean, message: String) { if (!value) throw CloudFailure(message) }
internal fun InputStream.readLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer); if (count < 0) break
        requireCloud(out.size() + count <= limit, "文件过大，最多允许 ${limit / 1024} KiB。")
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
/** Validate syntax and nesting before Android's permissive recursive parser. */
internal fun cloudJson(text: String): JSONObject {
    requireCloud(text.length <= CLOUD_OUTPUT_LIMIT, "状态或配置过大。")
    return try {
        StrictJson(text).objectDocument()
        JSONObject(text)
    } catch (error: CloudFailure) { throw error }
      catch (_: Exception) { throw CloudFailure("JSON 解析失败。") }
}
internal fun machineData(text: String, command: String): JSONObject {
    val envelope = cloudJson(text)
    requireCloud(envelope.opt("schema") == 1 &&
        envelope.opt("ok") == true && envelope.opt("command") == command,
        "MagicNet 的机器接口不兼容或读取失败；不会使用旧状态冒充成功。")
    return envelope.optJSONObject("data") ?: throw CloudFailure("MagicNet 状态缺少数据。")
}
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun safeNodeDocument(document: JSONObject): JSONObject {
    val copy = cloudJson(document.toString())
    val all = (copy.optJSONArray("outbounds") ?: JSONArray()).objects() + (copy.optJSONArray("endpoints") ?: JSONArray()).objects()
    requireCloud(all.isNotEmpty() && all.size <= 5000, "节点数量无效，最多支持 5000 个节点。")
    val tags = mutableSetOf<String>()
    all.forEachIndexed { index, node ->
        requireCloud(node.optString("type").isNotBlank(), "节点缺少协议类型。")
        val tag = node.optString("tag").ifBlank { "节点 ${index + 1}" }
        requireCloud(tag.length <= 200 && tag.none { it.isISOControl() }, "节点名称过长或含控制字符。")
        requireCloud(tag !in setOf(CLOUD_SELECTOR, CLOUD_DIRECT, CLOUD_DNS) && tags.add(tag), "节点名称重复或使用了保留名称。")
        node.put("tag", tag)
    }
    fun validate(value: Any?, depth: Int) {
        requireCloud(depth <= 32, "配置嵌套层级过深。")
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                requireCloud(!key.endsWith("_path") && key !in setOf("providers", "script", "command"), "订阅包含本地文件或可执行引用，已拒绝导入。")
                validate(value.opt(key), depth + 1)
            }
            is JSONArray -> (0 until value.length()).forEach { validate(value.opt(it), depth + 1) }
        }
    }
    validate(copy, 0)
    requireCloud(nodeChoices(copy).isNotEmpty(), "配置中没有可用代理节点。")
    return copy
}
internal fun nodeChoices(document: JSONObject): List<JSONObject> =
    ((document.optJSONArray("outbounds") ?: JSONArray()).objects() + (document.optJSONArray("endpoints") ?: JSONArray()).objects())
        .filter { it.optString("type") !in setOf("direct", "block", "dns", "selector", "urltest") }
internal fun makeCoreConfig(document: JSONObject, mode: String, selected: String, secret: String, cgroup: String): JSONObject {
    requireCloud(mode in setOf("system", "tun", "ebpf"), "不支持的连接方式。")
    val nodes = nodeChoices(document)
    requireCloud(nodes.any { it.optString("tag") == selected }, "请选择有效节点。")
    val outbounds = JSONArray((document.optJSONArray("outbounds") ?: JSONArray()).toString())
    outbounds.put(JSONObject().put("type", "selector").put("tag", CLOUD_SELECTOR).put("outbounds", JSONArray(nodes.map { it.optString("tag") })).put("default", selected))
    outbounds.put(JSONObject().put("type", "direct").put("tag", CLOUD_DIRECT))
    val inbounds = JSONArray().put(JSONObject().put("type", "mixed").put("tag", "mixed-in").put("listen", "127.0.0.1").put("listen_port", CLOUD_PROXY_PORT))
    if (mode == "tun") inbounds.put(JSONObject("""{
          "type":"tun","tag":"tun-in","interface_name":"magicnet0",
          "address":["172.19.0.1/30","fdfe:dcba:9876::1/126"],
          "auto_route":true,"auto_redirect":true,"strict_route":true,
          "stack":"mixed","mtu":1500,"exclude_uid":[0],
          "route_exclude_address":["127.0.0.0/8","10.0.0.0/8","172.16.0.0/12","192.168.0.0/16","::1/128","fc00::/7","fe80::/10"]
        }"""))
    if (mode == "ebpf") inbounds.put(JSONObject().put("type", "ebpf").put("tag", "tun-in").put("mode", "local")
        .put("network", JSONArray(listOf("tcp", "udp"))).put("local", JSONObject().put("dns_mode", "hijack").put("cgroup_path", cgroup)
            .put("ipv6", true).put("bypass_private_address", true).put("exclude_uid", JSONArray(listOf(0)))))
    // Android app UIDs cannot open netlink route monitors. A loopback mixed
    // proxy uses the OS default route and needs no privileged monitor at all.
    // Transparent root modes still require it to avoid routing back into TUN.
    return JSONObject().put("log", JSONObject().put("level", "warn").put("timestamp", false))
        .put("inbounds", inbounds).put("outbounds", outbounds).put("endpoints", document.optJSONArray("endpoints") ?: JSONArray())
        .put("dns", JSONObject().put("servers", JSONArray().put(JSONObject().put("type", "udp").put("server", "1.1.1.1").put("tag", CLOUD_DNS))))
        .put("route", JSONObject().put("auto_detect_interface", mode != "system").put("default_domain_resolver", CLOUD_DNS)
            .put("rules", JSONArray().put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))).put("final", CLOUD_SELECTOR))
        .put("experimental", JSONObject().put("clash_api", JSONObject().put("external_controller", "127.0.0.1:$CLOUD_API_PORT").put("secret", secret)))
}
