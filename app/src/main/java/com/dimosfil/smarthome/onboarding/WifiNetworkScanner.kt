package com.dimosfil.smarthome.onboarding

data class WifiNetwork(
    val ssid: String,
    val signalLevelDbm: Int,
)

internal data class WifiScanObservation(
    val ssid: String,
    val frequencyMhz: Int,
    val signalLevelDbm: Int,
)

interface WifiNetworkScanner {
    suspend fun scan(): Result<List<WifiNetwork>>
}

internal fun selectAvailableWifiNetworks(
    observations: List<WifiScanObservation>,
): List<WifiNetwork> = observations
    .asSequence()
    .filter { it.ssid.isNotBlank() && it.frequencyMhz in WIFI_24_GHZ_RANGE_MHZ }
    .groupBy(WifiScanObservation::ssid)
    .map { (ssid, accessPoints) ->
        WifiNetwork(
            ssid = ssid,
            signalLevelDbm = accessPoints.maxOf(WifiScanObservation::signalLevelDbm),
        )
    }
    .sortedWith(compareByDescending<WifiNetwork> { it.signalLevelDbm }.thenBy { it.ssid.lowercase() })

private val WIFI_24_GHZ_RANGE_MHZ = 2_400..2_500
