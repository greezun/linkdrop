package com.mydev.linkdrop.core.model

/**
 * Logical representation of a remote device
 * discovered on the network.
 *
 * Device is immutable and contains only descriptive data.
 * All connection logic is handled elsewhere.
 */
data class Device(
    val id: String,
    val name: String,
    val endpoints: List<Endpoint>,
    val capabilities: Set<Capability>,
    val platform: DevicePlatform = DevicePlatform.UNKNOWN,
)
