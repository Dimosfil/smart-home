package com.dimosfil.smarthome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dimosfil.smarthome.control.SwitchController
import com.dimosfil.smarthome.discovery.DeviceRepository
import com.dimosfil.smarthome.model.SmartDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val devices: List<SmartDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val isScanning: Boolean = false,
    val isBusy: Boolean = false,
    val powerState: Boolean? = null,
    val message: String? = null,
)

class MainViewModel(
    private val repository: DeviceRepository,
    private val controller: SwitchController,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.devices.collect { devices ->
                mutableState.update { current -> current.copy(devices = devices) }
            }
        }
    }

    fun startDiscovery(includeBluetooth: Boolean) {
        val errors = repository.start(includeBluetooth)
        mutableState.update {
            it.copy(
                isScanning = true,
                message = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            )
        }
    }

    fun stopDiscovery() {
        repository.stop()
        mutableState.update { it.copy(isScanning = false) }
    }

    fun select(device: SmartDevice) {
        mutableState.update {
            it.copy(selectedDeviceId = device.id, powerState = null, message = null)
        }
        refreshState()
    }

    fun refreshState() = runForSelected { device -> controller.readState(device) }

    fun setPower(enabled: Boolean) = runForSelected { device ->
        controller.setPower(device, enabled)
    }

    private fun runForSelected(block: suspend (SmartDevice) -> Result<Boolean>) {
        val device = selectedDevice() ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, message = null) }
            block(device).fold(
                onSuccess = { power ->
                    mutableState.update { it.copy(isBusy = false, powerState = power) }
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(isBusy = false, message = error.message ?: "Ошибка управления")
                    }
                },
            )
        }
    }

    private fun selectedDevice(): SmartDevice? {
        val id = mutableState.value.selectedDeviceId ?: return null
        return mutableState.value.devices.firstOrNull { it.id == id }
    }

    override fun onCleared() {
        repository.stop()
    }

    class Factory(
        private val repository: DeviceRepository,
        private val controller: SwitchController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(repository, controller) as T
    }
}
