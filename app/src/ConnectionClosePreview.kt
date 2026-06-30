package com.github.lightjunction.magicbox

const val CONNECTION_CLOSE_LIMIT = 8

data class ConnectionClosePreview(
    val totalMatches: Int,
    val candidates: List<ConnectionTarget>,
) {
    val totalBytes: Long = candidates.sumOf { it.totalBytes }
    val truncated: Boolean = totalMatches > candidates.size
}

fun buildConnectionClosePreview(
    matches: List<ConnectionTarget>,
    limit: Int = CONNECTION_CLOSE_LIMIT,
): ConnectionClosePreview {
    val safeLimit = limit.coerceIn(1, CONNECTION_CLOSE_LIMIT)
    return ConnectionClosePreview(
        totalMatches = matches.size,
        candidates = matches.sortedByDescending { it.totalBytes }.take(safeLimit),
    )
}

