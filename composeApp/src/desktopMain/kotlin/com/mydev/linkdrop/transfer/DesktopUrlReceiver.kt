package com.mydev.linkdrop.transfer

import com.mydev.linkdrop.discovery.MdnsDiscoveryProviderDesktop
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

class DesktopUrlReceiver(
    private val port: Int = MdnsDiscoveryProviderDesktop.DEFAULT_PORT,
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    private val _receivedUrls = MutableStateFlow<List<ReceivedUrl>>(emptyList())
    val receivedUrls: StateFlow<List<ReceivedUrl>> = _receivedUrls

    fun start() {
        if (server != null) return

        val created = HttpServer.create(InetSocketAddress(port), 0)
        created.createContext("/share/url") { exchange ->
            handleShareUrl(exchange)
        }

        val createdExecutor = Executors.newSingleThreadExecutor()
        created.executor = createdExecutor
        created.start()

        executor = createdExecutor
        server = created
    }

    fun stop() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    private fun handleShareUrl(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            logger.warning("Rejected request: method=${exchange.requestMethod} path=${exchange.requestURI.path}")
            writeResponse(exchange, 405, "method not allowed")
            return
        }

        val payload = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
        val parsed = parseShareUrlRequest(payload)
        if (parsed == null) {
            logger.warning("Rejected request: invalid payload json=$payload")
            writeResponse(exchange, 400, "invalid payload")
            return
        }

        if (!isValidUrl(parsed.url)) {
            logger.warning("Rejected request: invalid url='${parsed.url}' payload=$payload")
            writeResponse(exchange, 400, "invalid url")
            return
        }

        val received = ReceivedUrl(
            url = parsed.url,
            fromDeviceId = parsed.fromDeviceId,
            fromName = parsed.fromName,
            receivedAtEpochMs = System.currentTimeMillis(),
        )

        _receivedUrls.update { current ->
            listOf(received) + current
        }

        logger.info("Accepted URL from='${received.fromName}' url='${received.url}'")
        writeResponse(exchange, 200, "ok")
    }

    private fun writeResponse(exchange: HttpExchange, statusCode: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        try {
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { out ->
                out.write(bytes)
            }
        } catch (t: Throwable) {
            logger.log(Level.SEVERE, "Failed to write response status=$statusCode body=$body", t)
        }
    }

    private fun parseShareUrlRequest(rawJson: String): ShareUrlRequest? {
        val url = extractJsonString(rawJson, "url") ?: return null
        val fromDeviceId = extractJsonString(rawJson, "fromDeviceId") ?: return null
        val fromName = extractJsonString(rawJson, "fromName") ?: return null
        val sentAtEpochMs = extractJsonLong(rawJson, "sentAtEpochMs") ?: return null

        return ShareUrlRequest(
            url = url,
            fromDeviceId = fromDeviceId,
            fromName = fromName,
            sentAtEpochMs = sentAtEpochMs,
        )
    }

    private fun extractJsonString(rawJson: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        val value = pattern.find(rawJson)?.groupValues?.get(1) ?: return null
        return unescapeJsonString(value)
    }

    private fun extractJsonLong(rawJson: String, key: String): Long? {
        val pattern = Regex("\"$key\"\\s*:\\s*(\\d+)")
        return pattern.find(rawJson)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun unescapeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun isValidUrl(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    companion object {
        private val logger: Logger = Logger.getLogger(DesktopUrlReceiver::class.java.name)
    }
}
