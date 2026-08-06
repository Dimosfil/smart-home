package com.dimosfil.smarthome.discovery

import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRepositoryTest {
    @Test
    fun mergesAndDeduplicatesDevices() {
        val device = SmartDevice("ble:1", "Switch", DeviceTransport.Bluetooth, "1")
        val wifi = SmartDevice("wifi:1", "Switch", DeviceTransport.Wifi, "1")

        assertEquals(listOf(device, wifi), mergeDiscoveredDevices(listOf(device, device), listOf(wifi)))
    }

    @Test
    fun `new scan starts empty and publishes devices after discovery callbacks`() = runBlocking {
        val staleBluetooth = SmartDevice("ble:stale", "Stale", DeviceTransport.Bluetooth, "old")
        val discoveredWifi = SmartDevice("wifi:new", "Real switch", DeviceTransport.Wifi, "192.0.2.1:80")
        val bluetooth = RecordingDiscoverySource(listOf(staleBluetooth))
        val wifi = RecordingDiscoverySource(emptyList())
        val repository = DeviceRepository(bluetooth, wifi)

        repository.start(includeBluetooth = true)

        assertEquals(emptyList<SmartDevice>(), repository.devices.first())
        wifi.publish(discoveredWifi)
        assertEquals(listOf(discoveredWifi), repository.devices.first { it.isNotEmpty() })
    }

    @Test
    fun `wifi discovery continues when cloud bluetooth source is unavailable`() {
        val bluetooth = RecordingDiscoverySource(emptyList(), startError = "Cloud unavailable")
        val wifi = RecordingDiscoverySource(emptyList())
        val repository = DeviceRepository(bluetooth, wifi)

        val errors = repository.start(includeBluetooth = true)

        assertEquals(listOf("Cloud unavailable"), errors)
        assertEquals(1, wifi.startCount)
    }

    private class RecordingDiscoverySource(
        initial: List<SmartDevice>,
        private val startError: String? = null,
    ) : DeviceDiscoverySource {
        private val mutableDevices = MutableStateFlow(initial)
        var startCount: Int = 0
            private set

        override val devices: StateFlow<List<SmartDevice>> = mutableDevices

        override fun start(): Result<Unit> {
            startCount += 1
            return startError?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(Unit)
        }

        override fun stop() = Unit

        override fun clear() {
            mutableDevices.value = emptyList()
        }

        fun publish(device: SmartDevice) {
            mutableDevices.value = mutableDevices.value + device
        }
    }
}
