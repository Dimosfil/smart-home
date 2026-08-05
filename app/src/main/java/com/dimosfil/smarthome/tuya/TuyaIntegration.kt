package com.dimosfil.smarthome.tuya

import android.content.Context
import com.dimosfil.smarthome.BuildConfig
import com.dimosfil.smarthome.control.SwitchController
import com.dimosfil.smarthome.discovery.DeviceDiscoverySource
import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import com.dimosfil.smarthome.onboarding.DeviceProfileRegistry
import com.dimosfil.smarthome.onboarding.DeviceProvisioner
import com.dimosfil.smarthome.onboarding.ProvisionedDevice
import com.dimosfil.smarthome.onboarding.ProvisioningProgress
import com.dimosfil.smarthome.onboarding.ProvisioningRequest
import com.dimosfil.smarthome.onboarding.ProvisioningStage
import com.thingclips.smart.android.ble.api.BleScanResponse
import com.thingclips.smart.android.ble.api.LeScanSetting
import com.thingclips.smart.android.ble.api.ScanDeviceBean
import com.thingclips.smart.android.ble.api.ScanType
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.home.sdk.bean.HomeBean
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback
import com.thingclips.smart.sdk.api.IMultiModeActivator
import com.thingclips.smart.sdk.api.IMultiModeActivatorListener
import com.thingclips.smart.sdk.api.IDevListener
import com.thingclips.smart.sdk.api.IResultCallback
import com.thingclips.smart.sdk.api.IThingActivatorGetToken
import com.thingclips.smart.sdk.bean.DeviceBean
import com.thingclips.smart.sdk.bean.MultiModeActivatorBean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class TuyaSessionStatus(
    val configured: Boolean = BuildConfig.TUYA_CONFIGURED,
    val loggedIn: Boolean = false,
    val homeId: Long? = null,
    val error: String? = null,
) {
    val ready: Boolean get() = configured && loggedIn && homeId != null
}

class TuyaIntegration(context: Context) : DeviceDiscoverySource, DeviceProvisioner, SwitchController {
    private val identityPreferences = context.applicationContext.getSharedPreferences(
        IDENTITY_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutableDevices = MutableStateFlow<List<SmartDevice>>(emptyList())
    private val mutableProgress = MutableStateFlow<ProvisioningProgress?>(null)
    private val mutableSession = MutableStateFlow(
        TuyaSessionStatus(
            configured = BuildConfig.TUYA_CONFIGURED,
            loggedIn = BuildConfig.TUYA_CONFIGURED && ThingHomeSdk.getUserInstance().isLogin,
        ),
    )
    private val scanBeans = ConcurrentHashMap<String, ScanDeviceBean>()
    private var activator: IMultiModeActivator? = null

    override val devices: StateFlow<List<SmartDevice>> = mutableDevices.asStateFlow()
    override val progress: StateFlow<ProvisioningProgress?> = mutableProgress.asStateFlow()
    val session: StateFlow<TuyaSessionStatus> = mutableSession.asStateFlow()

    suspend fun restoreSession(): Result<Long> {
        if (!BuildConfig.TUYA_CONFIGURED) return Result.failure(configurationError())
        if (!ThingHomeSdk.getUserInstance().isLogin) {
            mutableSession.value = TuyaSessionStatus(configured = true)
            return Result.failure(IllegalStateException("Войдите в аккаунт Tuya для поиска устройств."))
        }
        return ensureHome().also { result ->
            mutableSession.value = result.fold(
                onSuccess = { TuyaSessionStatus(true, true, it) },
                onFailure = { TuyaSessionStatus(true, true, error = it.message) },
            )
        }
    }

    suspend fun connectAutomatically(): Result<Long> {
        if (!BuildConfig.TUYA_CONFIGURED) return Result.failure(configurationError())
        if (ThingHomeSdk.getUserInstance().isLogin) return restoreSession()

        val identity = loadOrCreateIdentity()
        val login = suspendCancellableCoroutine<Result<User>> { continuation ->
            ThingHomeSdk.getUserInstance().loginOrRegisterWithUid(
                DEFAULT_COUNTRY_CODE,
                identity.uid,
                identity.password,
                object : ILoginCallback {
                    override fun onSuccess(user: User) {
                        if (continuation.isActive) continuation.resume(Result.success(user))
                    }

                    override fun onError(code: String, error: String) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(tuyaError(code, error)))
                        }
                    }
                },
            )
        }
        if (login.isFailure) return Result.failure(login.exceptionOrNull()!!)
        return restoreSession()
    }

    private fun loadOrCreateIdentity(): AnonymousIdentity {
        val storedUid = identityPreferences.getString(KEY_UID, null)
        val storedPassword = identityPreferences.getString(KEY_PASSWORD, null)
        if (!storedUid.isNullOrBlank() && !storedPassword.isNullOrBlank()) {
            return AnonymousIdentity(storedUid, storedPassword)
        }

        val identity = AnonymousIdentity(
            uid = UUID.randomUUID().toString().replace("-", ""),
            password = UUID.randomUUID().toString().replace("-", "").take(20),
        )
        identityPreferences.edit()
            .putString(KEY_UID, identity.uid)
            .putString(KEY_PASSWORD, identity.password)
            .apply()
        return identity
    }

    override fun start(): Result<Unit> = runCatching {
        check(session.value.ready) {
            session.value.error ?: "Войдите в Tuya и дождитесь загрузки дома."
        }
        scanBeans.clear()
        mutableDevices.value = emptyList()
        val settings = LeScanSetting.Builder()
            .setTimeout(SCAN_TIMEOUT_MILLIS)
            .addScanType(ScanType.SINGLE)
            .setRepeatFilter(false)
            .build()
        ThingHomeSdk.getBleOperator().startLeScan(settings, BleScanResponse(::onScanResult))
    }

    override fun stop() {
        if (BuildConfig.TUYA_CONFIGURED) ThingHomeSdk.getBleOperator().stopLeScan()
    }

    override fun clear() {
        scanBeans.clear()
        mutableDevices.value = emptyList()
    }

    override suspend fun provision(request: ProvisioningRequest): Result<ProvisionedDevice> {
        val homeId = session.value.homeId
            ?: return Result.failure(IllegalStateException("Дом Tuya не выбран."))
        val scanBean = scanBeans[request.candidate.device.id]
            ?: return Result.failure(IllegalStateException("Результат Tuya-сканирования устарел. Запустите поиск снова."))
        if (request.ssid.isBlank()) return Result.failure(IllegalArgumentException("Укажите Wi-Fi сеть."))
        if (scanBean.getIsbind()) {
            return Result.failure(
                IllegalStateException(
                    "Устройство уже привязано к другому Tuya Home. Удалите его из Smart Life и выполните сброс.",
                ),
            )
        }
        val deviceUuid = scanBean.uuid?.takeIf(String::isNotBlank)
            ?: return Result.failure(
                IllegalStateException("Tuya не вернула UUID устройства. Сбросьте устройство и повторите поиск."),
            )

        mutableProgress.value = ProvisioningProgress(ProvisioningStage.EstablishingConnection)
        val token = getActivatorToken(homeId)
        if (token.isFailure) {
            mutableProgress.value = null
            return Result.failure(token.exceptionOrNull()!!)
        }

        mutableProgress.value = ProvisioningProgress(
            ProvisioningStage.SendingCredentials,
            setOf(ProvisioningStage.EstablishingConnection),
        )
        return suspendCancellableCoroutine { continuation ->
            val bean = MultiModeActivatorBean(scanBean).apply {
                this.homeId = homeId
                deviceType = scanBean.deviceType
                uuid = deviceUuid
                address = scanBean.address
                mac = scanBean.mac
                flag = scanBean.flag
                productId = scanBean.productId
                ssid = request.ssid
                pwd = request.password
                this.token = token.getOrThrow()
                timeout = ACTIVATION_TIMEOUT_MILLIS
                phase1Timeout = WIFI_ACTIVATION_TIMEOUT_MILLIS
            }
            val active = ThingHomeSdk.getActivator().newMultiModeActivator()
            activator = active
            mutableProgress.value = ProvisioningProgress(
                ProvisioningStage.ConnectingToRouter,
                setOf(
                    ProvisioningStage.EstablishingConnection,
                    ProvisioningStage.SendingCredentials,
                ),
            )
            val listener = object : IMultiModeActivatorListener {
                override fun onSuccess(deviceBean: DeviceBean) {
                    activator = null
                    mutableProgress.value = ProvisioningProgress(
                        ProvisioningStage.VerifyingDevice,
                        ProvisioningStage.entries.toSet(),
                    )
                    val dp = powerDp(deviceBean)
                    val power = dp?.let { deviceBean.getDps()?.get(it) as? Boolean } ?: false
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.success(
                                ProvisionedDevice(
                                    device = deviceBean.asSmartDevice(),
                                    initialPowerState = power,
                                ),
                            ),
                        )
                    }
                }

                override fun onFailure(code: Int, message: String, handle: Any?) {
                    activator = null
                    mutableProgress.value = null
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(tuyaError(code.toString(), message)),
                        )
                    }
                }
            }
            runCatching { active.startActivator(bean, listener) }
                .onFailure { error ->
                    activator = null
                    mutableProgress.value = null
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException(
                                    "Tuya не смогла запустить подключение устройства: ${error.message}",
                                    error,
                                ),
                            ),
                        )
                    }
                }
            continuation.invokeOnCancellation {
                active.stopActivator(scanBean.uuid)
                activator = null
                mutableProgress.value = null
            }
        }
    }

    override fun cancel() {
        scanBeans.values.firstOrNull()?.uuid?.let { activator?.stopActivator(it) }
        activator = null
        mutableProgress.value = null
    }

    override suspend fun readState(device: SmartDevice): Result<Boolean> = runCatching {
        check(session.value.ready) { "Сессия Tuya не готова." }
        val bean = ThingHomeSdk.getDataInstance().getDeviceBean(device.endpoint)
            ?: error("Устройство отсутствует в текущем Tuya Home.")
        val dp = powerDp(bean) ?: error("Устройство не содержит доступного Boolean DP питания.")
        bean.getDps()?.get(dp) as? Boolean
            ?: error("Tuya не вернула состояние DP $dp.")
    }

    override suspend fun setPower(device: SmartDevice, enabled: Boolean): Result<Boolean> {
        val bean = ThingHomeSdk.getDataInstance().getDeviceBean(device.endpoint)
            ?: return Result.failure(IllegalStateException("Устройство отсутствует в текущем Tuya Home."))
        val dp = powerDp(bean)
            ?: return Result.failure(IllegalStateException("Не найден Boolean DP питания."))
        val thingDevice = ThingHomeSdk.newDeviceInstance(bean.devId)
        val command = JSONObject().put(dp, enabled).toString()
        return runCatching {
            withTimeout(COMMAND_ACK_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<Result<Boolean>> { continuation ->
                    val completed = AtomicBoolean(false)
                    lateinit var listener: IDevListener
                    fun finish(result: Result<Boolean>) {
                        if (completed.compareAndSet(false, true)) {
                            thingDevice.unRegisterDevListener()
                            thingDevice.onDestroy()
                            if (continuation.isActive) continuation.resume(result)
                        }
                    }
                    listener = object : IDevListener {
                        override fun onDpUpdate(devId: String, dpStr: String) {
                            val value = runCatching { JSONObject(dpStr).opt(dp) as? Boolean }.getOrNull()
                            if (value != null) finish(Result.success(value))
                        }

                        override fun onRemoved(devId: String) {
                            finish(Result.failure(IllegalStateException("Устройство удалено из Tuya Home.")))
                        }

                        override fun onStatusChanged(devId: String, online: Boolean) = Unit
                        override fun onNetworkStatusChanged(devId: String, status: Boolean) = Unit
                        override fun onDevInfoUpdate(devId: String) = Unit
                    }
                    thingDevice.registerDevListener(listener)
                    thingDevice.publishDps(command, object : IResultCallback {
                        override fun onSuccess() = Unit

                        override fun onError(code: String, error: String) {
                            finish(Result.failure(tuyaError(code, error)))
                        }
                    })
                    continuation.invokeOnCancellation {
                        if (completed.compareAndSet(false, true)) {
                            thingDevice.unRegisterDevListener()
                            thingDevice.onDestroy()
                        }
                    }
                }
            }
        }.fold(
            onSuccess = { it },
            onFailure = {
                Result.failure(
                    IllegalStateException("Tuya не подтвердила новое состояние реле: ${it.message}"),
                )
            },
        )
    }

    private fun onScanResult(bean: ScanDeviceBean) {
        val uuid = bean.uuid?.takeIf(String::isNotBlank) ?: bean.id ?: return
        val isWifiCombo = bean.configType == CONFIG_TYPE_WIFI
        val id = if (isWifiCombo) "$SCAN_ID_PREFIX$uuid" else "$UNSUPPORTED_BLE_ID_PREFIX$uuid"
        if (isWifiCombo) scanBeans[id] = bean
        val product = bean.productId?.takeIf(String::isNotBlank)
        val name = bean.name?.takeIf(String::isNotBlank)
            ?: product?.let { "Tuya ${it.take(8)}" }
            ?: "Tuya device"
        val device = SmartDevice(
            id = id,
            name = name,
            transport = DeviceTransport.Bluetooth,
            endpoint = uuid,
            serviceType = "tuya:${bean.configType}:${product.orEmpty()}",
            signalStrength = bean.rssi,
        )
        mutableDevices.value = (mutableDevices.value.filterNot { it.id == id } + device)
            .sortedBy(SmartDevice::name)
    }

    private suspend fun ensureHome(): Result<Long> {
        val homes = queryHomes()
        if (homes.isFailure) return Result.failure(homes.exceptionOrNull()!!)
        val home = homes.getOrThrow().firstOrNull() ?: createHome().getOrElse {
            return Result.failure(it)
        }
        return loadHome(home.homeId).map { it.homeId }
    }

    private suspend fun queryHomes(): Result<List<HomeBean>> = suspendCancellableCoroutine { continuation ->
        ThingHomeSdk.getHomeManagerInstance().queryHomeList(object : IThingGetHomeListCallback {
            override fun onSuccess(homes: List<HomeBean>) {
                if (continuation.isActive) continuation.resume(Result.success(homes))
            }

            override fun onError(code: String, error: String) {
                if (continuation.isActive) continuation.resume(Result.failure(tuyaError(code, error)))
            }
        })
    }

    private suspend fun createHome(): Result<HomeBean> = suspendCancellableCoroutine { continuation ->
        ThingHomeSdk.getHomeManagerInstance().createHome(
            "Smart Home",
            0.0,
            0.0,
            "",
            listOf("Гостиная"),
            object : IThingHomeResultCallback {
                override fun onSuccess(home: HomeBean) {
                    if (continuation.isActive) continuation.resume(Result.success(home))
                }

                override fun onError(code: String, error: String) {
                    if (continuation.isActive) continuation.resume(Result.failure(tuyaError(code, error)))
                }
            },
        )
    }

    private suspend fun loadHome(homeId: Long): Result<HomeBean> =
        suspendCancellableCoroutine { continuation ->
            ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(object : IThingHomeResultCallback {
                override fun onSuccess(home: HomeBean) {
                    if (continuation.isActive) continuation.resume(Result.success(home))
                }

                override fun onError(code: String, error: String) {
                    if (continuation.isActive) continuation.resume(Result.failure(tuyaError(code, error)))
                }
            })
        }

    private suspend fun getActivatorToken(homeId: Long): Result<String> =
        suspendCancellableCoroutine { continuation ->
            ThingHomeSdk.getActivatorInstance().getActivatorToken(
                homeId,
                object : IThingActivatorGetToken {
                    override fun onSuccess(token: String) {
                        if (continuation.isActive) continuation.resume(Result.success(token))
                    }

                    override fun onFailure(code: String, error: String) {
                        if (continuation.isActive) continuation.resume(Result.failure(tuyaError(code, error)))
                    }
                },
            )
        }

    private suspend fun suspendResult(
        operation: (IResultCallback) -> Unit,
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        operation(object : IResultCallback {
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Result.success(Unit))
            }

            override fun onError(code: String, error: String) {
                if (continuation.isActive) continuation.resume(Result.failure(tuyaError(code, error)))
            }
        })
    }

    private fun powerDp(bean: DeviceBean): String? {
        val preferredCodes = setOf("switch_1", "switch", "switch_led", "power")
        val schemas = bean.getSchemaMap().orEmpty()
        return schemas.entries.firstOrNull { (_, schema) ->
            schema.code in preferredCodes && schema.type.equals("bool", ignoreCase = true)
        }?.key
            ?: bean.switchDp.takeIf { it > 0 }?.toString()
            ?: schemas.entries.firstOrNull { (_, schema) ->
                schema.type.equals("bool", ignoreCase = true) &&
                    !schema.mode.equals("ro", ignoreCase = true)
            }?.key
    }

    private fun DeviceBean.asSmartDevice(): SmartDevice = SmartDevice(
        id = "$DEVICE_ID_PREFIX$devId",
        name = getName()?.takeIf(String::isNotBlank) ?: "Tuya device",
        transport = DeviceTransport.Wifi,
        endpoint = devId,
        serviceType = "tuya:${getProductId()}",
    )

    private fun configurationError() = IllegalStateException(
        "Tuya SDK не настроен: добавьте thing.appKey, thing.appSecret и app/libs/security-algorithm.aar.",
    )

    private fun tuyaError(code: String, message: String) =
        IllegalStateException("Tuya $code: $message")

    private data class AnonymousIdentity(val uid: String, val password: String)

    companion object {
        const val SCAN_ID_PREFIX = "tuya-scan:"
        const val DEVICE_ID_PREFIX = "tuya-device:"
        private const val DEFAULT_COUNTRY_CODE = "7"
        private const val IDENTITY_PREFERENCES_NAME = "tuya_anonymous_identity"
        private const val KEY_UID = "uid"
        private const val KEY_PASSWORD = "password"
        private const val UNSUPPORTED_BLE_ID_PREFIX = "tuya-ble-unsupported:"
        private const val CONFIG_TYPE_WIFI = "config_type_wifi"
        private const val SCAN_TIMEOUT_MILLIS = 60_000L
        private const val ACTIVATION_TIMEOUT_MILLIS = 120_000L
        private const val WIFI_ACTIVATION_TIMEOUT_MILLIS = 60_000L
        private const val COMMAND_ACK_TIMEOUT_MILLIS = 15_000L
    }
}
