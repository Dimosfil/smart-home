package com.dimosfil.smarthome.onboarding

import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceProfileRegistryTest {
    private val registry = DeviceProfileRegistry()

    @Test
    fun `tuya scan maps to real tuya adapter`() {
        val profile = registry.resolve(device("tuya-scan:uuid", DeviceTransport.Bluetooth))

        assertEquals(DeviceProfileRegistry.tuyaProfile, profile)
    }

    @Test
    fun `wifi discovery maps to prototype http adapter`() {
        val profile = registry.resolve(device("wifi:plug", DeviceTransport.Wifi))

        assertEquals(DeviceProfileRegistry.prototypeHttpProfile, profile)
    }

    @Test
    fun `unknown bluetooth device is not treated as compatible`() {
        val profile = registry.resolve(device("ble:unknown", DeviceTransport.Bluetooth))

        assertEquals(ProvisioningMode.Unsupported, profile.provisioningMode)
    }

    private fun device(id: String, transport: DeviceTransport) = SmartDevice(
        id = id,
        name = "Test device",
        transport = transport,
        endpoint = "test-endpoint",
    )
}
