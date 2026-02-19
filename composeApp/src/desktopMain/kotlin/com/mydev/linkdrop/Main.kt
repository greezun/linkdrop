package com.mydev.linkdrop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mydev.linkdrop.core.model.Endpoint
import com.mydev.linkdrop.discovery.DesktopDiscovery
import com.mydev.linkdrop.transfer.DesktopUrlReceiver
import java.awt.Desktop
import java.net.URI


fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "LinkDrop") {

        // Create and start discovery once
        val discovery = remember { DesktopDiscovery() }
        val receiver = remember { DesktopUrlReceiver() }
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
