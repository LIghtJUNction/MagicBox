package com.github.lightjunction.magicbox

enum class TrafficAlertThreshold(
    val sustainedBytesPerSecond: Float,
    val spikeBytesPerSecond: Float,
) {
    Sensitive(256 * 1024f, 128 * 1024f),
    Balanced(1024 * 1024f, 256 * 1024f),
    Heavy(5 * 1024 * 1024f, 1024 * 1024f);

    companion object {
        fun fromName(name: String?): TrafficAlertThreshold =
            entries.firstOrNull { it.name == name } ?: Balanced
    }
}

fun UiText.trafficAlertThreshold(): String = if (this === UiText.zh) "告警阈值" else "Alert threshold"

fun UiText.trafficAlertThresholdName(threshold: TrafficAlertThreshold): String =
    when (threshold) {
        TrafficAlertThreshold.Sensitive -> if (this === UiText.zh) "灵敏" else "Sensitive"
        TrafficAlertThreshold.Balanced -> if (this === UiText.zh) "标准" else "Balanced"
        TrafficAlertThreshold.Heavy -> if (this === UiText.zh) "高流量" else "Heavy"
    }

fun UiText.trafficAlertThresholdSummary(threshold: TrafficAlertThreshold): String =
    if (this === UiText.zh) {
        "持续高流量阈值 ${formatRate(threshold.sustainedBytesPerSecond)}，突发基线 ${formatRate(threshold.spikeBytesPerSecond)}。"
    } else {
        "Sustained threshold ${formatRate(threshold.sustainedBytesPerSecond)}, spike floor ${formatRate(threshold.spikeBytesPerSecond)}."
    }
