package com.dimosfil.smarthome.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class AndroidWifiNetworkScanner(
    context: Context,
    private val scanTimeoutMillis: Long = DEFAULT_SCAN_TIMEOUT_MILLIS,
) : WifiNetworkScanner {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override suspend fun scan(): Result<List<WifiNetwork>> = try {
        check(wifiManager != null) { "Сканирование Wi‑Fi не поддерживается на этом устройстве." }
        check(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            "Разрешите точный доступ к геолокации для показа доступных Wi‑Fi сетей."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            check(hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                "Разрешите доступ к устройствам поблизости для поиска Wi‑Fi сетей."
            }
        }
        check(wifiManager.isWifiEnabled) { "Включите Wi‑Fi и повторите поиск сетей." }
        check(locationServicesEnabled()) {
            "Включите геолокацию Android — она необходима системе для показа доступных Wi‑Fi сетей."
        }
        Result.success(withTimeout(scanTimeoutMillis) { awaitScanResults(wifiManager) })
    } catch (_: TimeoutCancellationException) {
        Result.failure(IllegalStateException("Поиск Wi‑Fi сетей занял слишком много времени. Повторите попытку."))
    } catch (error: SecurityException) {
        Result.failure(SecurityException("Нет разрешения на поиск доступных Wi‑Fi сетей.", error))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun awaitScanResults(manager: WifiManager): List<WifiNetwork> =
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            lateinit var receiver: BroadcastReceiver

            fun unregisterReceiver() {
                runCatching { appContext.unregisterReceiver(receiver) }
            }

            fun complete(result: Result<List<WifiNetwork>>) {
                if (!completed.compareAndSet(false, true)) return
                unregisterReceiver()
                result.fold(
                    onSuccess = continuation::resume,
                    onFailure = continuation::resumeWithException,
                )
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
                    if (!updated) {
                        complete(
                            Result.failure(
                                IllegalStateException(
                                    "Android не обновил список Wi‑Fi сетей. Подождите немного и повторите поиск.",
                                ),
                            ),
                        )
                        return
                    }
                    complete(runCatching { mapScanResults(manager) })
                }
            }

            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) unregisterReceiver()
            }

            @Suppress("DEPRECATION")
            val started = runCatching { manager.startScan() }.getOrElse { error ->
                complete(Result.failure(error))
                return@suspendCancellableCoroutine
            }
            if (!started) {
                complete(
                    Result.failure(
                        IllegalStateException(
                            "Не удалось запустить обновление списка Wi‑Fi сетей. Android мог временно ограничить частоту поиска.",
                        ),
                    ),
                )
            }
        }

    @SuppressLint("MissingPermission") // scan() checks the revocable permissions immediately before registration.
    @Suppress("DEPRECATION")
    private fun mapScanResults(manager: WifiManager): List<WifiNetwork> =
        selectAvailableWifiNetworks(
            manager.scanResults.map { result ->
                WifiScanObservation(
                    ssid = result.SSID,
                    frequencyMhz = result.frequency,
                    signalLevelDbm = result.level,
                )
            },
        )

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun locationServicesEnabled(): Boolean {
        val manager = locationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private companion object {
        const val DEFAULT_SCAN_TIMEOUT_MILLIS = 15_000L
    }
}
