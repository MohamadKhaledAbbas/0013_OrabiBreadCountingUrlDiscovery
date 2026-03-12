package com.khaledabbas.orabi.breadcounting.discovery

/**
 * Returns the device's local (private) IPv4 address, e.g. "192.168.1.42",
 * or null if no suitable interface was found.
 */
expect fun getLocalIpAddress(): String?
