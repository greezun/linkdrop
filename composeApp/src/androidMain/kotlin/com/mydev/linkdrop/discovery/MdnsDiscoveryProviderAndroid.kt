package com.mydev.linkdrop.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.mydev.linkdrop.AndroidDeviceIdStore
import com.mydev.linkdrop.core.model.Capability
import com.mydev.linkdrop.core.model.DiscoveryConstants
import com.mydev.linkdrop.core.model.Device
import com.mydev.linkdrop.core.model.Endpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android mDNS/NSD implementation using [NsdManager].
 *
 * What it does:
 * - Advertises this device as "_linkdrop._tcp" service (registerService)
 * - Discovers other LinkDrop services (discoverServices)
 * - Resolves found services to get host/port/TXT and emits [DiscoveryEvent]
 *
 * Important Android NSD caveats:
 * - Resolve is effectively "one-at-a-time" in many implementations; we serialize resolves.
 *   (Otherwise you can hit FAILURE_ALREADY_ACTIVE on some devices/versions.)  [oai_citation:3‡GitHub](https://github.com/project-chip/connectedhomeip/issues/23709?utm_source=chatgpt.com)
 */
class MdnsDiscoveryProviderAndroid(
    appContext: Context,
    private val serviceType: String = SERVICE_TYPE,
    private val advertisePort: Int = DEFAULT_PORT,
) : DiscoveryProvider {

    private val localDeviceId: String =
        AndroidDeviceIdStore(appContext).getOrCreate()
    private val nsd: NsdManager =
        appContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _events = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = 64)
    override val events: Flow<DiscoveryEvent> = _events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val started = AtomicBoolean(false)

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    // Keep track of discovered services -> deviceId mapping for Lost events
    private val serviceNameToDeviceId = ConcurrentHashMap<String, String>()

    // Serialize resolves (avoid "already active")
    private val resolveQueue = Channel<NsdServiceInfo>(capacity = Channel.BUFFERED)

    override fun start() {
        if (!started.compareAndSet(false, true)) return

        // 1) Start resolver worker (serializes resolve calls)
        scope.launch {

            for (svc in resolveQueue) {
                withContext(Dispatchers.Main) {
                    resolveOnce(svc)
                }
            }
        }

        // 2) Advertise ourselves
        registerService()

        // 3) Discover others
        discoverServices()
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) return

        discoveryListener?.let {
            try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null

        registrationListener?.let {
            try { nsd.unregisterService(it) } catch (_: Exception) {}
        }
        registrationListener = null

        serviceNameToDeviceId.clear()

        resolveQueue.close()
    }

    private fun registerService() {
        val info = NsdServiceInfo().apply {
            // Use localDeviceId suffix to ensure uniqueness in the network
            serviceName = "LinkDrop-${Build.MODEL}-${localDeviceId.takeLast(4)}"
            serviceType = this@MdnsDiscoveryProviderAndroid.serviceType
            port = advertisePort

            setAttribute(TXT_DEVICE_ID, localDeviceId)
            setAttribute(TXT_DEVICE_NAME, Build.MODEL)
        }

        val regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d("NSD", "registered name=${serviceInfo.serviceName} type=${serviceInfo.serviceType} port=${serviceInfo.port}")

            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NSD", "registration FAILED code=$errorCode name=${serviceInfo.serviceName} type=${serviceInfo.serviceType}")

            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        registrationListener = regListener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener)
    }
    private fun discoverServices() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NSD", "discovery started for type=$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("NSD", "FOUND name=${serviceInfo.serviceName} type=${serviceInfo.serviceType}")

                // Filter by type
                // Note: Android NsdManager might return type with a dot or without, 
                // but usually it matches what was passed to discoverServices.
                if (!serviceInfo.serviceType.contains(serviceType)) return

                // Queue resolve (serial)
                scope.launch { resolveQueue.send(serviceInfo) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d("NSD", "LOST name=${serviceInfo.serviceName} type=${serviceInfo.serviceType}")
                val id = serviceNameToDeviceId.remove(serviceInfo.serviceName) ?: serviceInfo.serviceName
                if (id == localDeviceId) return
                scope.launch { _events.emit(DiscoveryEvent.Lost(id)) }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // For MVP: stop and let caller restart later.
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
        }

        discoveryListener = listener
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolveOnce(serviceInfo: NsdServiceInfo) {
        if (!started.get()) return

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // MVP: ignore (could retry)
            }

            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val device = resolved.toDevice()

                // Ignore ourselves
                if (device.id == localDeviceId) return

                serviceNameToDeviceId[resolved.serviceName] = device.id

                scope.launch { _events.emit(DiscoveryEvent.Found(device)) }
            }
        }

        // resolveService is deprecated on newer APIs in favor of resolveService + callbacks variants,
        // but still widely used; we’ll migrate later if needed.
        nsd.resolveService(serviceInfo, resolveListener)
    }

    private fun NsdServiceInfo.toDevice(): Device {
        val id = getTxtString(TXT_DEVICE_ID) ?: serviceName
        val name = getTxtString(TXT_DEVICE_NAME) ?: serviceName

        val hostStr = host?.hostAddress ?: ""
        val endpoint = Endpoint.Lan(host = hostStr, port = port)

        return Device(
            id = id,
            name = name,
            endpoints = listOf(endpoint),
            capabilities = setOf(Capability.LAN),
        )
    }

    private fun NsdServiceInfo.getTxtString(key: String): String? {
        return attributes[key]?.toString(Charsets.UTF_8)
    }

    companion object {
        // Android NSD expects type like "_linkdrop._tcp" (no trailing dot)
        const val SERVICE_TYPE: String = DiscoveryConstants.SERVICE_TYPE

        const val TXT_DEVICE_ID = "id"
        const val TXT_DEVICE_NAME = "name"

        const val DEFAULT_PORT: Int = 58231
        const val DEFAULT_DEVICE_NAME: String = "Android"
    }
}
