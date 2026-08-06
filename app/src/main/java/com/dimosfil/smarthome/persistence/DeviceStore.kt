package com.dimosfil.smarthome.persistence

import android.content.Context
import androidx.core.content.edit
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.onboarding.ConnectivityClass
import com.dimosfil.smarthome.onboarding.DeviceProfileRegistry
import com.dimosfil.smarthome.onboarding.SavedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

interface DeviceStore {
    val devices: StateFlow<List<SavedDevice>>

    fun upsert(device: SavedDevice)

    fun remove(deviceId: String)
}

class SharedPreferencesDeviceStore(context: Context) : DeviceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableDevices = MutableStateFlow(readDevicesAndRemoveLegacyDemoEntries())

    override val devices: StateFlow<List<SavedDevice>> = mutableDevices.asStateFlow()

    @Synchronized
    override fun upsert(device: SavedDevice) {
        val updated = (mutableDevices.value.filterNot { it.id == device.id } + device)
            .sortedBy(SavedDevice::name)
        preferences.edit { putString(KEY_DEVICES, encode(updated)) }
        mutableDevices.value = updated
    }

    @Synchronized
    override fun remove(deviceId: String) {
        val updated = mutableDevices.value.filterNot { it.id == deviceId }
        preferences.edit { putString(KEY_DEVICES, encode(updated)) }
        mutableDevices.value = updated
    }

    private fun readDevices(): List<SavedDevice> = runCatching {
        val raw = preferences.getString(KEY_DEVICES, null) ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SavedDevice(
                        id = item.getString("id"),
                        profileId = item.getString("profileId"),
                        name = item.getString("name"),
                        room = item.getString("room"),
                        transport = DeviceTransport.valueOf(item.getString("transport")),
                        endpoint = item.getString("endpoint"),
                        serviceType = item.optString("serviceType").takeIf(String::isNotBlank),
                        powerState = if (item.isNull("powerState")) null else item.getBoolean("powerState"),
                        isOnline = item.optBoolean("isOnline", true),
                        connectivityClass = item.optString("connectivityClass")
                            .takeIf(String::isNotBlank)
                            ?.let { runCatching { ConnectivityClass.valueOf(it) }.getOrNull() }
                            ?: legacyConnectivityClass(item.getString("profileId")),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun readDevicesAndRemoveLegacyDemoEntries(): List<SavedDevice> {
        val stored = readDevices()
        val realDevices = stored.filterNot(::isLegacyDemoEntry)
        if (realDevices.size != stored.size) {
            preferences.edit { putString(KEY_DEVICES, encode(realDevices)) }
        }
        return realDevices
    }

    private fun isLegacyDemoEntry(device: SavedDevice): Boolean =
        device.id.startsWith(LEGACY_DEMO_ID_PREFIX) ||
            device.profileId == LEGACY_DEMO_PROFILE_ID ||
            device.endpoint.startsWith(LEGACY_DEMO_ENDPOINT_PREFIX)

    private fun encode(devices: List<SavedDevice>): String = JSONArray().apply {
        devices.forEach { device ->
            put(
                JSONObject().apply {
                    put("id", device.id)
                    put("profileId", device.profileId)
                    put("name", device.name)
                    put("room", device.room)
                    put("transport", device.transport.name)
                    put("endpoint", device.endpoint)
                    put("serviceType", device.serviceType ?: "")
                    put("powerState", device.powerState ?: JSONObject.NULL)
                    put("isOnline", device.isOnline)
                    put("connectivityClass", device.connectivityClass.name)
                },
            )
        }
    }.toString()

    private companion object {
        fun legacyConnectivityClass(profileId: String): ConnectivityClass = when (profileId) {
            DeviceProfileRegistry.shellyProfile.id,
            DeviceProfileRegistry.prototypeHttpProfile.id,
            -> ConnectivityClass.LocalNative
            else -> ConnectivityClass.CloudOnly
        }

        const val PREFERENCES_NAME = "smart_home_devices"
        const val KEY_DEVICES = "saved_devices"
        const val LEGACY_DEMO_ID_PREFIX = "emulator:"
        const val LEGACY_DEMO_PROFILE_ID = "emulated-wifi-plug"
        const val LEGACY_DEMO_ENDPOINT_PREFIX = "emulator://"
    }
}
