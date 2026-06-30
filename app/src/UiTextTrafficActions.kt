package com.github.lightjunction.magicbox

fun UiText.trafficSamplerTitle(): String = if (this === UiText.zh) "流量采样" else "Traffic sampler"

fun UiText.trafficSamplerSummary(
    sampleCount: Int,
    failedSamples: Int,
): String =
    if (this === UiText.zh) {
        "$sampleCount 个真实样本，连续失败 $failedSamples 次。"
    } else {
        "$sampleCount real samples, $failedSamples consecutive failures."
    }

fun UiText.averageRate(): String = if (this === UiText.zh) "平均速率" else "Average"

fun UiText.peakRate(): String = if (this === UiText.zh) "峰值速率" else "Peak"

fun UiText.trafficOverview(): String = if (this === UiText.zh) "流量概览" else "Traffic overview"

fun UiText.trafficOverviewSummary(
    samples: Int,
    window: String,
): String =
    if (this === UiText.zh) {
        "$samples 个真实样本，窗口 $window；传输量按采样速率估算。"
    } else {
        "$samples real samples, $window window; transfer is estimated from sampled rates."
    }

fun UiText.currentRate(): String = if (this === UiText.zh) "当前速率" else "Current"

fun UiText.estimatedTransferred(): String = if (this === UiText.zh) "估算传输" else "Estimated transfer"

fun UiText.upAverage(): String = if (this === UiText.zh) "上行平均" else "Up average"

fun UiText.downAverage(): String = if (this === UiText.zh) "下行平均" else "Down average"

fun UiText.upPeak(): String = if (this === UiText.zh) "上行峰值" else "Up peak"

fun UiText.downPeak(): String = if (this === UiText.zh) "下行峰值" else "Down peak"

fun UiText.estimatedUp(): String = if (this === UiText.zh) "估算上传" else "Estimated up"

fun UiText.estimatedDown(): String = if (this === UiText.zh) "估算下载" else "Estimated down"

fun UiText.pause(): String = if (this === UiText.zh) "暂停" else "Pause"

fun UiText.resume(): String = if (this === UiText.zh) "恢复" else "Resume"

fun UiText.sampleNow(): String = if (this === UiText.zh) "采样" else "Sample"

fun UiText.copyTrafficSamples(): String = if (this === UiText.zh) "复制样本" else "Copy samples"

fun UiText.shareTrafficSamples(): String = if (this === UiText.zh) "分享样本" else "Share samples"

fun UiText.lastSampleFailure(): String = if (this === UiText.zh) "最近采样失败输出" else "Latest sample failure"

fun UiText.copySampleFailure(): String = if (this === UiText.zh) "复制失败输出" else "Copy failure"

fun UiText.shareSampleFailure(): String = if (this === UiText.zh) "分享失败输出" else "Share failure"
