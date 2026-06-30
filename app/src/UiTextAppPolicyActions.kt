package com.github.lightjunction.magicbox

fun UiText.stalePolicyTitle(): String = if (this === UiText.zh) "残留策略" else "Stale policies"

fun UiText.stalePolicySummary(
    count: Int,
    target: String,
): String =
    if (this === UiText.zh) "$target 名单中有 $count 个包名不在当前安装列表里。" else "$count packages in $target are not in the current installed-app list."

fun UiText.cleanStalePackages(): String = if (this === UiText.zh) "清理残留" else "Clean stale"

fun UiText.removeVisiblePackages(): String = if (this === UiText.zh) "移除当前可见" else "Remove visible"

fun UiText.copyAppPolicyRemovalPlan(): String = if (this === UiText.zh) "复制移除计划" else "Copy removal plan"

fun UiText.shareAppPolicyRemovalPlan(): String = if (this === UiText.zh) "分享移除计划" else "Share removal plan"

fun UiText.appInfo(): String = if (this === UiText.zh) "详情" else "Info"

fun UiText.openApp(): String = if (this === UiText.zh) "打开" else "Open"

fun UiText.confirmRemoveVisiblePackages(
    count: Int,
    target: String,
): String =
    if (this === UiText.zh) "确认从$target 名单移除当前可见的 $count 个包名？" else "Remove the $count visible packages from $target?"

fun UiText.confirmStaleCleanup(
    count: Int,
    target: String,
): String =
    if (this === UiText.zh) "确认从$target 名单移除 $count 个未安装包名？" else "Remove $count uninstalled packages from $target?"

fun UiText.confirmRecommendedBypass(count: Int): String =
    if (this === UiText.zh) "确认把 $count 个推荐应用加入 Bypass？这会批量改写应用分流策略。" else "Add $count recommended apps to Bypass? This will rewrite app routing policies."

fun UiText.noStalePackages(): String = if (this === UiText.zh) "没有可清理的残留包名。" else "No stale packages to clean."

fun UiText.staleCleanupSummary(
    removed: Int,
    failed: Int,
): String =
    if (this === UiText.zh) "已移除 $removed 个残留策略，失败 $failed 个。" else "Removed $removed stale policies, failed $failed."

fun UiText.appPolicyRemoveSummary(
    removed: Int,
    failed: Int,
): String =
    if (this === UiText.zh) "已移除 $removed 个应用策略，失败 $failed 个。" else "Removed $removed app policies, failed $failed."

fun UiText.failedPackagesLabel(): String = if (this === UiText.zh) "处理失败" else "Failed packages"

fun UiText.staleCleanupHint(): String =
    if (this === UiText.zh) "仅使用系统完整安装包列表判断，不使用搜索结果或 CLI 回退列表，避免误删仍已安装的策略。"
    else "Uses the full system package list only, not search results or CLI fallback output, to avoid removing installed policies."

fun UiText.staleCleanupUnavailable(): String =
    if (this === UiText.zh) "等待完整安装包列表；搜索过滤时不会清理残留。"
    else "Waiting for the full package list; stale cleanup is disabled while search is filtered."

fun UiText.packageInspector(): String = if (this === UiText.zh) "包名检查" else "Package check"

fun UiText.packageInspectorEmpty(): String =
    if (this === UiText.zh) "输入包名后可检查安装状态和当前策略。" else "Enter a package name to check install state and current policy."

fun UiText.packageInspectorInstalled(): String =
    if (this === UiText.zh) "已安装，尚未加入当前策略。" else "Installed and not yet in policy."

fun UiText.packageInspectorNotInstalled(): String =
    if (this === UiText.zh) "当前完整安装列表中未找到这个包名。" else "This package is not in the current full install list."

fun UiText.packageInspectorUnknown(): String =
    if (this === UiText.zh) "正在等待完整安装列表；仍可写入策略。" else "Waiting for the full install list; policy write is still available."

fun UiText.packageInspectorInPolicy(target: String): String =
    if (this === UiText.zh) "已在$target 名单中。" else "Already in $target."
