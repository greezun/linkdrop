package com.mydev.linkdrop

import android.content.Context
import java.util.UUID
import androidx.core.content.edit

class AndroidDeviceIdStore(private val context: Context) : DeviceIdStore {
    private val prefs = context.getSharedPreferences("linkdrop_prefs", Context.MODE_PRIVATE)

    override fun getOrCreate(): String {
        val key = "device_id"
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit { putString(key, created) }
        return created
    }
}

