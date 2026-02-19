package com.mydev.linkdrop.ui

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
import com.mydev.linkdrop.core.model.Device
import com.mydev.linkdrop.core.model.Endpoint
import com.mydev.linkdrop.transfer.ReceivedUrl

@Composable
fun DevicesScreen(
    devices: List<Device>,
    receivedUrls: List<ReceivedUrl>,
    onSendUrl: (device: Device, url: String, onResult: (String) -> Unit) -> Unit,
    onOpenUrl: (url: String, onResult: (String) -> Unit) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var sendStatus by remember { mutableStateOf<String?>(null) }
    var openStatus by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Devices on LAN (mDNS)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; sendStatus = null },
            label = { Text("URL to send") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        sendStatus?.let {
            Text(it)
            Spacer(Modifier.height(8.dp))
        }

        if (devices.isEmpty()) {
            Text("No devices found yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { d ->
                    val lan = d.endpoints.firstOrNull() as? Endpoint.Lan
                    Column(Modifier.fillMaxWidth()) {
                        Text("${d.name} (${d.id.take(8)})")
                        Text("LAN: ${lan?.host ?: "?"}:${lan?.port ?: "?"}")
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val trimmed = url.trim()
                                if (trimmed.isBlank()) {
                                    sendStatus = "Enter URL first"
                                    return@Button
                                }
                                sendStatus = "Sending..."
                                onSendUrl(d, trimmed) { message -> sendStatus = message }
                            }
                        ) {
                            Text("Send to this device")
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
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.url)
                        Text("From: ${item.fromName} (${item.fromDeviceId.take(8)})")
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                onOpenUrl(item.url) { message -> openStatus = message }
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
