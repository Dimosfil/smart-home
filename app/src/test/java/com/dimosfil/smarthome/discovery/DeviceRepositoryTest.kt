package com.dimosfil.smarthome.discovery

import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRepositoryTest {
    @Test
    fun mergesAndDeduplicatesDevices() {
        val device = SmartDevice("ble:1", "Switch", DeviceTransport.Bluetooth, "1")
        val wifi = SmartDevice("wifi:1", "Switch", DeviceTransport.Wifi, "1")

        assertEquals(listOf(device, wifi), mergeDiscoveredDevices(listOf(device, device), listOf(wifi)))
    }
}
