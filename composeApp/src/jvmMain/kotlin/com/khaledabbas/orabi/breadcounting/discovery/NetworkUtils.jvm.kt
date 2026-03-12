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
                    if (isPrivateIp(ip)) return ip
                }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

/** Returns true if [ip] belongs to a private (RFC 1918) address range. */
private fun isPrivateIp(ip: String): Boolean {
    if (ip.startsWith("192.168.") || ip.startsWith("10.")) return true
    if (ip.startsWith("172.")) {
        val secondOctet = ip.split(".").getOrNull(1)?.toIntOrNull() ?: return false
        return secondOctet in 16..31
    }
    return false
}
