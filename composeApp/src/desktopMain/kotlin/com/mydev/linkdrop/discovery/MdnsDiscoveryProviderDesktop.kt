package com.mydev.linkdrop.discovery

import com.mydev.linkdrop.DesktopDeviceIdStore
import com.mydev.linkdrop.core.model.Capability
import com.mydev.linkdrop.core.model.Device
import com.mydev.linkdrop.core.model.Endpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * Desktop (JVM) mDNS discovery implementation using JmDNS.
 */
class MdnsDiscoveryProviderDesktop(
    private val advertisePort: Int = DEFAULT_PORT,
) : DiscoveryProvider {

    private val localDeviceId: String = DesktopDeviceIdStore().getOrCreate()
    private val localDeviceName: String = getComputerName()
    private val _events = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = 64)
    override val events: Flow<DiscoveryEvent> = _events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var jmdns: JmDNS? = null
    private var listener: ServiceListener? = null
    private var registeredInfo: ServiceInfo? = null

    override fun start() {
        if (jmdns != null) return

        val bindAddr = findWifiIpv4Address()
        val mdns = if (bindAddr != null) JmDNS.create(bindAddr) else JmDNS.create()
        jmdns = mdns

        // 1) Advertise ourselves so other devices can find us
        val props = mapOf(
            TXT_DEVICE_ID to localDeviceId,
            TXT_DEVICE_NAME to localDeviceName,
        )

        // Use localDeviceId suffix to ensure uniqueness in the network
        val uniqueServiceName = "$localDeviceName-${localDeviceId.takeLast(4)}"

        val info = ServiceInfo.create(
            SERVICE_TYPE,
            uniqueServiceName,
            advertisePort,
            0,
            0,
            props
        )

        registeredInfo = info
        mdns.registerService(info)

        // 2) Discover other services
        val l = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                // Resolve full info (host/port/TXT). Recommended by JmDNS.
                mdns.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                val lostId = event.info?.getPropertyString(TXT_DEVICE_ID) ?: event.name
                if (lostId == localDeviceId) return

                scope.launch { _events.emit(DiscoveryEvent.Lost(lostId)) }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val infoResolved = event.info ?: return

                val remoteId = infoResolved.getPropertyString(TXT_DEVICE_ID) ?: infoResolved.name
                if (remoteId == localDeviceId) return

                val device = infoResolved.toDevice()
                scope.launch { _events.emit(DiscoveryEvent.Found(device)) }
            }
        }

        listener = l
        mdns.addServiceListener(SERVICE_TYPE, l)
    }

    override fun stop() {
        val mdns = jmdns ?: return

        listener?.let {
            mdns.removeServiceListener(SERVICE_TYPE, it)
        }
        listener = null

        registeredInfo?.let {
            try {
                mdns.unregisterService(it)
            } catch (_: Exception) { /* ignore */ }
        }
        registeredInfo = null

        jmdns = null
        try {
            mdns.close()
        } catch (_: Exception) { /* ignore */ }
    }

    private fun ServiceInfo.toDevice(): Device {
        val id = getPropertyString(TXT_DEVICE_ID) ?: name
        val deviceName = getPropertyString(TXT_DEVICE_NAME) ?: name

        val host = inet4Addresses.firstOrNull()?.hostAddress
            ?: inetAddresses.firstOrNull()?.hostAddress
            ?: ""

        val endpoint = Endpoint.Lan(host = host, port = port)

        return Device(
            id = id,
            name = deviceName,
            endpoints = listOf(endpoint),
            capabilities = setOf(Capability.LAN),
        )
    }

    private fun getComputerName(): String {
        return try {
            InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "Desktop"
        }
    }

    companion object {
        // Aligned with Android: JmDNS handles the trailing dot and .local automatically if needed,
        // but typically "_linkdrop._tcp.local." is the full type.
        // We use the same short type as on Android for consistency in registration.
        const val SERVICE_TYPE: String = "_linkdrop._tcp.local."

        const val TXT_DEVICE_ID = "id"
        const val TXT_DEVICE_NAME = "name"

        const val DEFAULT_PORT: Int = 58231
    }
}
