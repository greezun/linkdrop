package com.mydev.linkdrop.transfer

import android.util.Log
import com.mydev.linkdrop.discovery.MdnsDiscoveryProviderAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets

class AndroidUrlReceiver(
    private val port: Int = MdnsDiscoveryProviderAndroid.DEFAULT_PORT,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _receivedUrls = MutableStateFlow<List<ReceivedUrl>>(emptyList())
    val receivedUrls: StateFlow<List<ReceivedUrl>> = _receivedUrls

    fun start() {
        if (acceptJob?.isActive == true) return

        val server = ServerSocket(port)
        serverSocket = server
        acceptJob = scope.launch {
            while (isActive) {
                try {
                    val socket = server.accept()
                    launch { handleClient(socket) }
                } catch (_: SocketException) {
                    break
                } catch (t: Throwable) {
                    Log.e(TAG, "accept failed", t)
                }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        serverSocket?.close()
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = SOCKET_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val methodLine = reader.readLine() ?: run {
                    writeResponse(s, 400, "bad request")
                    return
                }

                val method = methodLine.substringBefore(' ')
                val path = methodLine.substringAfter(' ').substringBefore(' ')

                var contentLength = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                    }
                }

                if (method != "POST" || path != "/share/url") {
                    writeResponse(s, 404, "not found")
                    return
                }

                if (contentLength <= 0) {
                    writeResponse(s, 400, "invalid payload")
                    return
                }

                val bodyChars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }

                if (totalRead != contentLength) {
                    writeResponse(s, 400, "invalid payload")
                    return
                }

                val payload = String(bodyChars)
                val parsed = parseShareUrlRequest(payload)
                if (parsed == null || !isValidUrl(parsed.url)) {
                    writeResponse(s, 400, "invalid payload")
                    return
                }

                val received = ReceivedUrl(
                    url = parsed.url,
                    fromDeviceId = parsed.fromDeviceId,
                    fromName = parsed.fromName,
                    receivedAtEpochMs = System.currentTimeMillis(),
                )
                _receivedUrls.update { listOf(received) + it }
                writeResponse(s, 200, "ok")
            } catch (t: Throwable) {
                Log.e(TAG, "request handling failed", t)
                writeResponse(s, 500, "internal error")
            }
        }
    }

    private fun writeResponse(socket: Socket, code: Int, body: String) {
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val response = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("\r\n")
            append(body)
        }
        socket.getOutputStream().write(response.toByteArray(StandardCharsets.UTF_8))
        socket.getOutputStream().flush()
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
        private const val TAG = "AndroidUrlReceiver"
        private const val SOCKET_TIMEOUT_MS = 5_000
    }
}
