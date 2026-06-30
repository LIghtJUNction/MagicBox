package com.github.lightjunction.magicbox

fun parseNodeList(output: String): List<String> =
    output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

fun parseCurrentNode(output: String): String =
    output
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !it.startsWith("[") }
        .orEmpty()

fun parseNodeTestSummary(output: String): String =
    output
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.contains("=") && it.endsWith("ms") }
        .orEmpty()

fun parseNodeTestAll(output: String): Map<String, String> =
    output
        .lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val (node, value) = line.splitOnce("=") ?: return@mapNotNull null
            node.takeIf { it.isNotBlank() }?.let { it to value }
        }
        .toMap()

fun fastestNode(
    nodes: List<String>,
    tests: Map<String, String>,
): String? {
    var bestNode: String? = null
    var bestDelay: Int? = null
    nodes.forEach { node ->
        val delay = parseNodeDelayMillis(tests[node]) ?: return@forEach
        if (bestDelay == null || delay < bestDelay!!) {
            bestNode = node
            bestDelay = delay
        }
    }
    return bestNode
}

fun parseNodeDelayMillis(value: String?): Int? =
    value
        ?.trim()
        ?.let { Regex("""(?i)\b(\d+)\s*ms\b""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

private fun String.splitOnce(delimiter: String): Pair<String, String>? {
    val index = indexOf(delimiter)
    if (index < 0) return null
    return substring(0, index).trim() to substring(index + delimiter.length).trim()
}
