package com.mydev.linkdrop.discovery

import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific discovery implementation contract.
 *
 * Implementations are responsible for discovering devices
 * (e.g. via mDNS, BLE) and emitting discovery events.
 *
 * This interface does NOT define how devices are connected.
 */
interface DiscoveryProvider {

    /**
     * Stream of discovery events emitted by the provider.
     */
    val events: Flow<DiscoveryEvent>

    /**
     * Starts device discovery.
     */
    fun start()

    /**
     * Stops device discovery and releases resources.
     */
    fun stop()
}