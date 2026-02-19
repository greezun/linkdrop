package com.mydev.linkdrop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.mydev.linkdrop.discovery.AndroidDiscovery
import com.mydev.linkdrop.permissions.rememberNearbyWifiPermissionState
import com.mydev.linkdrop.transfer.AndroidLanTransferApi
import com.mydev.linkdrop.transfer.AndroidUrlReceiver
import com.mydev.linkdrop.transfer.ShareUrlRequest
import com.mydev.linkdrop.ui.DevicesScreen
import kotlinx.coroutines.launch

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
                val transferApi = remember { AndroidLanTransferApi() }
                val receiver = remember { AndroidUrlReceiver() }
                val deviceId = remember { AndroidDeviceIdStore(context).getOrCreate() }
                val senderName = remember { Build.MODEL ?: "Android" }
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

                DevicesScreen(
                    devices = devices,
                    receivedUrls = receivedUrls,
                    onSendUrl = { device, url, onResult ->
                        scope.launch {
                            runCatching {
                                transferApi.sendUrl(
                                    target = device,
                                    payload = ShareUrlRequest(
                                        url = url,
                                        fromDeviceId = deviceId,
                                        fromName = senderName,
                                        sentAtEpochMs = System.currentTimeMillis(),
                                    )
                                )
                            }.onSuccess {
                                onResult("Sent")
                            }.onFailure { err ->
                                onResult("Send failed: ${err.message ?: "unknown error"}")
                            }
                        }
                    },
                    onOpenUrl = { url, onResult ->
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }.onSuccess {
                            onResult("Opened")
                        }.onFailure { err ->
                            onResult("Open failed: ${err.message ?: "unknown error"}")
                        }
                    },
                )
            }

        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
