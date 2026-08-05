package com.dimosfil.smarthome

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dimosfil.smarthome.control.HttpSwitchController
import com.dimosfil.smarthome.discovery.DeviceRepository
import com.dimosfil.smarthome.discovery.NsdDeviceDiscovery
import com.dimosfil.smarthome.onboarding.DeviceIntegrationRegistry
import com.dimosfil.smarthome.onboarding.DeviceProfileRegistry
import com.dimosfil.smarthome.onboarding.AndroidWifiNetworkScanner
import com.dimosfil.smarthome.onboarding.OnboardingScreen
import com.dimosfil.smarthome.persistence.SharedPreferencesDeviceStore
import com.dimosfil.smarthome.ui.OnboardingUiState
import com.dimosfil.smarthome.ui.OnboardingViewModel
import com.dimosfil.smarthome.ui.SmartHomeApp
import com.dimosfil.smarthome.tuya.TuyaIntegration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tuya = TuyaIntegration(applicationContext)
        val repository = DeviceRepository(
            bluetooth = tuya,
            wifi = NsdDeviceDiscovery(applicationContext),
        )
        val profiles = DeviceProfileRegistry()
        val integrations = DeviceIntegrationRegistry(
            profileRegistry = profiles,
            provisioners = mapOf(DeviceProfileRegistry.tuyaProfile.id to tuya),
            controllers = mapOf(
                DeviceProfileRegistry.HTTP_CONTROLLER to HttpSwitchController(),
                DeviceProfileRegistry.TUYA_CONTROLLER to tuya,
            ),
        )
        val factory = OnboardingViewModel.Factory(
            repository = repository,
            profileRegistry = profiles,
            integrations = integrations,
            deviceStore = SharedPreferencesDeviceStore(applicationContext),
            wifiNetworkScanner = AndroidWifiNetworkScanner(applicationContext),
            tuya = tuya,
        )

        setContent {
            val model: OnboardingViewModel = viewModel(factory = factory)
            val state by model.state.collectAsStateWithLifecycle()
            PermissionAwareApp(state, model)
        }
    }
}

@Composable
private fun PermissionAwareApp(state: OnboardingUiState, model: OnboardingViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bluetoothPermissions = remember { bluetoothPermissions() }
    val wifiPermissions = remember { wifiScanPermissions() }
    var bluetoothGranted by remember {
        mutableStateOf(bluetoothPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        })
    }
    var wifiScanGranted by remember {
        mutableStateOf(wifiPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        })
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        bluetoothGranted = result.values.all { it }
        if (bluetoothGranted) model.startDiscovery(includeBluetooth = true)
    }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        wifiScanGranted = result.values.all { it }
    }

    LaunchedEffect(state.screen, wifiScanGranted) {
        if (state.screen == OnboardingScreen.NetworkSetup) {
            if (wifiScanGranted) model.scanWifiNetworks() else wifiPermissionLauncher.launch(wifiPermissions)
        }
    }

    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.stopDiscovery()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SmartHomeApp(
        state = state,
        bluetoothGranted = bluetoothGranted,
        wifiScanGranted = wifiScanGranted,
        onRequestBluetooth = { bluetoothPermissionLauncher.launch(bluetoothPermissions) },
        onRequestWifiScan = { wifiPermissionLauncher.launch(wifiPermissions) },
        model = model,
    )
}

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun wifiScanPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }
