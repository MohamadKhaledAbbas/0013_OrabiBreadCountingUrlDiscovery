package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun App() {
    MaterialTheme {
        var discoveryState by remember { mutableStateOf<DiscoveryState>(DiscoveryState.Idle) }
        val scope = rememberCoroutineScope()
        val engine = remember { DiscoveryEngine() }
        val cache = remember { TunnelCache() }

        /** Runs the full three-step discovery flow. */
        fun runDiscovery() {
            discoveryState = DiscoveryState.Idle
            scope.launch(Dispatchers.Default) {
                val completed = mutableListOf<StepResult>()

                // ── Step 1: Cached tunnel ────────────────────────
                val cachedUrl = cache.getCachedUrl()
                if (cachedUrl != null) {
                    discoveryState = DiscoveryState.Discovering(
                        stepLabel = "Cached Connection",
                        stepDetail = "Trying previously known tunnel…",
                        completedSteps = completed.toList(),
                    )
                    val ok = engine.verifyBoard(cachedUrl)
                    if (ok) {
                        discoveryState = DiscoveryState.Connected(cachedUrl)
                        return@launch
                    }
                    completed += StepResult("Cached Connection", false, "Cached tunnel unreachable")
                }

                // ── Step 2: Cloud discovery ──────────────────────
                discoveryState = DiscoveryState.Discovering(
                    stepLabel = "Cloud Discovery",
                    stepDetail = "Fetching tunnel URL from cloud…",
                    completedSteps = completed.toList(),
                )
                val tunnelUrl = engine.fetchTunnelUrl(DiscoveryConfig.CLOUD_BASE_URL)
                if (tunnelUrl != null) {
                    discoveryState = DiscoveryState.Discovering(
                        stepLabel = "Cloud Discovery",
                        stepDetail = "Verifying cloud tunnel…",
                        completedSteps = completed.toList(),
                    )
                    val ok = engine.verifyBoard(tunnelUrl)
                    if (ok) {
                        cache.saveCachedUrl(tunnelUrl)
                        discoveryState = DiscoveryState.Connected(tunnelUrl)
                        return@launch
                    }
                    completed += StepResult("Cloud Discovery", false, "Tunnel found but board unreachable")
                } else {
                    completed += StepResult("Cloud Discovery", false, "Could not reach cloud service")
                }

                // ── Step 3: Local network scan ───────────────────
                discoveryState = DiscoveryState.Discovering(
                    stepLabel = "Local Network Scan",
                    stepDetail = "Scanning local network for the board…",
                    completedSteps = completed.toList(),
                    localScanProgress = 0,
                )

                val localUrl = engine.scanLocalNetwork(
                    port = DiscoveryConfig.BOARD_PORT,
                    onProgress = { scanned, _ ->
                        discoveryState = DiscoveryState.Discovering(
                            stepLabel = "Local Network Scan",
                            stepDetail = "Scanning local network for the board…",
                            completedSteps = completed.toList(),
                            localScanProgress = scanned,
                        )
                    }
                )
                if (localUrl != null) {
                    cache.saveCachedUrl(localUrl)
                    discoveryState = DiscoveryState.Connected(localUrl)
                    return@launch
                }
                completed += StepResult(
                    "Local Network Scan",
                    false,
                    "No board found on the local network"
                )

                // ── All failed ───────────────────────────────────
                discoveryState = DiscoveryState.Failed(completed)
            }
        }

        // Kick off discovery on first composition
        LaunchedEffect(Unit) {
            runDiscovery()
        }

        // Dispose engine when leaving composition
        DisposableEffect(Unit) {
            onDispose { engine.close() }
        }

        // ── Render ───────────────────────────────────────────
        when (val s = discoveryState) {
            is DiscoveryState.Connected -> {
                BoardWebView(
                    url = s.boardUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                DiscoveryScreen(
                    state = discoveryState,
                    onRetry = { runDiscovery() },
                )
            }
        }
    }
}