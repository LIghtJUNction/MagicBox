package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Test

class TransparentModeParserTest {
    @Test
    fun acceptsOnlyExplicitTunOrEbpfStatus() {
        assertEquals("tun", currentTransparentMode(result("mode=tun\nconfigured_mode=tun")))
        assertEquals("ebpf", currentTransparentMode(result("mode=ebpf\nconfigured_mode=ebpf")))
        assertEquals("", currentTransparentMode(result("mode=auto")))
        assertEquals("", currentTransparentMode(result("mode=hybrid")))
        assertEquals("", currentTransparentMode(null))
    }

    private fun result(output: String): CliResult =
        CliResult(
            success = true,
            command = "transparent status",
            output = output,
        )
}
