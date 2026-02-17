package com.mydev.linkdrop.discovery

import com.mydev.linkdrop.core.model.Device
import com.mydev.linkdrop.core.model.Endpoint
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

    private val devicesMap = LinkedHashMap<String, Device>()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    fun start() {
        if (collectJob?.isActive == true) return

        devicesMap.clear()
        _devices.value = emptyList()

        provider.start()
        collectJob = scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is DiscoveryEvent.Found -> {
                        upsertWithLanMerge(devicesMap, event.device)
                    }
                    is DiscoveryEvent.Updated -> {
                        upsertWithLanMerge(devicesMap, event.device)
                    }
                    is DiscoveryEvent.Lost -> {
                        devicesMap.remove(event.deviceId)
                    }
                }
                _devices.value = devicesMap.values.toList()
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        provider.stop()
    }

    private fun Device.lanKeyOrNull(): String? {
        val lan = endpoints.filterIsInstance<Endpoint.Lan>().firstOrNull() ?: return null
        return "${lan.host}:${lan.port}"
    }

    private fun looksLikeUuid(id: String): Boolean {
        // простий і достатній хак для MVP
        return id.length >= 32 && id.count { it == '-' } >= 4
    }

    private fun upsertWithLanMerge(
        devicesMap: MutableMap<String, Device>,
        device: Device
    ) {
        val key = device.lanKeyOrNull()

        // Якщо прийшов "канонічний" id (UUID), зносимо дублі по тому ж LAN endpoint
        if (key != null && looksLikeUuid(device.id)) {
            val duplicateId = devicesMap.entries
                .firstOrNull { (id, existing) ->
                    id != device.id && existing.lanKeyOrNull() == key
                }
                ?.key

            if (duplicateId != null) {
                devicesMap.remove(duplicateId)
            }
        }

        devicesMap[device.id] = device
    }
}
