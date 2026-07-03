package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogsViewerCardTest {
    @Test
    fun parseLogLineCountAcceptsBoundaryValues() {
        assertEquals(20, parseLogLineCount("20"))
        assertEquals(120, parseLogLineCount(" 120 "))
        assertEquals(300, parseLogLineCount("300"))
    }

    @Test
    fun parseLogLineCountRejectsInvalidValues() {
        assertNull(parseLogLineCount(""))
        assertNull(parseLogLineCount("abc"))
        assertNull(parseLogLineCount("19"))
        assertNull(parseLogLineCount("301"))
    }
}
