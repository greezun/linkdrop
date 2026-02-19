package com.mydev.linkdrop.transfer

import com.mydev.linkdrop.core.model.Device

/**
 * Contract for sending transfer payloads to a target device.
 */
interface TransferApi {
    suspend fun sendUrl(target: Device, payload: ShareUrlRequest)
}
