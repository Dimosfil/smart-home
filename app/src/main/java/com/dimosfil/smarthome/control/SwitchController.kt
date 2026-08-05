package com.dimosfil.smarthome.control

import com.dimosfil.smarthome.model.SmartDevice

interface SwitchController {
    suspend fun readState(device: SmartDevice): Result<Boolean>

    suspend fun setPower(device: SmartDevice, enabled: Boolean): Result<Boolean>
}
