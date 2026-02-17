package com.mydev.linkdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.mydev.linkdrop.discovery.AndroidDiscovery
import com.mydev.linkdrop.permissions.rememberNearbyWifiPermissionState
import com.mydev.linkdrop.ui.DevicesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val hasNearby by rememberNearbyWifiPermissionState()

            if (!hasNearby) {
                Text("Allow Nearby Wi-Fi devices to discover peers")
            } else {
                val context = LocalContext.current
                val discovery = remember { AndroidDiscovery(context) }

                DisposableEffect(Unit) {
                    discovery.start()
                    onDispose { discovery.stop() }
                }

                val devices by discovery.devices.collectAsState()

                DevicesScreen(devices)
            }

        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}