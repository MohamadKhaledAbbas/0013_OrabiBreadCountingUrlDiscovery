@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.khaledabbas.orabi.breadcounting.discovery

import android.content.Context
import android.content.SharedPreferences

actual class TunnelCache actual constructor() {

    companion object {
        private const val PREFS_NAME = "tunnel_cache"
        private const val KEY_URL = "tunnel_url"

        /** Must be set from [MainActivity.onCreate] before the cache is used. */
        lateinit var appContext: Context
    }

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun getCachedUrl(): String? = prefs.getString(KEY_URL, null)

    actual fun saveCachedUrl(url: String) {
        prefs.edit().putString(KEY_URL, url).apply()
    }

    actual fun clear() {
        prefs.edit().remove(KEY_URL).apply()
    }
}
