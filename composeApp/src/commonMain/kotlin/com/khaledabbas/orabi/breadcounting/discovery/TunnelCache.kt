package com.khaledabbas.orabi.breadcounting.discovery

/** Platform-specific persistent cache for the last-known tunnel URL. */
expect class TunnelCache() {
    fun getCachedUrl(): String?
    fun saveCachedUrl(url: String)
    fun clear()
}
