package com.dimosfil.smarthome.onboarding

import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice

enum class OnboardingScreen {
    DeviceList,
    Discovery,
    DeviceFound,
    Installation,
    NetworkSetup,
    Provisioning,
    Success,
    Control,
}

enum class ProvisioningMode {
    RequiresWifiCredentials,
    AlreadyNetworked,
    Unsupported,
}

data class DeviceProfile(
    val id: String,
    val displayName: String,
    val provisioningMode: ProvisioningMode,
    val controllerKey: String,
)

data class DeviceCandidate(
    val device: SmartDevice,
    val profile: DeviceProfile,
)

enum class ProvisioningStage {
    EstablishingConnection,
    SendingCredentials,
    ConnectingToRouter,
    ActivatingDevice,
    VerifyingDevice,
}

data class ProvisioningProgress(
    val stage: ProvisioningStage,
    val completedStages: Set<ProvisioningStage> = emptySet(),
)

data class ProvisioningRequest(
    val candidate: DeviceCandidate,
    val ssid: String,
    val password: String,
)

data class ProvisionedDevice(
    val device: SmartDevice,
    val initialPowerState: Boolean,
)

data class SavedDevice(
    val id: String,
    val profileId: String,
    val name: String,
    val room: String,
    val transport: DeviceTransport,
    val endpoint: String,
    val serviceType: String? = null,
    val powerState: Boolean? = null,
    val isOnline: Boolean = true,
) {
    fun asSmartDevice(): SmartDevice = SmartDevice(
        id = id,
        name = name,
        transport = transport,
        endpoint = endpoint,
        serviceType = serviceType,
    )
}

class DeviceProfileRegistry {
    fun resolve(device: SmartDevice): DeviceProfile = when {
        device.id.startsWith(TUYA_SCAN_ID_PREFIX) -> tuyaProfile
        device.transport == DeviceTransport.Wifi -> prototypeHttpProfile
        else -> unsupportedBluetoothProfile
    }

    fun byId(profileId: String): DeviceProfile? = profiles[profileId]

    companion object {
        const val HTTP_CONTROLLER = "prototype-http"
        const val TUYA_CONTROLLER = "tuya"
        const val UNSUPPORTED_CONTROLLER = "unsupported"
        const val TUYA_SCAN_ID_PREFIX = "tuya-scan:"

        val tuyaProfile = DeviceProfile(
            id = "tuya-smart-device",
            displayName = "Powered by Tuya",
            provisioningMode = ProvisioningMode.RequiresWifiCredentials,
            controllerKey = TUYA_CONTROLLER,
        )

        val prototypeHttpProfile = DeviceProfile(
            id = "prototype-http-switch",
            displayName = "Прототипный HTTP-выключатель",
            provisioningMode = ProvisioningMode.AlreadyNetworked,
            controllerKey = HTTP_CONTROLLER,
        )
        val unsupportedBluetoothProfile = DeviceProfile(
            id = "unrecognized-bluetooth-device",
            displayName = "Неизвестное Bluetooth-устройство",
            provisioningMode = ProvisioningMode.Unsupported,
            controllerKey = UNSUPPORTED_CONTROLLER,
        )

        private val profiles = listOf(
            tuyaProfile,
            prototypeHttpProfile,
            unsupportedBluetoothProfile,
        ).associateBy(DeviceProfile::id)
    }
}
