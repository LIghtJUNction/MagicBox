package com.github.lightjunction.magicbox

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTrafficSamples(samples: List<LiveStats>): String =
    buildString {
        val overview = buildTrafficOverview(samples)
        appendTrafficSampleSummary(samples, overview)
        appendLine("index,timestamp,up_bytes_per_second,down_bytes_per_second,total_bytes_per_second,estimated_up_bytes,estimated_down_bytes")
        var estimatedUp = 0L
        var estimatedDown = 0L
        samples.forEachIndexed { index, sample ->
            if (index > 0) {
                val previous = samples[index - 1]
                val seconds = (sample.timestampMillis - previous.timestampMillis).coerceAtLeast(0L) / 1000.0
                estimatedUp += (((previous.up + sample.up) / 2.0) * seconds).toLong().coerceAtLeast(0L)
                estimatedDown += (((previous.down + sample.down) / 2.0) * seconds).toLong().coerceAtLeast(0L)
            }
            appendLine(
                "${index + 1},${sample.timestampMillis.csvTime()},${sample.up.csvRate()},${sample.down.csvRate()},${sample.total.csvRate()},$estimatedUp,$estimatedDown",
            )
        }
    }.trim()

private fun Float.csvRate(): String = String.format(Locale.US, "%.1f", this)

private fun Long.csvTime(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(this))

private fun StringBuilder.appendTrafficSampleSummary(
    samples: List<LiveStats>,
    overview: TrafficOverview,
) {
    if (samples.isEmpty()) return
    val first = samples.first()
    val last = samples.last()
    val profile = buildTrafficProfile(samples, overview)
    val average = samples.map { it.total }.average().toFloat()
    val peak = samples.maxOf { it.total }
    appendLine("# samples=${samples.size}")
    appendLine("# window_seconds=${(overview.windowMillis / 1000).coerceAtLeast(0L)}")
    appendLine("# first_timestamp=${first.timestampMillis.csvTime()}")
    appendLine("# last_timestamp=${last.timestampMillis.csvTime()}")
    appendLine("# average_total_bytes_per_second=${average.csvRate()}")
    appendLine("# peak_total_bytes_per_second=${peak.csvRate()}")
    appendLine("# estimated_up_bytes=${overview.estimatedUpBytes}")
    appendLine("# estimated_down_bytes=${overview.estimatedDownBytes}")
    appendLine("# profile=${profile.state.name.lowercase(Locale.US)}")
    appendLine("# trend=${profile.trend.name.lowercase(Locale.US)}")
    appendLine("# burst_ratio=${profile.burstRatio.csvRate()}")
    appendLine("# stability_cv=${profile.stabilityCv.csvRate()}")
    appendLine("# up_share=${profile.upShare.csvRate()}")
    appendLine("# down_share=${profile.downShare.csvRate()}")
}
