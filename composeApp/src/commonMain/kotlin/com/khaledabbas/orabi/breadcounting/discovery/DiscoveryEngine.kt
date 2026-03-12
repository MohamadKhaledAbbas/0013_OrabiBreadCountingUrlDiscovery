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
import kotlin.time.TimeSource

/**
 * Core discovery engine that probes the board via local, cached, and cloud strategies.
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
        val requestUrl = "${cloudBaseUrl.trimEnd('/')}/current"
        val startedAt = TimeSource.Monotonic.markNow()

        return try {
            println("[DiscoveryEngine] cloud_fetch_start | url=$requestUrl")
            val response: HttpResponse = cloudClient.get(requestUrl)
            val elapsedMs = startedAt.elapsedNow().inWholeMilliseconds

            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                println("[DiscoveryEngine] cloud_fetch_response | status=${response.status.value}, elapsedMs=$elapsedMs, bodyLength=${body.length}")

                val tunnel = json.decodeFromString<TunnelResponse>(body)
                val result = tunnel.tunnelUrl.takeIf { it.isNotBlank() }
                if (result == null) {
                    println("[DiscoveryEngine] cloud_fetch_parse | empty_tunnel_url=true")
                }
                result
            } else {
                println("[DiscoveryEngine] cloud_fetch_non_200 | status=${response.status.value}, elapsedMs=$elapsedMs")
                null
            }
        } catch (t: Throwable) {
            val elapsedMs = startedAt.elapsedNow().inWholeMilliseconds
            println("[DiscoveryEngine] cloud_fetch_exception | elapsedMs=$elapsedMs, type=${t::class.simpleName ?: "Unknown"}, message=${t.message ?: "none"}")
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
        } catch (t: Throwable) {
            println("[DiscoveryEngine] verify_board_exception | url=$url, type=${t::class.simpleName ?: "Unknown"}, message=${t.message ?: "none"}")
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
