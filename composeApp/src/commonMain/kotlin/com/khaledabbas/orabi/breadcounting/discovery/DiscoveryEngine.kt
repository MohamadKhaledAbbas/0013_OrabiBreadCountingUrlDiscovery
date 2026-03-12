package com.khaledabbas.orabi.breadcounting.discovery

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Core discovery engine that probes the board via cached, cloud, and local strategies.
 *
 * All public methods are suspend functions designed to be called from a coroutine scope.
 */
class DiscoveryEngine {

    private val json = Json { ignoreUnknownKeys = true }

    private val cloudClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = DiscoveryConfig.CLOUD_TIMEOUT_MS
            connectTimeoutMillis = DiscoveryConfig.CLOUD_CONNECT_TIMEOUT_MS
        }
    }

    private val scanClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = DiscoveryConfig.LOCAL_SCAN_TIMEOUT_MS
            connectTimeoutMillis = DiscoveryConfig.LOCAL_SCAN_CONNECT_TIMEOUT_MS
        }
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Fetches the tunnel URL from the Cloudflare Worker endpoint.
     * Returns the tunnel URL string or null on failure.
     */
    suspend fun fetchTunnelUrl(cloudBaseUrl: String): String? {
        return try {
            val response: HttpResponse = cloudClient.get("$cloudBaseUrl/current")
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val tunnel = json.decodeFromString<TunnelResponse>(body)
                tunnel.tunnelUrl.takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Verifies that a board is reachable at [url] by requesting /whoami.
     */
    suspend fun verifyBoard(url: String): Boolean {
        return try {
            val response = cloudClient.get("$url/whoami")
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scans the local /24 subnet for a board listening on [port].
     * Calls [onProgress] after each IP is probed so the UI can update.
     * Returns the first base URL that responds to /whoami, or null.
     */
    suspend fun scanLocalNetwork(
        port: Int = DiscoveryConfig.BOARD_PORT,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): String? {
        val localIp = getLocalIpAddress() ?: return null
        val parts = localIp.split(".")
        if (parts.size != 4) return null
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val total = 254
        var scannedCount = 0
        val mutex = Mutex()

        return coroutineScope {
            val deferred = (1..total).map { i ->
                async(Dispatchers.IO) {
                    val ip = "$prefix.$i"
                    val baseUrl = if (port == 80) "http://$ip" else "http://$ip:$port"
                    val found = probeBoard(baseUrl)
                    mutex.withLock {
                        scannedCount++
                        onProgress(scannedCount, total)
                    }
                    if (found) baseUrl else null
                }
            }
            // Return the first non-null result, then cancel remaining tasks.
            var result: String? = null
            for ((index, d) in deferred.withIndex()) {
                val r = d.await()
                if (r != null) {
                    result = r
                    // Cancel only the tasks we haven't awaited yet
                    for (j in (index + 1) until deferred.size) {
                        deferred[j].cancel()
                    }
                    break
                }
            }
            result
        }
    }

    fun close() {
        cloudClient.close()
        scanClient.close()
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private suspend fun probeBoard(baseUrl: String): Boolean {
        return try {
            val response = scanClient.get("$baseUrl/whoami")
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        }
    }
}
