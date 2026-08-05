package com.dimosfil.smarthome.discovery

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothDeviceDiscovery(
    private val context: Context,
) : DeviceDiscoverySource {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val discovered = ConcurrentHashMap<String, SmartDevice>()
    private val mutableDevices = MutableStateFlow<List<SmartDevice>>(emptyList())
    private var scanning = false

    override val devices: StateFlow<List<SmartDevice>> = mutableDevices.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            publish(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::publish)
        }
    }

    @SuppressLint("MissingPermission")
    override fun start(): Result<Unit> = runCatching {
        check(hasScanPermission()) { "Нет разрешения на поиск Bluetooth-устройств" }
        val adapter = bluetoothManager?.adapter
        check(adapter != null) { "Bluetooth LE не поддерживается" }
        check(adapter.isEnabled) { "Bluetooth выключен" }

        if (!scanning) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            adapter.bluetoothLeScanner.startScan(null, settings, callback)
            scanning = true
        }
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        if (!scanning) return
        runCatching {
            if (hasScanPermission()) {
                bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback)
            }
        }
        scanning = false
    }

    private fun publish(result: ScanResult) {
        val address = runCatching { result.device.address }.getOrNull() ?: return
        val advertisedName = result.scanRecord?.deviceName
        val device = SmartDevice(
            id = "ble:$address",
            name = advertisedName?.takeIf(String::isNotBlank) ?: "BLE $address",
            transport = DeviceTransport.Bluetooth,
            endpoint = address,
            signalStrength = result.rssi,
        )
        discovered[device.id] = device
        mutableDevices.value = discovered.values.sortedByDescending { it.signalStrength ?: Int.MIN_VALUE }
    }

    private fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
