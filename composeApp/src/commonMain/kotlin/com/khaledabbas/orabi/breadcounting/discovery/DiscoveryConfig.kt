package com.khaledabbas.orabi.breadcounting.discovery

object DiscoveryConfig {
    /**
     * Base URL of the Cloudflare Worker that returns the current tunnel URL.
     * The app will call $CLOUD_BASE_URL/current to get the tunnel info.
     */
    const val CLOUD_BASE_URL = "https://tunnel-publish.mh-khaled-abas.workers.dev"

    /** Port the board's HTTP server listens on for local network discovery. */
    const val BOARD_PORT = 8000

    /** Timeout for cloud and cached tunnel verification requests (ms). */
    const val CLOUD_TIMEOUT_MS = 5_000L

    /** Connect timeout for cloud requests (ms). */
    const val CLOUD_CONNECT_TIMEOUT_MS = 3_000L

    /** Timeout for each local network scan probe (ms). */
    const val LOCAL_SCAN_TIMEOUT_MS = 2_000L

    /** Connect timeout for local network scan probes (ms). */
    const val LOCAL_SCAN_CONNECT_TIMEOUT_MS = 1_000L
}
