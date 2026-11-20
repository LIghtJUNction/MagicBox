package com.github.lightjunction.magicbox

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SystemProxyConfigTest {
    private fun config(mode: String) = makeCoreConfig(
        JSONObject("""{"outbounds":[{"type":"http","tag":"Fixture","server":"127.0.0.1","server_port":18080}]}"""),
        mode, "Fixture", "fixture-only", "/sys/fs/cgroup",
    )

    @Test fun unprivilegedSystemProxyDoesNotRequireNetlinkMonitor() {
        val value = config("system")
        assertFalse(value.getJSONObject("route").getBoolean("auto_detect_interface"))
        assertEquals(1, value.getJSONArray("inbounds").length())
        assertEquals("mixed", value.getJSONArray("inbounds").getJSONObject(0).getString("type"))
    }

    @Test fun transparentRootModesStillRequireInterfaceMonitor() {
        for (mode in listOf("tun", "ebpf")) {
            val value = config(mode)
            assertTrue(value.getJSONObject("route").getBoolean("auto_detect_interface"))
            assertEquals(mode, value.getJSONArray("inbounds").getJSONObject(1).getString("type"))
        }
    }
}
