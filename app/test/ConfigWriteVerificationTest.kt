package com.github.lightjunction.magicbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigWriteVerificationTest {
    @Test
    fun formatConfigWriteVerificationIncludesBothRuntimeChecks() {
        val verification =
            ConfigWriteVerification(
                service = CliResult(true, "service status", "running", "running"),
                health = CliResult(true, "health", "ok", "ok"),
            )

        val report = formatConfigWriteVerification(verification)

        assertTrue("service_status=ok" in report)
        assertTrue("health=ok" in report)
        assertTrue("verified=true" in report)
        assertTrue("running" in report)
        assertTrue("ok" in report)
    }

    @Test
    fun configWriteVerificationFailsIfAnyRuntimeCheckFails() {
        val verification =
            ConfigWriteVerification(
                service = CliResult(true, "service status", "running", "running"),
                health = CliResult(false, "health", "module disabled", "module disabled"),
            )

        val report = formatConfigWriteVerification(verification)

        assertFalse(verification.success)
        assertTrue("service_status=ok" in report)
        assertTrue("health=failed" in report)
        assertTrue("verified=false" in report)
    }
}
