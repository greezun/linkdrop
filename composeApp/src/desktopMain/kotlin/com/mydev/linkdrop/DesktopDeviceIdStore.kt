package com.mydev.linkdrop

import java.io.File
import java.util.UUID

class DesktopDeviceIdStore : DeviceIdStore {
    private val file = File(System.getProperty("user.home"), ".linkdrop/device_id")

    override fun getOrCreate(): String {
        if (file.exists()) {
            val existing = file.readText().trim()
            if (existing.isNotBlank()) return existing
        }

        val created = UUID.randomUUID().toString()
        file.parentFile?.mkdirs()
        file.writeText(created)
        return created
    }
}

