package com.github.lightjunction.magicbox

fun UiText.configValidation(): String = if (this === UiText.zh) "配置校验" else "Config validation"

fun UiText.configValidationSummary(): String =
    if (this === UiText.zh) "校验 sing-box 配置，或同步上游模板并保留当前订阅出站。"
    else "Validate sing-box config, or sync the upstream template while preserving subscription outbounds."

fun UiText.validateConfig(): String = if (this === UiText.zh) "校验配置" else "Validate"

fun UiText.syncTemplate(): String = if (this === UiText.zh) "同步模板" else "Sync template"

fun UiText.applyConfigNow(): String = if (this === UiText.zh) "应用配置" else "Apply config"

fun UiText.repairRuntime(): String = if (this === UiText.zh) "修复运行时" else "Repair runtime"
