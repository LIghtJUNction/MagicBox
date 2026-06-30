package com.github.lightjunction.magicbox

fun UiText.warpRouting(): String = if (this === UiText.zh) "WARP 路由" else "WARP routing"

fun UiText.warpRoutingSummary(count: Int): String =
    if (this === UiText.zh) {
        if (count == 0) "还没有域名固定走 WARP。" else "$count 个域名后缀固定走 WARP。"
    } else {
        if (count == 0) "No domain suffixes are pinned to WARP yet." else "$count domain suffixes pinned to WARP."
    }

fun UiText.warpDomainPlaceholder(): String = if (this === UiText.zh) "example.com" else "example.com"

fun UiText.addWarpDomain(): String = if (this === UiText.zh) "域名走 WARP" else "Route via WARP"

fun UiText.globalWarp(): String = if (this === UiText.zh) "全局 WARP" else "Global WARP"

fun UiText.ruleWarp(): String = if (this === UiText.zh) "规则出站" else "Rule outbound"

fun UiText.confirmWarpSelector(global: Boolean): String =
    if (this === UiText.zh) {
        if (global) "确认把 final 选择器切到 WARP？这会影响当前全局出站。" else "确认把 final 选择器恢复到 proxy？"
    } else {
        if (global) "Set the final selector to WARP? This changes the current global outbound." else "Restore the final selector to proxy?"
    }

fun UiText.noWarpRoutes(): String = if (this === UiText.zh) "没有 WARP 域名规则。" else "No WARP domain rules."

fun UiText.warpImportPlaceholder(): String =
    if (this === UiText.zh) "粘贴 WireGuard/WARP 配置，[Interface] 与 [Peer] 均需存在" else "Paste WireGuard/WARP config with [Interface] and [Peer]"

fun UiText.importWarpConfig(): String = if (this === UiText.zh) "导入 WARP 配置" else "Import WARP config"

fun UiText.warpImportMissing(): String =
    if (this === UiText.zh) "请先粘贴 WireGuard/WARP 配置。" else "Paste a WireGuard/WARP config first."
