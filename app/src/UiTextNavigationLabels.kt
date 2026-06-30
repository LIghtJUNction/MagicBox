package com.github.lightjunction.magicbox

fun UiText.pageLabel(page: MagicPage): String =
    when (page) {
        MagicPage.Stats -> "MagicNet"
        MagicPage.Rules -> if (this === UiText.zh) "规则" else "Rule"
        MagicPage.Apps -> if (this === UiText.zh) "应用" else "App"
        MagicPage.Tools -> if (this === UiText.zh) "工具" else "Tool"
    }

fun UiText.languageName(language: AppLanguage): String =
    when (language) {
        AppLanguage.English -> "English"
        AppLanguage.Chinese -> "中文"
    }

fun UiText.backgroundName(style: BackgroundStyle): String =
    when (style) {
        BackgroundStyle.Monet -> if (this === UiText.zh) "莫奈取色" else "Monet"
        BackgroundStyle.Ember -> if (this === UiText.zh) "火炬" else "Ember"
        BackgroundStyle.Aurora -> if (this === UiText.zh) "极光" else "Aurora"
        BackgroundStyle.Minimal -> if (this === UiText.zh) "极简" else "Minimal"
    }

fun UiText.ruleBucket(bucket: RuleBucket): String =
    when (bucket) {
        RuleBucket.Proxy -> if (this === UiText.zh) "代理" else "Proxy"
        RuleBucket.Direct -> if (this === UiText.zh) "直连" else "Direct"
        RuleBucket.Block -> if (this === UiText.zh) "阻断" else "Block"
        RuleBucket.Warp -> "WARP"
    }

fun UiText.appTarget(target: AppTarget): String =
    when (target) {
        AppTarget.Proxy -> if (this === UiText.zh) "代理" else "Proxy"
        AppTarget.Bypass -> if (this === UiText.zh) "绕过" else "Bypass"
    }

fun UiText.appMode(mode: String): String =
    when (mode.lowercase()) {
        "blacklist" -> if (this === UiText.zh) "黑名单" else "Blacklist"
        "whitelist" -> if (this === UiText.zh) "白名单" else "Whitelist"
        else -> mode.ifBlank { unknown }
    }

fun UiText.cliState(value: String): String =
    when (value.lowercase()) {
        "executable" -> executable
        "missing" -> missing
        else -> value.ifBlank { unknown }
    }

