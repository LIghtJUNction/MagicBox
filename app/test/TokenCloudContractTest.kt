package com.github.lightjunction.magicbox

import com.github.lightjunction.magicbox.reboot.*
import org.junit.Assert.*
import org.junit.Test

class TokenCloudContractTest {
    @Test fun unknownReadinessNeverBecomesConnected() {
        assertFalse(InputPolicy.isReady(1, true, "ready", null))
        assertFalse(InputPolicy.isReady(2, true, "ready", true))
        assertFalse(InputPolicy.isReady(1, false, "ready", true))
        assertFalse(InputPolicy.isReady(1, true, "running_unknown", true))
        assertTrue(InputPolicy.isReady(1, true, "ready", true))
    }
    @Test fun shellArgumentsAreSingleQuoted() {
        assertEquals("'a'\"'\"'b; $(id)'", InputPolicy.quote("a'b; $(id)"))
    }
    @Test fun oversizedOrBinarySubscriptionsAreRejected() {
        for (source in listOf("", " \n", "hello\u0000world", "x".repeat(InputPolicy.MAX_BYTES + 1))) {
            try { InputPolicy.validate(source); fail("invalid input accepted") } catch (_: BoxFailure) {}
        }
        assertEquals("vmess://example", InputPolicy.validate("  vmess://example \n"))
    }
    @Test fun cloudIsBoundedDeterministicAndSolidifies() {
        assertEquals(0f, TokenGeometry.spread(0f), 0.00001f)
        assertEquals(0f, TokenGeometry.spread(1f), 0.00001f)
        assertEquals(1f, TokenGeometry.spread(0.5f), 0.00001f)
        assertTrue(TokenGeometry.COUNT <= 128)
        repeat(TokenGeometry.COUNT) { i ->
            assertTrue(TokenGeometry.x(i) in 0f..1f)
            assertTrue(TokenGeometry.y(i) in 0f..1f)
            assertEquals(TokenGeometry.dx(i), TokenGeometry.dx(i), 0f)
            assertTrue(TokenGeometry.dy(i).isFinite())
        }
    }
    @Test fun packageIdsAreDistinctAndFlavorRoleIsExplicit() {
        if (BuildConfig.UI_ONLY) assertTrue(BuildConfig.APPLICATION_ID.endsWith(".ui"))
        else assertFalse(BuildConfig.APPLICATION_ID.endsWith(".ui"))
    }
}
