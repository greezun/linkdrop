package com.mydev.linkdrop.discovery

import com.mydev.linkdrop.core.model.Device

/**
 * Events emitted by discovery providers
 * to notify about device availability changes.
 *
 * These events are consumed by higher-level
 * services or UI layers.
 */
sealed interface DiscoveryEvent {

    /**
     * A new device has been discovered.
     */
    data class Found(val device: Device) : DiscoveryEvent

    /**
     * An already known device has updated information.
     */
    data class Updated(val device: Device) : DiscoveryEvent

    /**
     * A previously discovered device is no longer available.
     */
    data class Lost(val deviceId: String) : DiscoveryEvent
}