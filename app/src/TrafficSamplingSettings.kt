package com.github.lightjunction.magicbox

enum class TrafficSampleInterval(
    val label: String,
    val millis: Long,
) {
    Fast("2.5s", 2_500L),
    Balanced("5s", 5_000L),
    Light("10s", 10_000L),
    Battery("30s", 30_000L);

    companion object {
        fun fromName(name: String?): TrafficSampleInterval =
            entries.firstOrNull { it.name == name } ?: Balanced
    }
}

fun UiText.sampleInterval(): String = if (this === UiText.zh) "采样间隔" else "Sample interval"

fun UiText.sampleIntervalSummary(interval: TrafficSampleInterval): String =
    if (this === UiText.zh) {
        "自动采样每 ${interval.label} 拉取一次真实 api stats。"
    } else {
        "Auto sampling reads real api stats every ${interval.label}."
    }
