package com.dimosfil.smarthome.control

import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ShellySwitchController(
    private val client: ShellyRpcClient = ShellyRpcClient(),
) : SwitchController {
    override suspend fun readState(device: SmartDevice): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            requireLocalWifi(device)
            client.readSwitchState(device.endpoint)
        }
    }

    override suspend fun setPower(device: SmartDevice, enabled: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireLocalWifi(device)
                client.setSwitchPower(device.endpoint, enabled)
                val confirmed = client.readSwitchState(device.endpoint)
                check(confirmed == enabled) {
                    "Shelly приняла команду, но фактическое состояние реле не изменилось."
                }
                confirmed
            }
        }

    private fun requireLocalWifi(device: SmartDevice) {
        require(device.transport == DeviceTransport.Wifi) {
            "Локальный Shelly-контроллер работает только через Wi‑Fi."
        }
    }
}

data class ShellyDeviceInfo(
    val id: String,
    val model: String,
)

class ShellyRpcClient(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    fun readSwitchState(endpoint: String): Boolean =
        ShellyRpcCodec.output(request(endpoint, "GET", "/rpc/Switch.GetStatus?id=0"))

    fun setSwitchPower(endpoint: String, enabled: Boolean) {
        val response = request(endpoint, "GET", "/rpc/Switch.Set?id=0&on=$enabled")
        ShellyRpcCodec.ensureNoRpcError(response)
    }

    fun readDeviceInfo(endpoint: String): ShellyDeviceInfo {
        val json = JSONObject(request(endpoint, "GET", "/rpc/Shelly.GetDeviceInfo"))
        ShellyRpcCodec.ensureNoRpcError(json.toString())
        val id = json.optString("id").ifBlank { error("Shelly не вернула идентификатор устройства.") }
        return ShellyDeviceInfo(
            id = id,
            model = json.optString("model").ifBlank { id },
        )
    }

    fun configureWifi(endpoint: String, ssid: String, password: String) {
        val body = JSONObject()
            .put(
                "config",
                JSONObject().put(
                    "sta",
                    JSONObject()
                        .put("enable", true)
                        .put("ssid", ssid)
                        .put("pass", password),
                ),
            )
            .toString()
        ShellyRpcCodec.ensureNoRpcError(
            request(endpoint, "POST", "/rpc/WiFi.SetConfig", body),
        )
    }

    private fun request(
        endpoint: String,
        method: String,
        path: String,
        body: String? = null,
    ): String {
        val base = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            endpoint.trimEnd('/')
        } else {
            "http://${endpoint.trimEnd('/')}"
        }
        val connection = connectionFactory(URL("$base$path"))
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            check(status in 200..299) {
                "Shelly вернула HTTP $status${response.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 5_000
    }
}

internal object ShellyRpcCodec {
    fun output(response: String): Boolean {
        ensureNoRpcError(response)
        val match = OUTPUT_PATTERN.find(response)
            ?: error("Shelly не вернула состояние компонента Switch:0.")
        return match.groupValues[1].toBooleanStrict()
    }

    fun ensureNoRpcError(response: String) {
        if (response.isBlank()) return
        if (!ERROR_OBJECT_PATTERN.containsMatchIn(response)) return
        val code = ERROR_CODE_PATTERN.find(response)?.groupValues?.get(1).orEmpty().ifBlank { "?" }
        val message = ERROR_MESSAGE_PATTERN.find(response)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            .orEmpty()
            .ifBlank { "неизвестная RPC-ошибка" }
        throw IllegalStateException("Shelly RPC $code: $message")
    }

    private val OUTPUT_PATTERN = Regex("\\\"output\\\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
    private val ERROR_OBJECT_PATTERN = Regex("\\\"error\\\"\\s*:\\s*\\{")
    private val ERROR_CODE_PATTERN = Regex("\\\"code\\\"\\s*:\\s*(-?\\d+)")
    private val ERROR_MESSAGE_PATTERN = Regex("\\\"message\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
}
