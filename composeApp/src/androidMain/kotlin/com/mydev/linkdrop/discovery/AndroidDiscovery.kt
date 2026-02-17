package com.mydev.linkdrop.discovery

import android.content.Context
import com.mydev.linkdrop.core.model.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Android-only helper that:
 * - starts NSD discovery
 * - collects [DiscoveryEvent] into a device list for UI
 *
 * Why it exists:
 * keep platform wiring in androidMain for now.
 */
class AndroidDiscovery(
    context: Context,
    private val provider: DiscoveryProvider = MdnsDiscoveryProviderAndroid(context.applicationContext)
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