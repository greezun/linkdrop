package com.mydev.linkdrop.discovery

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

fun findWifiIpv4Address(): InetAddress? {
    return NetworkInterface.getNetworkInterfaces()
        .toList()
        .firstOrNull { iface ->
            iface.isUp &&
            !iface.isLoopback &&
            iface.name.startsWith("en") // en0 = Wi-Fi на macOS
        }
        ?.inetAddresses
        ?.toList()
        ?.firstOrNull { addr ->
            addr is Inet4Address && !addr.isLoopbackAddress
        }
}