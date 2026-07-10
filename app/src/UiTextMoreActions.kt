package com.github.lightjunction.magicbox

fun UiText.commandHistoryTitle(): String = if (this === UiText.zh) "最近命令" else "Recent commands"

fun UiText.commandHistorySummary(count: Int): String =
    if (this === UiText.zh) {
        if (count == 0) "保存最近 8 条真实执行命令。" else "$count 条命令记录。"
    } else {
        if (count == 0) "Keeps the latest 8 real commands." else "$count command records."
    }

fun UiText.commandHistoryDetail(
    time: String,
    summary: String,
): String = "$time · $summary"

fun UiText.noCommandHistory(): String = if (this === UiText.zh) "还没有命令记录。" else "No command history yet."

fun UiText.rerunCommand(): String = if (this === UiText.zh) "复跑" else "Run again"

fun UiText.deleteCommand(): String = if (this === UiText.zh) "删除" else "Delete"

fun UiText.confirmDeleteCommand(): String = if (this === UiText.zh) "确认删除这条命令记录？" else "Delete this command record?"

fun UiText.copyCommandOutput(): String = if (this === UiText.zh) "复制记录" else "Copy record"

fun UiText.shareCommandOutput(): String = if (this === UiText.zh) "分享记录" else "Share record"

fun UiText.confirmClearCommandHistory(count: Int): String =
    if (this === UiText.zh) "确认清空 $count 条最近命令？" else "Clear $count recent commands?"

fun UiText.confirm(): String = if (this === UiText.zh) "确认" else "Confirm"

fun UiText.cancel(): String = if (this === UiText.zh) "取消" else "Cancel"

fun UiText.clear(): String = if (this === UiText.zh) "清空" else "Clear"

fun UiText.copyReport(): String = if (this === UiText.zh) "复制" else "Copy"

fun UiText.shareReport(): String = if (this === UiText.zh) "分享" else "Share"

fun UiText.copyToolOutput(): String = if (this === UiText.zh) "复制输出" else "Copy output"

fun UiText.shareToolOutput(): String = if (this === UiText.zh) "分享输出" else "Share output"

fun UiText.connectivityTest(): String = if (this === UiText.zh) "连通性" else "Connectivity"

fun UiText.openSingBoxPanel(): String = if (this === UiText.zh) "面板" else "Panel"

fun UiText.singBoxApiNotReady(): String = if (this === UiText.zh) "sing-box API 未就绪。" else "sing-box API is not ready."

fun UiText.singBoxPanelMissing(): String = if (this === UiText.zh) "未读取到 sing-box 面板入口。" else "sing-box WebUI entry was not reported."

fun UiText.openedSingBoxPanel(): String = if (this === UiText.zh) "已打开 sing-box 面板。" else "Opened sing-box WebUI."

fun UiText.nodes(): String = if (this === UiText.zh) "节点" else "Nodes"

fun UiText.loadingNodes(): String = if (this === UiText.zh) "正在读取节点清单。" else "Loading node list."

fun UiText.noNodes(): String = if (this === UiText.zh) "没有读取到订阅节点。" else "No subscription nodes were found."

fun UiText.nodeCount(count: Int): String =
    if (this === UiText.zh) "已读取 $count 个可用节点。" else "$count available nodes found."

fun UiText.networkSnapshot(): String = if (this === UiText.zh) "网络快照" else "Network snapshot"

fun UiText.networkSnapshotHint(): String =
    if (this === UiText.zh) "按需读取接口、路由和转发表快照。" else "Read interfaces, routes, and forwarding rules on demand."

fun UiText.networkSnapshotLoaded(): String = if (this === UiText.zh) "已读取拓扑和路由快照。" else "Loaded topology and route snapshot."

fun UiText.routeSnapshotLoaded(): String = if (this === UiText.zh) "拓扑不可用，已读取路由快照。" else "Topology unavailable; loaded route snapshot."

fun UiText.networkSnapshotFailed(): String = if (this === UiText.zh) "网络快照读取失败。" else "Network snapshot failed."

fun UiText.dnsSettings(): String = if (this === UiText.zh) "DNS" else "DNS"

fun UiText.loadingDns(): String = if (this === UiText.zh) "正在读取 DNS 状态。" else "Loading DNS status."

fun UiText.dnsProfileSummary(
    profile: String,
    transport: String,
): String =
    if (this === UiText.zh) "当前配置：$profile，传输：$transport。" else "Current profile: $profile, transport: $transport."

fun UiText.dnsEndpoint(
    primary: String,
    secondary: String,
): String =
    if (this === UiText.zh) {
        "主 DNS：${primary.ifBlank { "默认" }}${secondary.ifBlank { "" }.let { if (it.isBlank()) "" else " / 备用：$it" }}"
    } else {
        "Primary: ${primary.ifBlank { "default" }}${secondary.ifBlank { "" }.let { if (it.isBlank()) "" else " / Secondary: $it" }}"
    }

fun UiText.testDns(): String = if (this === UiText.zh) "测试 DNS" else "Test DNS"

fun UiText.dnsTestDomainPlaceholder(): String =
    if (this === UiText.zh) "测试域名，例如 www.gstatic.com" else "Test domain, e.g. www.gstatic.com"

fun UiText.confirmDnsProfile(profile: String): String =
    if (this === UiText.zh) "确认切换到 $profile？这会应用 DNS 配置并重启当前核心。" else "Switch to $profile? This applies DNS settings and restarts the current core."

fun UiText.warp(): String = "WARP"

fun UiText.loadingWarp(): String = if (this === UiText.zh) "正在读取 WARP 状态。" else "Loading WARP status."

fun UiText.warpNotConfigured(): String =
    if (this === UiText.zh) "尚未导入 WARP/WireGuard 配置。" else "WARP/WireGuard config has not been imported."

fun UiText.warpConfigured(tag: String): String =
    if (this === UiText.zh) "$tag 已配置，当前未启用。" else "$tag is configured but disabled."

fun UiText.warpEnabled(tag: String): String = if (this === UiText.zh) "$tag 已启用。" else "$tag is enabled."

fun UiText.warpEndpoint(
    endpoint: String,
    addresses: String,
    allowedIps: String,
): String =
    if (this === UiText.zh) {
        "端点：${endpoint.ifBlank { "未知" }}，地址 $addresses，路由 $allowedIps"
    } else {
        "Endpoint: ${endpoint.ifBlank { "unknown" }}, addresses $addresses, routes $allowedIps"
    }

fun UiText.testWarp(): String = if (this === UiText.zh) "测试" else "Test"

fun UiText.enableWarp(): String = if (this === UiText.zh) "启用" else "Enable"

fun UiText.disableWarp(): String = if (this === UiText.zh) "禁用" else "Disable"

fun UiText.confirmWarpAction(enable: Boolean): String =
    if (this === UiText.zh) {
        if (enable) "确认启用 WARP？这会应用配置并重启当前核心。" else "确认禁用 WARP？这会应用配置并重启当前核心。"
    } else {
        if (enable) "Enable WARP? This applies config and restarts the current core." else "Disable WARP? This applies config and restarts the current core."
    }

fun UiText.subscriptionConfigured(count: Int): String =
    if (this === UiText.zh) "已配置 $count 个 sing-box 订阅源。" else "$count sing-box subscription sources configured."

fun UiText.subscriptionEmpty(): String = if (this === UiText.zh) "未配置 sing-box 订阅源。" else "No sing-box subscription source configured."

fun UiText.subscriptionPrimaryConfigured(): String =
    if (this === UiText.zh) "sing-box 主订阅已配置" else "sing-box primary subscription configured"

fun UiText.subscriptionSourceLine(
    index: Int,
    configured: Boolean,
): String =
    if (this === UiText.zh) {
        "sing-box #$index: ${if (configured) "已配置" else "空"}"
    } else {
        "sing-box #$index: ${if (configured) "configured" else "empty"}"
    }

fun UiText.subscriptionSources(): String = if (this === UiText.zh) "订阅源" else "Subscription sources"

fun UiText.subscriptionInputPlaceholder(): String =
    if (this === UiText.zh) "粘贴合法 http(s) 订阅 URL；每行一个，也支持空格分隔" else "Paste legal http(s) subscription URLs; one per line, or split with spaces"

fun UiText.saveSubscriptions(): String = if (this === UiText.zh) "保存订阅" else "Save subs"

fun UiText.subscriptionSaved(count: Int): String =
    if (this === UiText.zh) "已保存 $count 个 sing-box 订阅源。" else "Saved $count sing-box subscription sources."

fun UiText.noValidSubscriptions(): String = if (this === UiText.zh) "没有可保存的有效订阅 URL。" else "No valid subscription URLs to save."

fun UiText.invalidSubscriptionsLabel(): String = if (this === UiText.zh) "无效订阅" else "Invalid subscriptions"

fun UiText.duplicateSubscriptionsLabel(): String = if (this === UiText.zh) "重复订阅" else "Duplicate subscriptions"

fun UiText.overflowSubscriptionsLabel(limit: Int): String =
    if (this === UiText.zh) "超过本次上限 $limit，未写入" else "Over the $limit URL limit; not saved"

fun UiText.showFullReport(): String = if (this === UiText.zh) "完整预览" else "Full preview"

fun UiText.collapseReport(): String = if (this === UiText.zh) "折叠预览" else "Collapse"

fun UiText.showAllHealthEntries(count: Int): String = if (this === UiText.zh) "查看全部 $count 项" else "Show all $count checks"

fun UiText.collapseHealthEntries(): String = if (this === UiText.zh) "折叠健康项" else "Collapse checks"

fun UiText.copied(): String = if (this === UiText.zh) "已复制" else "Copied"

fun UiText.bulkPackages(): String = if (this === UiText.zh) "批量包名" else "Bulk packages"

fun UiText.bulkPackagePlaceholder(): String =
    if (this === UiText.zh) "每行一个包名，也支持空格、逗号或分号分隔" else "One package per line, or split with spaces, commas, or semicolons"

fun UiText.importPackages(): String = if (this === UiText.zh) "导入应用" else "Import apps"

fun UiText.noValidPackages(): String = if (this === UiText.zh) "没有可导入的有效包名。" else "No valid packages to import."

fun UiText.noWritablePackages(): String = if (this === UiText.zh) "没有需要写入的新包名。" else "No new packages to write."

fun UiText.packageBulkImportSummary(
    added: Int,
    skipped: Int,
): String =
    if (this === UiText.zh) "已导入 $added 个应用，跳过 $skipped 个未写入项。" else "Imported $added apps, skipped $skipped unwritten items."

fun UiText.invalidPackagesLabel(): String = if (this === UiText.zh) "无效包名" else "Invalid packages"

fun UiText.duplicatePackagesLabel(): String = if (this === UiText.zh) "重复包名" else "Duplicate packages"

fun UiText.existingPackagesLabel(): String = if (this === UiText.zh) "已存在" else "Already present"

fun UiText.overflowPackagesLabel(limit: Int): String =
    if (this === UiText.zh) "超过本次上限 $limit，未处理" else "Over the $limit item limit; not processed"

fun UiText.confirmDangerAction(command: String): String =
    if (this === UiText.zh) {
        when (command) {
            "service start" -> "确认启动 sing-box？启动会接管 MagicNet 运行状态。"
            "service ensure" -> "确认确保服务运行？这可能会启动或修复 sing-box。"
            "service restart sing-box" -> "确认重启 sing-box？当前连接可能会短暂中断。"
            "service stop" -> "确认停止 sing-box？停止后流量可能无法继续通过 MagicNet。"
            "config apply" -> "确认应用配置？配置生效时可能会重启相关运行状态。"
            "api close-all" -> "确认关闭当前连接？现有代理连接会被断开。"
            "transparent set proxy" -> "确认切换到 Proxy 模式？当前连接可能会短暂中断。"
            "transparent set external-tun" -> "确认切换到外部 TUN 模式？当前连接可能会短暂中断。"
            "transparent set hybrid" -> "确认切换到 Hybrid 模式？当前连接可能会短暂中断。"
            "transparent set tun" -> "确认切换到 TUN 模式？当前连接可能会短暂中断。"
            else -> "确认执行：$command"
        }
    } else {
        when (command) {
            "service start" -> "Start sing-box? This changes MagicNet runtime state."
            "service ensure" -> "Ensure the service is running? This may start or repair sing-box."
            "service restart sing-box" -> "Restart sing-box? Current connections may briefly drop."
            "service stop" -> "Stop sing-box? Traffic may stop using MagicNet until it is started again."
            "config apply" -> "Apply config? Runtime state may restart while the config takes effect."
            "api close-all" -> "Close current connections? Existing proxy connections will be dropped."
            "transparent set proxy" -> "Switch to Proxy mode? Current connections may briefly drop."
            "transparent set external-tun" -> "Switch to External TUN mode? Current connections may briefly drop."
            "transparent set hybrid" -> "Switch to Hybrid mode? Current connections may briefly drop."
            "transparent set tun" -> "Switch to TUN mode? Current connections may briefly drop."
            else -> "Confirm: $command"
        }
    }
