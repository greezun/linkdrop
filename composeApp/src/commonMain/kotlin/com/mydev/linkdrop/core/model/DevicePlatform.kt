package com.mydev.linkdrop.core.model

enum class DevicePlatform {
    ANDROID,
    DESKTOP,
    IOS,
    UNKNOWN;

    fun toWireValue(): String = name.lowercase()

    companion object {
        fun fromWireValue(value: String?): DevicePlatform {
            if (value.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
