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
            "transparent set tun",
            "transparent set ebpf",
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
        assertFalse(controlActionRequiresConfirmation("transparent set auto"))
    }

    @Test
    fun dangerConfirmationTextCoversNewConfirmedActions() {
        assertTrue("启动" in UiText.zh.confirmDangerAction("service start"))
        assertTrue("Ensure" in UiText.en.confirmDangerAction("service ensure"))
        assertTrue("重启" in UiText.zh.confirmDangerAction("service restart sing-box"))
        assertTrue("透明模式" in UiText.zh.confirmDangerAction("transparent apply"))
        assertTrue("eBPF" in UiText.zh.confirmDangerAction("transparent set ebpf"))
        assertTrue("TUN" in UiText.en.confirmDangerAction("transparent set tun"))
        assertTrue("Apply config" in UiText.en.confirmDangerAction("config apply"))
    }

    @Test
    fun transparentModesAreStrictlyReported() {
        for (mode in listOf("tun", "ebpf")) {
            val result = CliResult(success = true, command = "transparent status", output = "mode=$mode")
            assertTrue("$mode should be reported", currentTransparentMode(result) == mode)
        }
        for (mode in listOf("proxy", "external", "external-tun", "hybrid", "auto")) {
            val result = CliResult(success = true, command = "transparent status", output = "mode=$mode")
            assertTrue("$mode should be rejected", currentTransparentMode(result).isEmpty())
        }
    }
}
