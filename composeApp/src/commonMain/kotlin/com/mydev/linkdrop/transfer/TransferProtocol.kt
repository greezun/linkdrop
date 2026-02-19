package com.mydev.linkdrop.transfer

object TransferProtocol {
    const val SHARE_URL_PATH: String = "/share/url"

    fun shareUrlEndpoint(host: String, port: Int): String {
        val normalizedHost = normalizeHostForUrl(host)
        return "http://$normalizedHost:$port$SHARE_URL_PATH"
    }

    fun serializeShareUrlRequest(payload: ShareUrlRequest): String {
        return buildString {
            append("{")
            append("\"url\":\"").append(escapeJson(payload.url)).append("\",")
            append("\"fromDeviceId\":\"").append(escapeJson(payload.fromDeviceId)).append("\",")
            append("\"fromName\":\"").append(escapeJson(payload.fromName)).append("\",")
            append("\"sentAtEpochMs\":").append(payload.sentAtEpochMs)
            append("}")
        }
    }

    fun parseShareUrlRequest(rawJson: String): ShareUrlRequest? {
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

    fun isValidUrl(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
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

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun normalizeHostForUrl(rawHost: String): String {
        val host = rawHost.substringBefore('%')
        if (host.startsWith("[") && host.endsWith("]")) return host
        return if (host.contains(':')) "[$host]" else host
    }
}
