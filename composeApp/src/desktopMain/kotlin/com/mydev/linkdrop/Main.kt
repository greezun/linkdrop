package com.mydev.linkdrop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mydev.linkdrop.core.model.Device
import com.mydev.linkdrop.core.model.Endpoint
import com.mydev.linkdrop.discovery.DesktopDiscovery
import com.mydev.linkdrop.discovery.findWifiIpv4Address
import com.mydev.linkdrop.transfer.DesktopLanTransferApi
import com.mydev.linkdrop.transfer.DesktopUrlReceiver
import com.mydev.linkdrop.transfer.ShareUrlRequest
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.InetAddress
import java.net.URI


fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "LinkDrop") {

        // Create and start discovery once
        val discovery = remember { DesktopDiscovery() }
        val receiver = remember { DesktopUrlReceiver() }
        val transferApi = remember { DesktopLanTransferApi() }
        val localDeviceId = remember { DesktopDeviceIdStore().getOrCreate() }
        val localLanHost = remember { findWifiIpv4Address()?.hostAddress }
        val senderName = remember {
            runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Desktop")
        }
        val scope = rememberCoroutineScope()

        DisposableEffect(Unit) {
            discovery.start()
            receiver.start()
            onDispose {
                receiver.stop()
                discovery.stop()
            }
        }

        val devices by discovery.devices.collectAsState()
        val receivedUrls by receiver.receivedUrls.collectAsState()
        val androidTargets = remember(devices, localDeviceId, localLanHost) {
            devices.filter { device ->
                val lanHost = (device.endpoints.firstOrNull() as? Endpoint.Lan)?.host
                device.id != localDeviceId &&
                    (localLanHost == null || lanHost != localLanHost) &&
                    isLikelyAndroidDevice(device)
            }
        }

        var sendUrl by remember { mutableStateOf("") }
        var sendStatus by remember { mutableStateOf<String?>(null) }
        var openStatus by remember { mutableStateOf<String?>(null) }

        MaterialTheme {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Devices on LAN (mDNS)", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                if (devices.isEmpty()) {
                    Text("No devices found yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(devices) { d ->
                            val lan = d.endpoints.firstOrNull() as? Endpoint.Lan
                            Column {
                                Text("${d.name} (${d.id.take(8)})")
                                Text("LAN: ${lan?.host ?: "?"}:${lan?.port ?: "?"}")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Send URL to Android", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sendUrl,
                    onValueChange = { sendUrl = it; sendStatus = null },
                    label = { Text("URL to send") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                sendStatus?.let {
                    Text(it)
                    Spacer(Modifier.height(8.dp))
                }

                if (androidTargets.isEmpty()) {
                    Text("No Android devices available for send.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(androidTargets) { target ->
                            Column {
                                Text("${target.name} (${target.id.take(8)})")
                                Button(
                                    onClick = {
                                        val trimmed = sendUrl.trim()
                                        if (trimmed.isBlank()) {
                                            sendStatus = "Enter URL first"
                                            return@Button
                                        }
                                        sendStatus = "Sending..."
                                        scope.launch {
                                            runCatching {
                                                transferApi.sendUrl(
                                                    target = target,
                                                    payload = ShareUrlRequest(
                                                        url = trimmed,
                                                        fromDeviceId = localDeviceId,
                                                        fromName = senderName,
                                                        sentAtEpochMs = System.currentTimeMillis(),
                                                    ),
                                                )
                                            }.onSuccess {
                                                sendStatus = "Sent"
                                            }.onFailure { err ->
                                                sendStatus = "Send failed: ${err.message ?: "unknown error"}"
                                            }
                                        }
                                    }
                                ) {
                                    Text("Send to this Android")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Received URLs", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                openStatus?.let {
                    Text(it)
                    Spacer(Modifier.height(8.dp))
                }

                if (receivedUrls.isEmpty()) {
                    Text("No URLs received yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(receivedUrls) { item ->
                            Column {
                                Text(item.url)
                                Text("From: ${item.fromName} (${item.fromDeviceId.take(8)})")
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        runCatching {
                                            if (!Desktop.isDesktopSupported()) {
                                                error("Desktop API is not supported")
                                            }
                                            Desktop.getDesktop().browse(URI(item.url))
                                        }.onSuccess {
                                            openStatus = "Opened"
                                        }.onFailure { err ->
                                            openStatus = "Open failed: ${err.message ?: "unknown error"}"
                                        }
                                    }
                                ) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isLikelyAndroidDevice(device: Device): Boolean {
    val name = device.name.lowercase()
    return name.contains("android") ||
        name.contains("pixel") ||
        name.contains("samsung") ||
        name.contains("xiaomi") ||
        name.contains("redmi") ||
        name.contains("oneplus") ||
        name.startsWith("sdk")
}
