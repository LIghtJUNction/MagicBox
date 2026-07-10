package com.github.lightjunction.magicbox

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkSnapshotTest {
    @Test
    fun parseNetworkSnapshotSummaryCountsTopologySections() {
        val summary =
            parseNetworkSnapshotSummary(
                """
                MagicNet network topology
                module=/data/adb/modules/MagicNet

                [interfaces]
                1: lo    inet 127.0.0.1/8 scope host lo
                2: wlan0 inet 192.0.2.2/24 brd 192.0.2.255 scope global wlan0

                [routes]
                ip rule:
                0: from all lookup local
                10000: from all fwmark 0x1 lookup 100
                ip route:
                default via 192.0.2.1 dev wlan0 table 100
                192.0.2.0/24 dev wlan0 proto kernel scope link

                [forwarding]
                -A PREROUTING -p tcp -j REDIRECT
                """.trimIndent(),
            )

        assertEquals(NetworkSnapshotSummary(2, 2, 2, 1), summary)
    }

    @Test
    fun parseNetworkSnapshotSummaryCountsRouteOnlyFallback() {
        val summary =
            parseNetworkSnapshotSummary(
                """
                ip rule:
                0: from all lookup local
                ip route:
                default dev wlan0
                """.trimIndent(),
            )

        assertEquals(NetworkSnapshotSummary(0, 1, 1, 0), summary)
    }
}
