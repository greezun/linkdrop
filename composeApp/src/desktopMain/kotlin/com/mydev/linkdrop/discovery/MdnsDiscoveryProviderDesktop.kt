package com.mydev.linkdrop.discovery

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
import java.util.UUID
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

/**
 * Desktop (JVM) mDNS discovery implementation using JmDNS.
 *
 * What it does:
 * - Advertises this device as a LinkDrop service in local network (mDNS/Bonjour).
 * - Discovers other LinkDrop devices and emits [DiscoveryEvent].
 *
 * What it does NOT do (yet):
 * - Persist stable deviceId between app launches (we use random UUID by default).
 * - Emit Updated events (only Found/Lost).
 */
class MdnsDiscoveryProviderDesktop(
    private val serviceType: String = SERVICE_TYPE,
    private val bindAddress: InetAddress? = null,

    // Local identity (for MVP: defaults are OK, later we’ll make it stable & configurable)
    private val localDeviceId: String = UUID.randomUUID().toString(),
    private val localDeviceName: String = DEFAULT_DEVICE_NAME,

    // Port of future local HTTP server (for MVP: fixed is OK)
    private val advertisePort: Int = DEFAULT_PORT,
) : DiscoveryProvider {

    private val _events = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = 64)
    override val events: Flow<DiscoveryEvent> = _events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var jmdns: JmDNS? = null
    private var listener: ServiceListener? = null
    private var registeredInfo: ServiceInfo? = null

    override fun start() {
        if (jmdns != null) return

        val mdns = if (bindAddress != null) JmDNS.create(bindAddress) else JmDNS.create()
        jmdns = mdns

        // 1) Advertise ourselves so other devices can find us
        val props = mapOf(
            TXT_DEVICE_ID to localDeviceId,
            TXT_DEVICE_NAME to localDeviceName,
            // later: "pv" to "1", "cap" to "lan"
        )

        val info = ServiceInfo.create(
            serviceType,
            // Instance name visible in Bonjour browsers
            "LinkDrop-$localDeviceName",
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
                // event.info can be null here, so Lost by service name fallback
                val lostId = event.info?.getPropertyString(TXT_DEVICE_ID) ?: event.name
                // Ignore ourselves
                if (lostId == localDeviceId) return

                scope.launch { _events.emit(DiscoveryEvent.Lost(lostId)) }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val infoResolved = event.info ?: return

                // Ignore ourselves (important: otherwise you’ll see your own device)
                val remoteId = infoResolved.getPropertyString(TXT_DEVICE_ID) ?: infoResolved.name
                if (remoteId == localDeviceId) return

                val device = infoResolved.toDevice()
                scope.launch { _events.emit(DiscoveryEvent.Found(device)) }
            }
        }

        listener = l
        mdns.addServiceListener(serviceType, l)
    }

    override fun stop() {
        val mdns = jmdns ?: return

        // Stop discovery
        listener?.let { mdns.removeServiceListener(serviceType, it) }
        listener = null

        // Remove advertised service
        registeredInfo?.let {
            try {
                mdns.unregisterService(it)
            } catch (_: Exception) { /* ignore */ }
        }
        registeredInfo = null

        jmdns = null
        try {
            mdns.close()
        } catch (_: Exception) {
            // ignore
        }
    }

    /**
     * Converts mDNS service info into our shared [Device] model.
     */
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

    companion object {
        const val SERVICE_TYPE: String = "_linkdrop._tcp.local."

        const val TXT_DEVICE_ID = "id"
        const val TXT_DEVICE_NAME = "name"

        const val DEFAULT_PORT: Int = 58231
        const val DEFAULT_DEVICE_NAME: String = "Desktop"
    }
}