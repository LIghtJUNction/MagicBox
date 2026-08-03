package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSummaryParserTest {
    @Test
    fun parseAppSummaryReadsProxyDirectAndBypassSections() {
        val summary =
            parseAppSummary(
                """
                mode=whitelist

                PROXY APPS:
                com.example.proxy

                Direct Apps:
                com.example.direct

                bypass apps:
                com.example.bypass
                """.trimIndent(),
            )

        assertEquals("whitelist", summary.mode)
        assertEquals(listOf("com.example.proxy"), summary.proxy)
        assertEquals(listOf("com.example.direct"), summary.direct)
        assertEquals(listOf("com.example.bypass"), summary.bypass)
    }

    @Test
    fun failedRecommendationResultPreservesCliFailure() {
        val failure = CliResult(false, "magicnet app recommendations", "permission denied")

        assertEquals(failure, failedRecommendationResult(failure))
        assertNull(failedRecommendationResult(CliResult(true, failure.command, "")))
    }
}
