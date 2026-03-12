@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.khaledabbas.orabi.breadcounting.discovery

import java.util.prefs.Preferences

actual class TunnelCache actual constructor() {

    private val prefs: Preferences =
        Preferences.userNodeForPackage(TunnelCache::class.java)

    actual fun getCachedUrl(): String? = prefs.get(KEY_URL, null)

    actual fun saveCachedUrl(url: String) {
        prefs.put(KEY_URL, url)
        prefs.flush()
    }

    actual fun clear() {
        prefs.remove(KEY_URL)
        prefs.flush()
    }

    private companion object {
        const val KEY_URL = "tunnel_url"
    }
}
