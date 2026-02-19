package com.mydev.linkdrop.transfer

/**
 * Payload sent from one device to another when sharing a URL.
 */
data class ShareUrlRequest(
    val url: String,
    val fromDeviceId: String,
    val fromName: String,
    val sentAtEpochMs: Long,
)

/**
 * URL item stored on the receiving side.
 */
data class ReceivedUrl(
    val url: String,
    val fromDeviceId: String,
    val fromName: String,
    val receivedAtEpochMs: Long,
)
