package com.mydev.linkdrop.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

@Composable
fun rememberNearbyWifiPermissionState(): State<Boolean> {
    val granted = remember { mutableStateOf(true) }

    if (Build.VERSION.SDK_INT < 33) return granted

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted.value = isGranted
    }

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    return granted
}