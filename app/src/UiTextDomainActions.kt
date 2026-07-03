package com.github.lightjunction.magicbox

fun UiText.bulkDomains(): String = if (this === UiText.zh) "批量域名" else "Bulk domains"

fun UiText.bulkDomainPlaceholder(): String =
    if (this === UiText.zh) "用空格、逗号或分号分隔域名" else "Split domains with spaces, commas, or semicolons"

fun UiText.importDomains(): String = if (this === UiText.zh) "导入域名" else "Import domains"

fun UiText.removeVisibleDomains(): String = if (this === UiText.zh) "移除当前可见" else "Remove visible"

fun UiText.confirmRemoveVisibleDomains(
    count: Int,
    bucket: String,
): String =
    if (this === UiText.zh) "确认从$bucket 规则移除当前可见的 $count 个域名？" else "Remove the $count visible domains from $bucket?"

fun UiText.noValidDomains(): String = if (this === UiText.zh) "没有可导入的有效域名。" else "No valid domains to import."

fun UiText.noWritableDomains(): String = if (this === UiText.zh) "没有需要写入的新域名。" else "No new domains to write."

fun UiText.domainBulkImportSummary(
    added: Int,
    skipped: Int,
): String =
    if (this === UiText.zh) "已导入 $added 个域名，跳过 $skipped 个未写入项。" else "Imported $added domains, skipped $skipped unwritten items."

fun UiText.domainPolicyRemoveSummary(
    removed: Int,
    failed: Int,
): String =
    if (this === UiText.zh) "已移除 $removed 个域名规则，失败 $failed 个。" else "Removed $removed domain rules, failed $failed."

fun UiText.invalidDomainsLabel(): String = if (this === UiText.zh) "无效域名" else "Invalid domains"

fun UiText.duplicateDomainsLabel(): String = if (this === UiText.zh) "重复域名" else "Duplicate domains"

fun UiText.existingDomainsLabel(): String = if (this === UiText.zh) "已存在" else "Already present"

fun UiText.failedDomainsLabel(): String = if (this === UiText.zh) "写入失败" else "Failed writes"

fun UiText.failedDomainRemovalsLabel(): String = if (this === UiText.zh) "移除失败" else "Failed removals"

fun UiText.overflowDomainsLabel(limit: Int): String =
    if (this === UiText.zh) "超过本次上限 $limit，未处理" else "Over the $limit item limit; not processed"

fun UiText.domainInspector(): String = if (this === UiText.zh) "域名检查" else "Domain check"

fun UiText.domainInspectorEmpty(): String =
    if (this === UiText.zh) "输入域名后可检查它是否已在自定义规则中。" else "Enter a domain to check whether it is already in custom rules."

fun UiText.domainInspectorInBuckets(buckets: String): String =
    if (this === UiText.zh) "已存在于：$buckets。" else "Already listed in: $buckets."

fun UiText.domainInspectorNotListed(): String =
    if (this === UiText.zh) "当前自定义规则中未找到，可写入目标规则。" else "Not found in custom rules; it can be added."

fun UiText.domainInspectorUnknown(): String =
    if (this === UiText.zh) "等待路由列表加载；仍可写入规则。" else "Waiting for the route list; rule writes are still available."
