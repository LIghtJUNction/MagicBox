package com.github.lightjunction.magicbox

fun formatAppPolicyRemovalPlan(
    target: AppTarget,
    packageNames: List<String>,
): String {
    val visible = packageNames.filter { it.isNotBlank() }
    return buildString {
        appendLine("MagicBox app policy removal plan")
        appendLine("target: ${target.cli}")
        appendLine("count: ${visible.size}")
        appendLine()
        visible.forEachIndexed { index, pkg ->
            appendLine("${index + 1}. $pkg")
            appendLine("   command: app remove $pkg ${target.cli}")
        }
    }.trim()
}

fun appPolicyRemovalPlanShareTitle(target: AppTarget): String = "MagicBox ${target.cli} app policy removal plan"

