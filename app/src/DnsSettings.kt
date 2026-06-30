package com.github.lightjunction.magicbox

enum class DnsProfile(val cli: String) {
    Default("default"),
    CloudflareDoh("cloudflare-doh"),
    CloudflareDot("cloudflare-dot"),
    CloudflareUdp("cloudflare-udp"),
}

data class DnsStatus(
    val profile: String,
    val primary: String,
    val secondary: String,
    val transport: String,
)

fun parseDnsStatus(output: String): DnsStatus {
    val values =
        output
            .lineSequence()
            .mapNotNull { line ->
                val (key, value) = line.splitOnce("=") ?: return@mapNotNull null
                key.trim() to value.trim()
            }
            .toMap()
    return DnsStatus(
        profile = values["profile"].orEmpty().ifBlank { "default" },
        primary = values["primary"].orEmpty(),
        secondary = values["secondary"].orEmpty(),
        transport = values["transport"].orEmpty().ifBlank { "default" },
    )
}

fun dnsProfileLabel(
    t: UiText,
    profile: DnsProfile,
): String =
    when (profile) {
        DnsProfile.Default -> if (t === UiText.zh) "默认" else "Default"
        DnsProfile.CloudflareDoh -> "DoH"
        DnsProfile.CloudflareDot -> "DoT"
        DnsProfile.CloudflareUdp -> "UDP"
    }

private fun String.splitOnce(delimiter: String): Pair<String, String>? {
    val index = indexOf(delimiter)
    if (index < 0) return null
    return take(index) to drop(index + delimiter.length)
}

