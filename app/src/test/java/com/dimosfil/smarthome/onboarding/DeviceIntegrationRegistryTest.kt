package com.dimosfil.smarthome.onboarding

import com.dimosfil.smarthome.model.SmartDevice
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceIntegrationRegistryTest {
    @Test
    fun `tuya profile resolves remote removal adapter`() {
        val remover = object : DeviceRemover {
            override suspend fun remove(device: SmartDevice): Result<Unit> = Result.success(Unit)
        }
        val registry = DeviceIntegrationRegistry(
            profileRegistry = DeviceProfileRegistry(),
            removers = mapOf(DeviceProfileRegistry.TUYA_CONTROLLER to remover),
        )

        assertSame(remover, registry.remover(DeviceProfileRegistry.tuyaProfile))
    }
}
