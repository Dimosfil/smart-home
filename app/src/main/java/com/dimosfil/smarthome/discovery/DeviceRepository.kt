package com.dimosfil.smarthome.discovery

import com.dimosfil.smarthome.model.SmartDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DeviceRepository(
    private val bluetooth: DeviceDiscoverySource,
    private val wifi: DeviceDiscoverySource,
) {
    val devices: Flow<List<SmartDevice>> = combine(
        bluetooth.devices,
        wifi.devices,
        ::mergeDiscoveredDevices,
    )

    fun start(includeBluetooth: Boolean): List<String> = buildList {
        bluetooth.clear()
        wifi.clear()
        wifi.start().exceptionOrNull()?.message?.let(::add)
        if (includeBluetooth) {
            bluetooth.start().exceptionOrNull()?.message?.let(::add)
        }
    }

    fun stop() {
        bluetooth.stop()
        wifi.stop()
    }
}

internal fun mergeDiscoveredDevices(
    bluetooth: List<SmartDevice>,
    wifi: List<SmartDevice>,
): List<SmartDevice> = (bluetooth + wifi)
    .distinctBy(SmartDevice::id)
    .sortedWith(compareBy(SmartDevice::transport, SmartDevice::name))
