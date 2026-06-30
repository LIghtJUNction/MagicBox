package com.github.lightjunction.magicbox

fun UiText.backup(): String = if (this === UiText.zh) "备份" else "Backup"

fun UiText.backupSummary(): String =
    if (this === UiText.zh) {
        "导出或恢复 MagicNet 订阅、规则、DNS、WARP 与透明代理配置。"
    } else {
        "Export or restore MagicNet subscriptions, rules, DNS, WARP, and transparent proxy settings."
    }

fun UiText.backupSafetyCodePlaceholder(): String =
    if (this === UiText.zh) "可选安全码；导出和恢复需使用同一个值" else "Optional safety code; use the same value for export and restore"

fun UiText.backupPayloadPlaceholder(): String =
    if (this === UiText.zh) "粘贴备份内容后点击恢复" else "Paste backup content, then tap Restore"

fun UiText.exportBackup(): String = if (this === UiText.zh) "导出备份" else "Export"

fun UiText.restoreBackup(): String = if (this === UiText.zh) "恢复备份" else "Restore"

fun UiText.confirmRestoreBackup(): String =
    if (this === UiText.zh) {
        "确认恢复这份备份？这会真实覆盖 MagicNet 的订阅、规则、DNS、WARP 与透明代理配置。"
    } else {
        "Restore this backup? This will overwrite MagicNet subscriptions, rules, DNS, WARP, and transparent proxy settings."
    }

fun UiText.backupPayloadMissing(): String =
    if (this === UiText.zh) "请先粘贴备份内容。" else "Paste backup content first."
