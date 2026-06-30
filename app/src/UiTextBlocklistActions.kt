package com.github.lightjunction.magicbox

fun UiText.blocklist(): String = if (this === UiText.zh) "拦截列表" else "Blocklist"

fun UiText.blocklistSummary(
    enabled: String,
    community: String,
): String =
    if (this === UiText.zh) {
        "总开关：$enabled，社区规则：$community。"
    } else {
        "Main: $enabled, community rules: $community."
    }

fun UiText.enabled(): String = if (this === UiText.zh) "启用" else "enabled"

fun UiText.disabled(): String = if (this === UiText.zh) "禁用" else "disabled"

fun UiText.blockDomainPlaceholder(): String =
    if (this === UiText.zh) "添加手工阻断域名后缀，例如 example.com" else "Add a manual blocked domain suffix, e.g. example.com"

fun UiText.blockUrlPlaceholder(): String =
    if (this === UiText.zh) "社区规则 URL，必须是 http(s)" else "Community rules URL, must be http(s)"

fun UiText.invalidBlocklistUrl(): String =
    if (this === UiText.zh) "拦截列表 URL 必须以 http:// 或 https:// 开头。" else "Blocklist URL must start with http:// or https://."

fun UiText.saveUrl(): String = if (this === UiText.zh) "保存 URL" else "Save URL"

fun UiText.updateBlocklist(): String = if (this === UiText.zh) "更新规则" else "Update rules"

fun UiText.blockDiff(): String = if (this === UiText.zh) "查看变更" else "Diff"

fun UiText.enableBlocklist(): String = if (this === UiText.zh) "启用拦截" else "Enable block"

fun UiText.disableBlocklist(): String = if (this === UiText.zh) "禁用拦截" else "Disable block"

fun UiText.enableCommunityBlocklist(): String = if (this === UiText.zh) "社区开" else "Community on"

fun UiText.disableCommunityBlocklist(): String = if (this === UiText.zh) "社区关" else "Community off"
