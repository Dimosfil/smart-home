package com.dimosfil.smarthome.onboarding

import com.dimosfil.smarthome.model.SmartDevice

interface DeviceRemover {
    suspend fun remove(device: SmartDevice): Result<Unit>
}
