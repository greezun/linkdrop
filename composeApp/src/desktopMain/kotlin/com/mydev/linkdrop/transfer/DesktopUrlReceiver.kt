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
        created.createContext(TransferProtocol.SHARE_URL_PATH) { exchange ->
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

        if (exchange.requestURI.path != TransferProtocol.SHARE_URL_PATH) {
            logger.warning("Rejected request: unknown path=${exchange.requestURI.path}")
            writeResponse(exchange, 404, "not found")
            return
        }

        val payload = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
        val parsed = TransferProtocol.parseShareUrlRequest(payload)
        if (parsed == null) {
            logger.warning("Rejected request: invalid payload json=$payload")
            writeResponse(exchange, 400, "invalid payload")
            return
        }

        if (!TransferProtocol.isValidUrl(parsed.url)) {
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

    companion object {
        private val logger: Logger = Logger.getLogger(DesktopUrlReceiver::class.java.name)
    }
}
