package com.dimosfil.smarthome.onboarding

import kotlinx.coroutines.flow.StateFlow

interface DeviceProvisioner {
    val progress: StateFlow<ProvisioningProgress?>

    suspend fun provision(request: ProvisioningRequest): Result<ProvisionedDevice>

    fun cancel()
}
