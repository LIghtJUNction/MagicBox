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
            "transparent apply",
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
        assertFalse(controlActionRequiresConfirmation("transparent set proxy"))
        assertFalse(controlActionRequiresConfirmation("transparent set external-tun"))
        assertFalse(controlActionRequiresConfirmation("transparent set hybrid"))
        assertFalse(controlActionRequiresConfirmation("transparent set tun"))
    }

    @Test
    fun dangerConfirmationTextCoversNewConfirmedActions() {
        assertTrue("启动" in UiText.zh.confirmDangerAction("service start"))
        assertTrue("Ensure" in UiText.en.confirmDangerAction("service ensure"))
        assertTrue("重启" in UiText.zh.confirmDangerAction("service restart sing-box"))
        assertTrue("TUN" in UiText.zh.confirmDangerAction("transparent apply"))
        assertTrue("Apply config" in UiText.en.confirmDangerAction("config apply"))
    }

    @Test
    fun legacyTransparentModesAreDisplayedAsTun() {
        for (mode in listOf("proxy", "external", "external-tun", "hybrid", "tun")) {
            val result = CliResult(success = true, command = "transparent status", output = "mode=$mode")
            assertTrue("$mode should normalize to tun", currentTransparentMode(result) == "tun")
        }
    }
}
