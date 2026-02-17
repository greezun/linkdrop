package com.mydev.linkdrop.discovery

import android.content.Context
import com.mydev.linkdrop.core.model.Device
import kotlinx.coroutines.Job
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
