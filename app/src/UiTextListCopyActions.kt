package com.github.lightjunction.magicbox

fun UiText.copyVisibleDomains(): String = if (this === UiText.zh) "复制当前域名" else "Copy visible domains"

fun UiText.shareVisibleDomains(): String = if (this === UiText.zh) "分享当前域名" else "Share visible domains"

fun UiText.copyVisiblePackages(): String = if (this === UiText.zh) "复制当前包名" else "Copy visible packages"

fun UiText.shareVisiblePackages(): String = if (this === UiText.zh) "分享当前包名" else "Share visible packages"
