package com.mydev.linkdrop.discovery

import com.mydev.linkdrop.core.model.Device
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

    private val devicesMap = LinkedHashMap<String, Device>()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    fun start() {
        provider.start()
        scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is DiscoveryEvent.Found -> {
                        devicesMap[event.device.id] = event.device
                        _devices.value = devicesMap.values.toList()
                    }
                    is DiscoveryEvent.Updated -> {
                        devicesMap[event.device.id] = event.device
                        _devices.value = devicesMap.values.toList()
                    }
                    is DiscoveryEvent.Lost -> {
                        devicesMap.remove(event.deviceId)
                        _devices.value = devicesMap.values.toList()
                    }
                }
            }
        }
    }

    fun stop() {
        provider.stop()
    }
}