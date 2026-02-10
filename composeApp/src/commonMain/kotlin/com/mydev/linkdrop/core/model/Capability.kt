package com.mydev.linkdrop.core.model

/**
 * Describes what transport or discovery capabilities
 * a device supports.
 *
 * Used during discovery and link negotiation to choose
 * the best available connection method.
 */
enum class Capability {
    LAN,
    BLE,
    WIFI_DIRECT
}