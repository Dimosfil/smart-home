package com.dimosfil.smarthome.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiNetworkScannerTest {
    @Test
    fun `keeps visible 2 point 4 GHz networks and strongest access point per ssid`() {
        val networks = selectAvailableWifiNetworks(
            listOf(
                WifiScanObservation("Home", 2_412, -72),
                WifiScanObservation("Home", 2_437, -44),
                WifiScanObservation("Guest", 2_462, -60),
                WifiScanObservation("Five GHz", 5_180, -30),
                WifiScanObservation("", 2_412, -20),
            ),
        )

        assertEquals(
            listOf(
                WifiNetwork("Home", -44),
                WifiNetwork("Guest", -60),
            ),
            networks,
        )
    }
}
