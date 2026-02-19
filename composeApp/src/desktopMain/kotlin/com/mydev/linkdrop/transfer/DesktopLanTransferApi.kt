package com.mydev.linkdrop.transfer

import com.mydev.linkdrop.core.model.Device
import com.mydev.linkdrop.core.model.Endpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger

class DesktopLanTransferApi : TransferApi {

    override suspend fun sendUrl(target: Device, payload: ShareUrlRequest) {
        val lan = target.endpoints.filterIsInstance<Endpoint.Lan>().firstOrNull()
            ?: throw IllegalArgumentException("Target device has no LAN endpoint")

        val endpoint = "http://${lan.host}:${lan.port}/share/url"
        val bodyBytes = payload.toJson().toByteArray(StandardCharsets.UTF_8)

        withContext(Dispatchers.IO) {
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                try {
                    connection.outputStream.use { out ->
                        out.write(bodyBytes)
                    }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        logger.severe("sendUrl failed endpoint=$endpoint http=$code body=$errorText payload=${payload.toJson()}")
                        throw IOException("URL send failed: HTTP $code${if (errorText.isNullOrBlank()) "" else " - $errorText"}")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (t: Throwable) {
                logger.log(Level.SEVERE, "sendUrl exception endpoint=$endpoint payload=${payload.toJson()}", t)
                throw t
            }
        }
    }

    private fun ShareUrlRequest.toJson(): String {
        return buildString {
            append("{")
            append("\"url\":\"").append(escapeJson(url)).append("\",")
            append("\"fromDeviceId\":\"").append(escapeJson(fromDeviceId)).append("\",")
            append("\"fromName\":\"").append(escapeJson(fromName)).append("\",")
            append("\"sentAtEpochMs\":").append(sentAtEpochMs)
            append("}")
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private val logger: Logger = Logger.getLogger(DesktopLanTransferApi::class.java.name)
        const val CONNECT_TIMEOUT_MS: Int = 4_000
        const val READ_TIMEOUT_MS: Int = 4_000
    }
}
