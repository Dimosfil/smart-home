package com.dimosfil.smarthome

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dimosfil.smarthome.control.HttpSwitchController
import com.dimosfil.smarthome.discovery.BluetoothDeviceDiscovery
import com.dimosfil.smarthome.discovery.DeviceRepository
import com.dimosfil.smarthome.discovery.NsdDeviceDiscovery
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import com.dimosfil.smarthome.ui.MainUiState
import com.dimosfil.smarthome.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = DeviceRepository(
            bluetooth = BluetoothDeviceDiscovery(applicationContext),
            wifi = NsdDeviceDiscovery(applicationContext),
        )
        val factory = MainViewModel.Factory(repository, HttpSwitchController())

        setContent {
            MaterialTheme {
                val model: MainViewModel = viewModel(factory = factory)
                val state by model.state.collectAsStateWithLifecycle()
                PermissionAwareScreen(state, model)
            }
        }
    }
}

@Composable
private fun PermissionAwareScreen(state: MainUiState, model: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissions = remember { bluetoothPermissions() }
    var bluetoothGranted by remember {
        mutableStateOf(permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        bluetoothGranted = result.values.all { it }
    }

    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.stopDiscovery()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SmartHomeScreen(
        state = state,
        bluetoothGranted = bluetoothGranted,
        onRequestBluetooth = { permissionLauncher.launch(permissions) },
        onStart = { model.startDiscovery(bluetoothGranted) },
        onStop = model::stopDiscovery,
        onSelect = model::select,
        onRefresh = model::refreshState,
        onPowerChange = model::setPower,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SmartHomeScreen(
    state: MainUiState,
    bluetoothGranted: Boolean,
    onRequestBluetooth: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSelect: (SmartDevice) -> Unit,
    onRefresh: () -> Unit,
    onPowerChange: (Boolean) -> Unit,
) {
    val selected = state.devices.firstOrNull { it.id == state.selectedDeviceId }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Умный дом") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Найдите выключатель рядом по Bluetooth или в локальной Wi‑Fi сети.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (!bluetoothGranted) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Bluetooth-поиск требует доступ к устройствам поблизости.")
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onRequestBluetooth) {
                                Text("Разрешить Bluetooth")
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = if (state.isScanning) onStop else onStart) {
                        Text(if (state.isScanning) "Остановить поиск" else "Найти устройства")
                    }
                    Text(
                        text = "Найдено: ${state.devices.size}",
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            state.message?.let { message ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            items(state.devices, key = SmartDevice::id) { device ->
                DeviceCard(
                    device = device,
                    selected = device.id == state.selectedDeviceId,
                    onClick = { onSelect(device) },
                )
            }

            selected?.let { device ->
                item {
                    ControlCard(
                        device = device,
                        state = state,
                        onRefresh = onRefresh,
                        onPowerChange = onPowerChange,
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun DeviceCard(device: SmartDevice, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                when (device.transport) {
                    DeviceTransport.Bluetooth -> "Bluetooth LE • RSSI ${device.signalStrength ?: "—"}"
                    DeviceTransport.Wifi -> "Wi‑Fi • ${device.endpoint}"
                },
            )
            if (selected) Text("Выбрано", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ControlCard(
    device: SmartDevice,
    state: MainUiState,
    onRefresh: () -> Unit,
    onPowerChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Управление выключателем", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (device.transport == DeviceTransport.Wifi) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        when (state.powerState) {
                            true -> "Включён"
                            false -> "Выключен"
                            null -> "Состояние неизвестно"
                        },
                    )
                    Switch(
                        checked = state.powerState == true,
                        enabled = !state.isBusy,
                        onCheckedChange = onPowerChange,
                    )
                }
                OutlinedButton(enabled = !state.isBusy, onClick = onRefresh) {
                    Text("Обновить состояние")
                }
            } else {
                Text("Устройство найдено. Для управления нужен BLE-протокол конкретной модели.")
            }
        }
    }
}

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
