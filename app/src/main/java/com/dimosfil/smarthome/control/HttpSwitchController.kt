package com.dimosfil.smarthome.control

import com.dimosfil.smarthome.model.DeviceTransport
import com.dimosfil.smarthome.model.SmartDevice
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpSwitchController(
    private val statePath: String = "/state",
    private val commandPath: String = "/switch",
) : SwitchController {
    override suspend fun readState(device: SmartDevice): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            requireWifi(device)
            SwitchStateCodec.parse(request(device, "GET", statePath))
        }
    }

    override suspend fun setPower(device: SmartDevice, enabled: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireWifi(device)
                val response = request(
                    device = device,
                    method = "POST",
                    path = commandPath,
                    body = "{\"on\":$enabled}",
                )
                if (response.isBlank()) enabled else SwitchStateCodec.parse(response)
            }
        }

    private fun requireWifi(device: SmartDevice) {
        require(device.transport == DeviceTransport.Wifi) {
            "Для управления BLE-устройством нужен адаптер протокола производителя"
        }
    }

    private fun request(
        device: SmartDevice,
        method: String,
        path: String,
        body: String? = null,
    ): String {
        val connection = URL("http://${device.endpoint}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.setRequestProperty("Accept", "application/json, text/plain")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            check(status in 200..299) { "Устройство вернуло HTTP $status" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal object SwitchStateCodec {
    fun parse(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return when {
            normalized in setOf("on", "true", "1") -> true
            normalized in setOf("off", "false", "0") -> false
            Regex("\"(?:on|enabled|power)\"\\s*:\\s*true").containsMatchIn(normalized) -> true
            Regex("\"(?:on|enabled|power)\"\\s*:\\s*false").containsMatchIn(normalized) -> false
            else -> error("Не удалось распознать состояние выключателя")
        }
    }
}
