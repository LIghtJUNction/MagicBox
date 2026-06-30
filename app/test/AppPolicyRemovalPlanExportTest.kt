package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPolicyRemovalPlanExportTest {
    @Test
    fun formatAppPolicyRemovalPlanIncludesTargetCountAndCommands() {
        val report =
            formatAppPolicyRemovalPlan(
                AppTarget.Bypass,
                listOf("com.chat.app", "", "com.video.app"),
            )

        assertTrue("MagicBox app policy removal plan" in report)
        assertTrue("target: bypass" in report)
        assertTrue("count: 2" in report)
        assertTrue("1. com.chat.app" in report)
        assertTrue("command: app remove com.chat.app bypass" in report)
        assertTrue("2. com.video.app" in report)
        assertTrue("command: app remove com.video.app bypass" in report)
        assertFalse("\n2. \n" in report)
    }

    @Test
    fun appPolicyRemovalPlanShareTitleUsesTarget() {
        assertEquals("MagicBox proxy app policy removal plan", appPolicyRemovalPlanShareTitle(AppTarget.Proxy))
    }

    @Test
    fun appPolicyRemovalPlanTextFormatsLocalizedText() {
        assertEquals("复制移除计划", UiText.zh.copyAppPolicyRemovalPlan())
        assertEquals("Copy removal plan", UiText.en.copyAppPolicyRemovalPlan())
        assertEquals("分享移除计划", UiText.zh.shareAppPolicyRemovalPlan())
        assertEquals("Share removal plan", UiText.en.shareAppPolicyRemovalPlan())
    }
}
