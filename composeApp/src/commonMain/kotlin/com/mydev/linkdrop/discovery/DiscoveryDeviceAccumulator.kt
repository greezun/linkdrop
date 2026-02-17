package com.mydev.linkdrop.discovery

import com.mydev.linkdrop.core.model.Device
import com.mydev.linkdrop.core.model.Endpoint

/**
 * Shared device-list accumulator for discovery events.
 *
 * Keeps platform behavior consistent:
 * - handles Found/Updated/Lost
 * - merges duplicates by LAN endpoint when a canonical UUID id appears
 */
class DiscoveryDeviceAccumulator {
    private val devicesMap = LinkedHashMap<String, Device>()

    fun reset() {
        devicesMap.clear()
    }

    fun onEvent(event: DiscoveryEvent): List<Device> {
        when (event) {
            is DiscoveryEvent.Found -> upsertWithLanMerge(event.device)
            is DiscoveryEvent.Updated -> upsertWithLanMerge(event.device)
            is DiscoveryEvent.Lost -> devicesMap.remove(event.deviceId)
        }

        return devicesMap.values.toList()
    }

    private fun Device.lanKeyOrNull(): String? {
        val lan = endpoints.filterIsInstance<Endpoint.Lan>().firstOrNull() ?: return null
        return "${lan.host}:${lan.port}"
    }

    private fun looksLikeUuid(id: String): Boolean {
        return id.length >= 32 && id.count { it == '-' } >= 4
    }

    private fun upsertWithLanMerge(device: Device) {
        val key = device.lanKeyOrNull()

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
