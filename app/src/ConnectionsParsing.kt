package com.github.lightjunction.magicbox

import org.json.JSONObject

data class ConnectionSnapshot(
    val count: Int,
    val uploadTotal: Long,
    val downloadTotal: Long,
    val connections: List<ConnectionTarget>,
) {
    val topConnections: List<ConnectionTarget> = connections.take(8)
}

data class ConnectionTarget(
    val id: String,
    val label: String,
    val network: String,
    val rule: String,
    val rulePayload: String,
    val chain: String,
    val detail: String,
    val transfer: String,
    val totalBytes: Long,
)

data class ConnectionBucket(
    val name: String,
    val query: String,
    val count: Int,
    val bytes: Long,
)

fun parseConnectionSnapshot(output: String): ConnectionSnapshot? =
    runCatching {
        val root = JSONObject(output)
        val connections = root.optJSONArray("connections") ?: return null
        val targets = mutableListOf<ConnectionTarget>()
        for (index in 0 until connections.length()) {
            val item = connections.optJSONObject(index) ?: continue
            val id = item.optString("id").orEmpty()
            val metadata = item.optJSONObject("metadata")
            val host = metadata?.optString("host").orEmpty()
            val destination = metadata?.optString("destinationIP").orEmpty()
            val port = metadata?.opt("destinationPort")?.toString().orEmpty()
            val target = host.ifBlank { destination }
            if (id.isNotBlank() && target.isNotBlank()) {
                val upload = item.optLong("upload", 0L)
                val download = item.optLong("download", 0L)
                val label = if (port.isBlank() || port == "0") target else "$target:$port"
                val network = metadata?.optString("network").orEmpty()
                val rule = item.optString("rule").orEmpty()
                val rulePayload = item.optString("rulePayload").orEmpty()
                val chain =
                    item.optJSONArray("chains")?.let { chains ->
                        (0 until chains.length()).joinToString(" > ") { chains.optString(it) }.takeIf { it.isNotBlank() }
                    }.orEmpty()
                targets +=
                    ConnectionTarget(
                        id = id,
                        label = label,
                        network = network,
                        rule = rule,
                        rulePayload = rulePayload,
                        chain = chain,
                        detail = connectionDetail(network, rule, rulePayload, chain),
                        transfer = "${formatBytes(upload)} up / ${formatBytes(download)} down",
                        totalBytes = upload + download,
                    )
            }
        }
        ConnectionSnapshot(
            count = connections.length(),
            uploadTotal = root.optLong("uploadTotal", 0L),
            downloadTotal = root.optLong("downloadTotal", 0L),
            connections = targets.distinctBy { it.id }.sortedByDescending { it.totalBytes },
        )
    }.getOrNull()

fun ConnectionTarget.matchesConnectionQuery(query: String): Boolean {
    val terms = query.split(Regex("""\s+""")).map { it.trim() }.filter { it.isNotBlank() }
    if (terms.isEmpty()) return true
    val haystack = "$label $network $rule $rulePayload $chain $detail $transfer".lowercase()
    return terms.all { term -> haystack.contains(term.lowercase()) }
}

fun ruleConnectionBuckets(connections: List<ConnectionTarget>): List<ConnectionBucket> =
    connections
        .mapNotNull { target ->
            val name =
                listOf(target.rule, target.rulePayload)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { return@mapNotNull null }
            ConnectionBucketSeed(name = name, query = name, bytes = target.totalBytes)
        }
        .toConnectionBuckets()

fun chainConnectionBuckets(connections: List<ConnectionTarget>): List<ConnectionBucket> =
    connections
        .mapNotNull { target ->
            val name = target.chain.ifBlank { target.network }.ifBlank { return@mapNotNull null }
            ConnectionBucketSeed(name = name, query = name.replace(" > ", " "), bytes = target.totalBytes)
        }
        .toConnectionBuckets()

private data class ConnectionBucketSeed(
    val name: String,
    val query: String,
    val bytes: Long,
)

private fun List<ConnectionBucketSeed>.toConnectionBuckets(): List<ConnectionBucket> =
    groupBy { it.name }
        .map { (name, seeds) ->
            ConnectionBucket(
                name = name,
                query = seeds.first().query,
                count = seeds.size,
                bytes = seeds.sumOf { it.bytes },
            )
        }
        .sortedWith(compareByDescending<ConnectionBucket> { it.bytes }.thenByDescending { it.count })
        .take(3)

private fun connectionDetail(
    network: String,
    rule: String,
    rulePayload: String,
    chain: String,
): String {
    val parts =
        listOf(
            network,
            rule,
            rulePayload,
            chain,
        ).filter { it.isNotBlank() }
    return parts.joinToString(" · ").ifBlank { "direct" }
}

fun formatBytes(value: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var amount = value.toDouble().coerceAtLeast(0.0)
    var unit = 0
    while (amount >= 1024.0 && unit < units.lastIndex) {
        amount /= 1024.0
        unit += 1
    }
    return if (unit == 0) "${amount.toLong()} ${units[unit]}" else String.format("%.1f %s", amount, units[unit])
}
