package com.dimosfil.smarthome.model

enum class DeviceTransport {
    Bluetooth,
    Wifi,
}

data class SmartDevice(
    val id: String,
    val name: String,
    val transport: DeviceTransport,
    val endpoint: String,
    val serviceType: String? = null,
    val signalStrength: Int? = null,
)
