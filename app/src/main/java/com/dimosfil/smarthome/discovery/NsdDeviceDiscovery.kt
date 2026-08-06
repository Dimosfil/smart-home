package com.dimosfil.smarthome.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NsdDeviceDiscovery(
    context: Context,
    private val serviceTypes: List<String> = listOf(
        "_smart-switch._tcp.",
        "_shelly._tcp.",
        "_http._tcp.",
        "_esphomelib._tcp.",
    ),
) : DeviceDiscoverySource {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val discovered = ConcurrentHashMap<String, SmartDevice>()
    private val listeners = ConcurrentHashMap<String, NsdManager.DiscoveryListener>()
    private val mutableDevices = MutableStateFlow<List<SmartDevice>>(emptyList())

    override val devices: StateFlow<List<SmartDevice>> = mutableDevices.asStateFlow()

    override fun clear() {
        discovered.clear()
        mutableDevices.value = emptyList()
    }

    override fun start(): Result<Unit> = runCatching {
        if (listeners.isEmpty()) {
            clear()
        }
        check(nsdManager != null) { "Wi-Fi discovery не поддерживается" }
        serviceTypes.forEach { type ->
            if (!listeners.containsKey(type)) {
                val listener = listenerFor(type)
                listeners[type] = listener
                try {
                    nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (error: RuntimeException) {
                    listeners.remove(type)
                    throw error
                }
            }
        }
    }

    override fun stop() {
        val active = listeners.values.toList()
        listeners.clear()
        active.forEach { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
    }

    private fun listenerFor(type: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            resolve(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val ids = discovered.filterValues {
                it.name == serviceInfo.serviceName
            }.keys
            ids.forEach(discovered::remove)
            emitDevices()
        }

        override fun onDiscoveryStopped(serviceType: String) {
            listeners.remove(type)
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            listeners.remove(type)
            runCatching { nsdManager?.stopServiceDiscovery(this) }
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            listeners.remove(type)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(serviceInfo: NsdServiceInfo) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val host = resolved.host?.hostAddress ?: return
                val endpoint = formatEndpoint(host, resolved.port)
                val isShelly = resolved.serviceType.startsWith("_shelly._tcp", ignoreCase = true) ||
                    resolved.serviceName.startsWith("shelly", ignoreCase = true)
                val device = SmartDevice(
                    id = if (isShelly) {
                        "shelly:${resolved.serviceName.lowercase()}"
                    } else {
                        "wifi:$endpoint"
                    },
                    name = resolved.serviceName.ifBlank { "Wi-Fi $endpoint" },
                    transport = DeviceTransport.Wifi,
                    endpoint = endpoint,
                    serviceType = resolved.serviceType,
                )
                discovered[device.id] = device
                emitDevices()
            }
        })
    }

    private fun emitDevices() {
        mutableDevices.value = discovered.values.sortedBy(SmartDevice::name)
    }

    private fun formatEndpoint(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith('[')) "[$host]:$port" else "$host:$port"
}
