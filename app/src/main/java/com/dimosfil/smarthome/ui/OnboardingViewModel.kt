package com.dimosfil.smarthome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dimosfil.smarthome.discovery.DeviceRepository
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import com.dimosfil.smarthome.onboarding.DeviceCandidate
import com.dimosfil.smarthome.onboarding.DeviceIntegrationRegistry
import com.dimosfil.smarthome.onboarding.DeviceProfileRegistry
import com.dimosfil.smarthome.onboarding.OnboardingEvent
import com.dimosfil.smarthome.onboarding.OnboardingScreen
import com.dimosfil.smarthome.onboarding.OnboardingStateMachine
import com.dimosfil.smarthome.onboarding.ProvisioningMode
import com.dimosfil.smarthome.onboarding.ProvisioningProgress
import com.dimosfil.smarthome.onboarding.ProvisioningRequest
import com.dimosfil.smarthome.onboarding.SavedDevice
import com.dimosfil.smarthome.onboarding.WifiNetwork
import com.dimosfil.smarthome.onboarding.WifiNetworkScanner
import com.dimosfil.smarthome.persistence.DeviceStore
import com.dimosfil.smarthome.tuya.TuyaIntegration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class OnboardingUiState(
    val screen: OnboardingScreen = OnboardingScreen.DeviceList,
    val savedDevices: List<SavedDevice> = emptyList(),
    val candidates: List<DeviceCandidate> = emptyList(),
    val selectedCandidate: DeviceCandidate? = null,
    val selectedSavedDeviceId: String? = null,
    val isScanning: Boolean = false,
    val isBusy: Boolean = false,
    val ssid: String = "",
    val password: String = "",
    val availableWifiNetworks: List<WifiNetwork> = emptyList(),
    val isWifiScanning: Boolean = false,
    val wifiScanError: String? = null,
    val provisioningProgress: ProvisioningProgress? = null,
    val successDeviceId: String? = null,
    val successName: String = "",
    val successRoom: String = "Гостиная",
    val powerState: Boolean? = null,
    val errorMessage: String? = null,
    val tuyaConfigured: Boolean = false,
    val tuyaLoggedIn: Boolean = false,
    val tuyaHomeReady: Boolean = false,
    val accountMessage: String? = null,
)

class OnboardingViewModel(
    private val repository: DeviceRepository,
    private val profileRegistry: DeviceProfileRegistry,
    private val integrations: DeviceIntegrationRegistry,
    private val deviceStore: DeviceStore,
    private val wifiNetworkScanner: WifiNetworkScanner,
    private val tuya: TuyaIntegration,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingUiState())
    private var operationJob: Job? = null
    private var wifiScanJob: Job? = null
    private var shellyApScanJob: Job? = null
    private var provisioningProgressJob: Job? = null
    private var discoveredDevices: List<SmartDevice> = emptyList()
    private var shellyApDevices: List<SmartDevice> = emptyList()

    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.devices.collect { devices ->
                discoveredDevices = devices
                publishCandidates()
            }
        }
        viewModelScope.launch {
            deviceStore.devices.collect { devices ->
                mutableState.update { current -> current.copy(savedDevices = devices) }
            }
        }
        viewModelScope.launch {
            tuya.session.collect { session ->
                mutableState.update {
                    it.copy(
                        tuyaConfigured = session.configured,
                        tuyaLoggedIn = session.loggedIn,
                        tuyaHomeReady = session.ready,
                        accountMessage = session.error ?: if (session.ready) null else it.accountMessage,
                    )
                }
            }
        }
        viewModelScope.launch {
            if (tuya.session.value.configured) {
                mutableState.update { it.copy(accountMessage = "Автоматическое подключение к Tuya…") }
                tuya.connectAutomatically().onFailure { error ->
                    mutableState.update {
                        it.copy(accountMessage = error.message ?: "Не удалось подключиться к Tuya.")
                    }
                }
            }
        }
    }

    fun openDiscovery(includeBluetooth: Boolean) {
        val current = mutableState.value.screen
        val next = if (current == OnboardingScreen.DeviceList) {
            OnboardingStateMachine.transition(current, OnboardingEvent.AddDevice)
        } else {
            OnboardingScreen.Discovery
        }
        mutableState.update {
            it.copy(
                screen = next,
                selectedCandidate = null,
                errorMessage = null,
            )
        }
        startDiscovery(includeBluetooth)
    }

    fun startDiscovery(includeBluetooth: Boolean) {
        shellyApDevices = emptyList()
        mutableState.update {
            it.copy(
                candidates = emptyList(),
                isScanning = true,
                errorMessage = null,
            )
        }
        val errors = repository.start(includeBluetooth)
        mutableState.update {
            it.copy(errorMessage = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        }
        scanShellyAccessPoints()
    }

    fun stopDiscovery() {
        repository.stop()
        shellyApScanJob?.cancel()
        mutableState.update { it.copy(isScanning = false) }
    }

    private fun scanShellyAccessPoints() {
        shellyApScanJob?.cancel()
        shellyApScanJob = viewModelScope.launch {
            wifiNetworkScanner.scan().fold(
                onSuccess = { networks ->
                    shellyApDevices = networks
                        .filter { it.ssid.startsWith("shelly", ignoreCase = true) }
                        .map { network ->
                            SmartDevice(
                                id = "shelly-ap:${network.ssid.lowercase()}",
                                name = network.ssid,
                                transport = DeviceTransport.Wifi,
                                endpoint = "192.168.33.1",
                                serviceType = "shelly-ap",
                                signalStrength = network.signalLevelDbm,
                            )
                        }
                    publishCandidates()
                },
                onFailure = { error ->
                    mutableState.update { current ->
                        current.copy(
                            errorMessage = current.errorMessage ?: error.message
                                ?: "Не удалось найти точки доступа локальных устройств.",
                        )
                    }
                },
            )
        }
    }

    private fun publishCandidates() {
        val candidates = (discoveredDevices + shellyApDevices)
            .distinctBy(SmartDevice::id)
            .map { DeviceCandidate(it, profileRegistry.resolve(it)) }
        mutableState.update { it.copy(candidates = candidates) }
    }

    fun selectCandidate(candidate: DeviceCandidate) {
        stopDiscovery()
        mutableState.update {
            it.copy(
                screen = OnboardingStateMachine.transition(
                    it.screen,
                    OnboardingEvent.CandidateSelected,
                ),
                selectedCandidate = candidate,
                errorMessage = null,
            )
        }
    }

    fun confirmCandidate() {
        mutableState.update {
            it.copy(
                screen = OnboardingStateMachine.transition(it.screen, OnboardingEvent.ConfirmDevice),
                errorMessage = null,
            )
        }
    }

    fun confirmInstallation() {
        val candidate = mutableState.value.selectedCandidate ?: return
        when (candidate.profile.provisioningMode) {
            ProvisioningMode.Unsupported -> mutableState.update {
                it.copy(
                    errorMessage = "Для этого Bluetooth-устройства ещё не установлен совместимый адаптер.",
                )
            }
            ProvisioningMode.RequiresWifiCredentials -> mutableState.update {
                it.copy(
                    screen = OnboardingStateMachine.transition(
                        it.screen,
                        OnboardingEvent.InstallationReady,
                        candidate.profile.provisioningMode,
                    ),
                    errorMessage = null,
                )
            }
            ProvisioningMode.AlreadyNetworked -> activateAlreadyNetworked(candidate)
        }
    }

    fun updateSsid(value: String) {
        mutableState.update { it.copy(ssid = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        mutableState.update { it.copy(password = value, errorMessage = null) }
    }

    fun scanWifiNetworks() {
        if (mutableState.value.screen != OnboardingScreen.NetworkSetup) return
        wifiScanJob?.cancel()
        wifiScanJob = viewModelScope.launch {
            mutableState.update { it.copy(isWifiScanning = true, wifiScanError = null) }
            wifiNetworkScanner.scan().fold(
                onSuccess = { networks ->
                    mutableState.update { current ->
                        current.copy(
                            availableWifiNetworks = networks,
                            isWifiScanning = false,
                            wifiScanError = if (networks.isEmpty()) {
                                "Сети 2.4 GHz не найдены. Подойдите ближе к роутеру и обновите список."
                            } else {
                                null
                            },
                            ssid = current.ssid.takeIf { selected ->
                                networks.any { it.ssid == selected }
                            }.orEmpty(),
                        )
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            isWifiScanning = false,
                            wifiScanError = error.message ?: "Не удалось получить список Wi‑Fi сетей.",
                        )
                    }
                },
            )
        }
    }

    fun submitNetwork() {
        val current = mutableState.value
        val candidate = current.selectedCandidate ?: return
        if (current.ssid.isBlank()) {
            mutableState.update { it.copy(errorMessage = "Выберите Wi-Fi сеть из списка.") }
            return
        }
        if (current.password.length < 8) {
            mutableState.update {
                it.copy(errorMessage = "Пароль Wi-Fi должен содержать не менее 8 символов.")
            }
            return
        }
        val provisioner = integrations.provisioner(candidate.profile)
        if (provisioner == null) {
            mutableState.update { it.copy(errorMessage = "Provisioning adapter недоступен.") }
            return
        }

        mutableState.update {
            it.copy(
                screen = OnboardingStateMachine.transition(
                    it.screen,
                    OnboardingEvent.NetworkSubmitted,
                ),
                isBusy = true,
                provisioningProgress = null,
                errorMessage = null,
            )
        }
        provisioningProgressJob?.cancel()
        provisioningProgressJob = viewModelScope.launch {
            provisioner.progress.collect { progress ->
                mutableState.update { it.copy(provisioningProgress = progress) }
            }
        }
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            provisioner.provision(
                ProvisioningRequest(candidate, current.ssid, current.password),
            ).fold(
                onSuccess = { result ->
                    val saved = SavedDevice(
                        id = result.device.id,
                        profileId = candidate.profile.id,
                        name = result.device.name,
                        room = "Гостиная",
                        transport = result.device.transport,
                        endpoint = result.device.endpoint,
                        serviceType = result.device.serviceType,
                        powerState = result.initialPowerState,
                        connectivityClass = candidate.profile.connectivityClass,
                    )
                    deviceStore.upsert(saved)
                    mutableState.update {
                        it.copy(
                            screen = OnboardingStateMachine.transition(
                                it.screen,
                                OnboardingEvent.ProvisioningSucceeded,
                            ),
                            isBusy = false,
                            password = "",
                            successDeviceId = saved.id,
                            successName = saved.name,
                            successRoom = saved.room,
                            powerState = saved.powerState,
                        )
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            password = "",
                            provisioningProgress = null,
                            errorMessage = error.message ?: "Не удалось подключить устройство.",
                        )
                    }
                },
            )
            provisioningProgressJob?.cancel()
        }
    }

    fun updateSuccessName(value: String) {
        mutableState.update { it.copy(successName = value) }
    }

    fun updateSuccessRoom(value: String) {
        mutableState.update { it.copy(successRoom = value) }
    }

    fun finishSuccess() {
        val current = mutableState.value
        val saved = current.savedDevices.firstOrNull { it.id == current.successDeviceId } ?: return
        val updated = saved.copy(
            name = current.successName.trim().ifBlank { saved.name },
            room = current.successRoom.trim().ifBlank { "Без комнаты" },
        )
        deviceStore.upsert(updated)
        mutableState.update {
            it.copy(
                screen = OnboardingStateMachine.transition(it.screen, OnboardingEvent.FinishSuccess),
                selectedCandidate = null,
                successDeviceId = null,
                ssid = "",
                password = "",
                availableWifiNetworks = emptyList(),
                isWifiScanning = false,
                wifiScanError = null,
                provisioningProgress = null,
                errorMessage = null,
            )
        }
    }

    fun openControl(device: SavedDevice) {
        mutableState.update {
            it.copy(
                screen = OnboardingStateMachine.transition(it.screen, OnboardingEvent.OpenControl),
                selectedSavedDeviceId = device.id,
                powerState = device.powerState,
                errorMessage = null,
            )
        }
        refreshPower()
    }

    fun refreshPower() = runControl(rediscoverLocalEndpoint = true) { controller, device ->
        controller.readState(device.asSmartDevice())
    }

    fun setPower(enabled: Boolean) = runControl(rediscoverLocalEndpoint = false) { controller, device ->
        controller.setPower(device.asSmartDevice(), enabled)
    }

    fun removeSelectedDevice() {
        val current = mutableState.value
        val saved = current.savedDevices.firstOrNull { it.id == current.selectedSavedDeviceId } ?: return
        val remover = integrations.profile(saved.profileId)?.let(integrations::remover)
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorMessage = null) }
            val removal = remover?.remove(saved.asSmartDevice()) ?: Result.success(Unit)
            removal.fold(
                onSuccess = {
                    deviceStore.remove(saved.id)
                    mutableState.update {
                        it.copy(
                            screen = OnboardingScreen.DeviceList,
                            selectedSavedDeviceId = null,
                            powerState = null,
                            isBusy = false,
                            errorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "Не удалось удалить устройство.",
                        )
                    }
                },
            )
        }
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun retryProvisioning() {
        mutableState.value.selectedCandidate?.profile?.let(integrations::provisioner)?.cancel()
        operationJob?.cancel()
        mutableState.update {
            it.copy(
                screen = OnboardingScreen.NetworkSetup,
                isBusy = false,
                provisioningProgress = null,
                errorMessage = null,
            )
        }
    }

    fun goBack() {
        val current = mutableState.value
        if (current.screen == OnboardingScreen.Provisioning) {
            integrations.provisioner(current.selectedCandidate?.profile ?: return)?.cancel()
            operationJob?.cancel()
        }
        if (current.screen == OnboardingScreen.Discovery) stopDiscovery()
        if (current.screen == OnboardingScreen.NetworkSetup) wifiScanJob?.cancel()
        mutableState.update {
            it.copy(
                screen = OnboardingStateMachine.transition(it.screen, OnboardingEvent.Back),
                isBusy = false,
                password = if (
                    current.screen == OnboardingScreen.NetworkSetup ||
                    current.screen == OnboardingScreen.Provisioning
                ) "" else it.password,
                ssid = if (current.screen == OnboardingScreen.NetworkSetup) "" else it.ssid,
                availableWifiNetworks = if (
                    current.screen == OnboardingScreen.NetworkSetup
                ) emptyList() else it.availableWifiNetworks,
                isWifiScanning = false,
                wifiScanError = null,
                provisioningProgress = null,
                errorMessage = null,
            )
        }
    }

    private fun activateAlreadyNetworked(candidate: DeviceCandidate) {
        mutableState.update {
            it.copy(
                screen = OnboardingStateMachine.transition(
                    it.screen,
                    OnboardingEvent.InstallationReady,
                    candidate.profile.provisioningMode,
                ),
                isBusy = true,
                errorMessage = null,
            )
        }
        val controller = integrations.controller(candidate.profile)
        if (controller == null) {
            mutableState.update { it.copy(isBusy = false, errorMessage = "Controller недоступен.") }
            return
        }
        operationJob?.cancel()
        wifiScanJob?.cancel()
        operationJob = viewModelScope.launch {
            controller.readState(candidate.device).fold(
                onSuccess = { power ->
                    val saved = SavedDevice(
                        id = candidate.device.id,
                        profileId = candidate.profile.id,
                        name = candidate.device.name,
                        room = "Гостиная",
                        transport = candidate.device.transport,
                        endpoint = candidate.device.endpoint,
                        serviceType = candidate.device.serviceType,
                        powerState = power,
                        connectivityClass = candidate.profile.connectivityClass,
                    )
                    deviceStore.upsert(saved)
                    mutableState.update {
                        it.copy(
                            screen = OnboardingStateMachine.transition(
                                it.screen,
                                OnboardingEvent.ProvisioningSucceeded,
                            ),
                            isBusy = false,
                            successDeviceId = saved.id,
                            successName = saved.name,
                            successRoom = saved.room,
                            powerState = power,
                        )
                    }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            screen = OnboardingScreen.Installation,
                            isBusy = false,
                            errorMessage = error.message ?: "Устройство не отвечает.",
                        )
                    }
                },
            )
        }
    }

    private fun runControl(
        rediscoverLocalEndpoint: Boolean,
        block: suspend (
            com.dimosfil.smarthome.control.SwitchController,
            SavedDevice,
        ) -> Result<Boolean>,
    ) {
        val current = mutableState.value
        val saved = current.savedDevices.firstOrNull { it.id == current.selectedSavedDeviceId } ?: return
        val profile = integrations.profile(saved.profileId)
        val controller = profile?.let(integrations::controller)
        if (controller == null) {
            mutableState.update { it.copy(errorMessage = "Для устройства нет controller adapter.") }
            return
        }
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, errorMessage = null) }
            val target = if (rediscoverLocalEndpoint) rediscoverShellyEndpoint(saved) else saved
            block(controller, target).fold(
                onSuccess = { power ->
                    deviceStore.upsert(target.copy(powerState = power, isOnline = true))
                    mutableState.update { it.copy(isBusy = false, powerState = power) }
                },
                onFailure = { error ->
                    deviceStore.upsert(target.copy(isOnline = false))
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = error.message ?: "Команда не выполнена.",
                        )
                    }
                },
            )
        }
    }

    private suspend fun rediscoverShellyEndpoint(saved: SavedDevice): SavedDevice {
        if (saved.profileId != DeviceProfileRegistry.shellyProfile.id) return saved
        repository.start(includeBluetooth = false)
        return try {
            val discovered = withTimeoutOrNull(LOCAL_REDISCOVERY_TIMEOUT_MILLIS) {
                repository.devices.first { devices -> devices.any { it.id == saved.id } }
                    .first { it.id == saved.id }
            } ?: return saved
            saved.copy(
                endpoint = discovered.endpoint,
                serviceType = discovered.serviceType,
                isOnline = true,
            ).also(deviceStore::upsert)
        } finally {
            repository.stop()
        }
    }

    override fun onCleared() {
        repository.stop()
        mutableState.value.selectedCandidate?.profile?.let(integrations::provisioner)?.cancel()
        shellyApScanJob?.cancel()
        provisioningProgressJob?.cancel()
        operationJob?.cancel()
    }

    class Factory(
        private val repository: DeviceRepository,
        private val profileRegistry: DeviceProfileRegistry,
        private val integrations: DeviceIntegrationRegistry,
        private val deviceStore: DeviceStore,
        private val wifiNetworkScanner: WifiNetworkScanner,
        private val tuya: TuyaIntegration,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(
            repository,
            profileRegistry,
            integrations,
            deviceStore,
            wifiNetworkScanner,
            tuya,
        ) as T
    }

    private companion object {
        const val LOCAL_REDISCOVERY_TIMEOUT_MILLIS = 5_000L
    }
}
