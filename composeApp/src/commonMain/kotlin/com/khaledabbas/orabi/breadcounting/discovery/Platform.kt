package com.khaledabbas.orabi.breadcounting.discovery

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** `true` on platforms where the board URL should be opened in an external browser (e.g. Android). */
expect val useExternalBrowser: Boolean

/** Open [url] in the platform's default external browser. */
expect fun openInExternalBrowser(url: String)
