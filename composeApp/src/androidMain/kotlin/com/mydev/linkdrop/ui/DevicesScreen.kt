package com.mydev.linkdrop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mydev.linkdrop.core.model.Endpoint
import com.mydev.linkdrop.core.model.Device

@Composable
fun DevicesScreen(devices: List<Device>) {
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
    }
}