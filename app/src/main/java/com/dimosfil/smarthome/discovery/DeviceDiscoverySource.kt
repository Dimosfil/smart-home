package com.dimosfil.smarthome.discovery

import com.dimosfil.smarthome.model.SmartDevice
import kotlinx.coroutines.flow.StateFlow

interface DeviceDiscoverySource {
    val devices: StateFlow<List<SmartDevice>>

    fun start(): Result<Unit>

    fun stop()

    fun clear()
}
