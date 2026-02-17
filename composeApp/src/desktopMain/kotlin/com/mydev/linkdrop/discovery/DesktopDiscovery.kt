package com.mydev.linkdrop.discovery

import com.mydev.linkdrop.core.model.Device
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Desktop-only helper that:
 * - starts mDNS discovery
 * - collects [DiscoveryEvent] into a device list for UI
 *
 * Why it exists:
 * We keep commonMain free of platform wiring for now.
 * Later we can move this into shared "DiscoveryService".
 */
class DesktopDiscovery(
    private val provider: DiscoveryProvider = MdnsDiscoveryProviderDesktop()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    private val accumulator = DiscoveryDeviceAccumulator()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    fun start() {
        if (collectJob?.isActive == true) return

        accumulator.reset()
        _devices.value = emptyList()

        provider.start()
        collectJob = scope.launch {
            provider.events.collect { event ->
                _devices.value = accumulator.onEvent(event)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        provider.stop()
    }
}
