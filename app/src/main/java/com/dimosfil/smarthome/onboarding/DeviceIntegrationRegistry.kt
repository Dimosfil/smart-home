package com.dimosfil.smarthome.onboarding

import com.dimosfil.smarthome.control.SwitchController

class DeviceIntegrationRegistry(
    private val profileRegistry: DeviceProfileRegistry,
    private val provisioners: Map<String, DeviceProvisioner> = emptyMap(),
    private val controllers: Map<String, SwitchController> = emptyMap(),
    private val removers: Map<String, DeviceRemover> = emptyMap(),
) {
    fun profile(profileId: String): DeviceProfile? = profileRegistry.byId(profileId)

    fun provisioner(profile: DeviceProfile): DeviceProvisioner? = provisioners[profile.id]

    fun controller(profile: DeviceProfile): SwitchController? = controllers[profile.controllerKey]

    fun remover(profile: DeviceProfile): DeviceRemover? = removers[profile.controllerKey]
}
