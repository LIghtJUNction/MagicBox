package com.github.lightjunction.magicbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlActionSafetyTest {
    @Test
    fun mutatingControlActionsRequireConfirmation() {
        listOf(
            "service start",
            "service ensure",
            "service restart sing-box",
            "service stop",
            "transparent set proxy",
            "transparent set external-tun",
            "transparent set hybrid",
            "transparent set tun",
            "config apply",
            "api close-all",
        ).forEach { command ->
            assertTrue("$command should require confirmation", controlActionRequiresConfirmation(command))
        }
    }

    @Test
    fun unknownControlActionsDoNotBecomeDangerousByDefault() {
        assertFalse(controlActionRequiresConfirmation("health"))
        assertFalse(controlActionRequiresConfirmation("api stats"))
    }

    @Test
    fun dangerConfirmationTextCoversNewConfirmedActions() {
        assertTrue("启动" in UiText.zh.confirmDangerAction("service start"))
        assertTrue("Ensure" in UiText.en.confirmDangerAction("service ensure"))
        assertTrue("重启" in UiText.zh.confirmDangerAction("service restart sing-box"))
        assertTrue("Apply config" in UiText.en.confirmDangerAction("config apply"))
    }
}
