package com.dimosfil.smarthome.onboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import com.dimosfil.smarthome.control.ShellyRpcClient
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers

class ShellyProvisioner(context: Context) : DeviceProvisioner {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val resolver = ShellyNsdResolver(appContext)
    private val mutableProgress = MutableStateFlow<ProvisioningProgress?>(null)
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    override val progress: StateFlow<ProvisioningProgress?> = mutableProgress.asStateFlow()

    override suspend fun provision(request: ProvisioningRequest): Result<ProvisionedDevice> = try {
        check(request.candidate.device.id.startsWith("shelly-ap:")) {
            "Выбранное устройство не является точкой доступа Shelly."
        }
        val manager = connectivityManager
            ?: error("Android не предоставляет управление сетевыми подключениями.")

        progress(ProvisioningStage.EstablishingConnection)
        val lease = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            awaitShellyNetwork(manager, request.candidate.device.name)
        } else {
            error("Автоматическое подключение Shelly поддерживается на Android 10 и новее.")
        }
        activeNetworkCallback = lease.callback
        val info = try {
            withContext(Dispatchers.IO) {
                val client = ShellyRpcClient { url ->
                    lease.network.openConnection(url) as HttpURLConnection
                }
                val deviceInfo = client.readDeviceInfo(SHELLY_AP_ENDPOINT)
                progress(
                    ProvisioningStage.SendingCredentials,
                    ProvisioningStage.EstablishingConnection,
                )
                client.configureWifi(SHELLY_AP_ENDPOINT, request.ssid, request.password)
                deviceInfo
            }
        } finally {
            unregisterNetworkCallback(manager, lease.callback)
            activeNetworkCallback = null
        }

        progress(
            ProvisioningStage.ConnectingToRouter,
            ProvisioningStage.EstablishingConnection,
            ProvisioningStage.SendingCredentials,
        )
        val device = resolver.await(info.id, info.model).getOrThrow()
        progress(
            ProvisioningStage.VerifyingDevice,
            ProvisioningStage.EstablishingConnection,
            ProvisioningStage.SendingCredentials,
            ProvisioningStage.ConnectingToRouter,
        )
        val initialState = withContext(Dispatchers.IO) {
            ShellyRpcClient().readSwitchState(device.endpoint)
        }
        mutableProgress.value = ProvisioningProgress(
            stage = ProvisioningStage.VerifyingDevice,
            completedStages = setOf(
                ProvisioningStage.EstablishingConnection,
                ProvisioningStage.SendingCredentials,
                ProvisioningStage.ConnectingToRouter,
                ProvisioningStage.VerifyingDevice,
            ),
        )
        Result.success(ProvisionedDevice(device, initialState))
    } catch (_: TimeoutCancellationException) {
        Result.failure(
            IllegalStateException(
                "Shelly не появилась в домашней сети. Проверьте пароль, диапазон 2.4 GHz и изоляцию клиентов Wi‑Fi.",
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    } finally {
        mutableProgress.value = null
    }

    override fun cancel() {
        activeNetworkCallback?.let { callback ->
            connectivityManager?.let { unregisterNetworkCallback(it, callback) }
        }
        activeNetworkCallback = null
        resolver.cancel()
        mutableProgress.value = null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun awaitShellyNetwork(
        manager: ConnectivityManager,
        ssid: String,
    ): NetworkLease = withTimeout(AP_CONNECTION_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            val specifier = WifiNetworkSpecifier.Builder().setSsid(ssid).build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(NetworkLease(network, this))
                    }
                }

                override fun onUnavailable() {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(
                                IllegalStateException(
                                    "Android не подключился к точке доступа $ssid. Подтвердите системный запрос и повторите попытку.",
                                ),
                            ),
                        )
                    }
                }
            }
            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) unregisterNetworkCallback(manager, callback)
            }
            manager.requestNetwork(request, callback)
        }
    }

    private fun progress(stage: ProvisioningStage, vararg completed: ProvisioningStage) {
        mutableProgress.value = ProvisioningProgress(stage, completed.toSet())
    }

    private fun unregisterNetworkCallback(
        manager: ConnectivityManager,
        callback: ConnectivityManager.NetworkCallback,
    ) {
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    private data class NetworkLease(
        val network: Network,
        val callback: ConnectivityManager.NetworkCallback,
    )

    private companion object {
        const val SHELLY_AP_ENDPOINT = "192.168.33.1"
        const val AP_CONNECTION_TIMEOUT_MILLIS = 45_000L
    }
}

private class ShellyNsdResolver(context: Context) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private var activeListener: NsdManager.DiscoveryListener? = null

    suspend fun await(deviceId: String, model: String): Result<SmartDevice> = try {
        Result.success(withTimeout(DISCOVERY_TIMEOUT_MILLIS) { awaitService(deviceId, model) })
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun cancel() {
        activeListener?.let { listener -> runCatching { nsdManager?.stopServiceDiscovery(listener) } }
        activeListener = null
    }

    private suspend fun awaitService(deviceId: String, model: String): SmartDevice =
        suspendCancellableCoroutine { continuation ->
            val manager = nsdManager ?: run {
                continuation.resumeWith(Result.failure(IllegalStateException("mDNS не поддерживается устройством Android.")))
                return@suspendCancellableCoroutine
            }
            val completed = AtomicBoolean(false)
            val expected = normalize(deviceId)
            lateinit var listener: NsdManager.DiscoveryListener

            fun stop() {
                if (activeListener === listener) activeListener = null
                runCatching { manager.stopServiceDiscovery(listener) }
            }

            fun fail(message: String) {
                if (completed.compareAndSet(false, true)) {
                    stop()
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(IllegalStateException(message)))
                    }
                }
            }

            @Suppress("DEPRECATION")
            fun resolve(info: NsdServiceInfo) {
                manager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (!completed.compareAndSet(false, true)) return
                        val host = serviceInfo.host?.hostAddress
                        if (host == null) {
                            completed.set(false)
                            return
                        }
                        val endpoint = if (host.contains(':') && !host.startsWith('[')) {
                            "[$host]:${serviceInfo.port}"
                        } else {
                            "$host:${serviceInfo.port}"
                        }
                        stop()
                        if (continuation.isActive) {
                            continuation.resume(
                                SmartDevice(
                                    id = "shelly:${deviceId.lowercase()}",
                                    name = model.ifBlank { serviceInfo.serviceName },
                                    transport = DeviceTransport.Wifi,
                                    endpoint = endpoint,
                                    serviceType = SHELLY_SERVICE_TYPE,
                                ),
                            )
                        }
                    }
                })
            }

            listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (normalize(serviceInfo.serviceName).contains(expected)) resolve(serviceInfo)
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    fail("Не удалось запустить локальный поиск Shelly: код $errorCode.")
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
            activeListener = listener
            continuation.invokeOnCancellation { stop() }
            manager.discoverServices(SHELLY_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

    private companion object {
        const val SHELLY_SERVICE_TYPE = "_shelly._tcp."
        const val DISCOVERY_TIMEOUT_MILLIS = 75_000L
    }
}
