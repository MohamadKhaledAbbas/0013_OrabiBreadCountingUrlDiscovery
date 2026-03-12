package com.khaledabbas.orabi.breadcounting.discovery

import java.net.Inet4Address
import java.net.NetworkInterface

actual fun getLocalIpAddress(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (iface in interfaces) {
            if (iface.isLoopback || !iface.isUp) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    // Accept only private-range addresses
                    if (ip.startsWith("192.168.") ||
                        ip.startsWith("10.") ||
                        ip.startsWith("172.")
                    ) {
                        return ip
                    }
                }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}
