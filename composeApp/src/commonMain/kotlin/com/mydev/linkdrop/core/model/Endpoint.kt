package com.mydev.linkdrop.core.model

/**
 * Represents a concrete network endpoint
 * that can be used to establish a connection
 * with a device.
 *
 * Endpoint does NOT perform any networking by itself,
 * it only describes how the device can be reached.
 */
sealed interface Endpoint {

    /**
     * Local network (LAN) endpoint reachable via IP and port.
     */
    data class Lan(val host: String, val port: Int) : Endpoint
}

object DiscoveryConstants {
    // Canonical DNS-SD service type for LinkDrop discovery.
    const val SERVICE_TYPE: String = "_linkdrop._tcp"
}
