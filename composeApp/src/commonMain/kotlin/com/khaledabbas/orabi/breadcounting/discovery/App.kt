package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

// ── Orabi brand M3 colour scheme – Ocean Blue ────────────────
private val OrabiBlue = Color(0xFF1976D2)
private val OrabiDarkBlue = Color(0xFF0D253F)
private val OrabiLightBg = Color(0xFFF0F4FA)
private val OrabiLightBlue = Color(0xFFBBDEFB)
private val OrabiErrorRed = Color(0xFFC62828)

private val OrabiColorScheme = lightColorScheme(
    primary = OrabiBlue,
    onPrimary = Color.White,
    primaryContainer = OrabiLightBlue,
    onPrimaryContainer = OrabiDarkBlue,
    secondary = OrabiDarkBlue,
    onSecondary = Color.White,
    secondaryContainer = OrabiLightBg,
    onSecondaryContainer = OrabiDarkBlue,
    background = OrabiLightBg,
    onBackground = OrabiDarkBlue,
    surface = OrabiLightBg,
    onSurface = OrabiDarkBlue,
    surfaceVariant = Color.White,
    onSurfaceVariant = OrabiDarkBlue.copy(alpha = 0.7f),
    error = OrabiErrorRed,
    onError = Color.White,
    outline = OrabiDarkBlue.copy(alpha = 0.2f),
)

// ── Helper: detect local-network URLs ────────────────────────
/**
 * Returns `true` when [url] points at a private/local IP address
 * (RFC 1918 ranges 10.x, 172.16–31.x, 192.168.x, or link-local 169.254.x).
 * Used to decide whether a cached URL should be re-tried after a full
 * local subnet scan has already been performed.
 */
private fun isLocalNetworkUrl(url: String): Boolean {
    // Strip scheme ("http://", "https://") and everything after the host
    val host = url
        .substringAfter("://")
        .substringBefore("/")
        .substringBefore(":")
        .trim()
    val parts = host.split(".")
    if (parts.size != 4) return false          // not an IPv4 literal
    val octets = parts.mapNotNull { it.toIntOrNull() }
    if (octets.size != 4) return false
    val (a, b, _, _) = octets
    return when {
        a == 10                          -> true   // 10.0.0.0/8
        a == 172 && b in 16..31          -> true   // 172.16.0.0/12
        a == 192 && b == 168             -> true   // 192.168.0.0/16
        a == 169 && b == 254             -> true   // 169.254.0.0/16  (link-local)
        else                             -> false
    }
}

// Arabic step labels (shared between App flow and tests)
internal object ArabicLabels {
    const val STEP_CACHED = "الاتصال المحفوظ"
    const val STEP_CLOUD = "الاتصال السحابي"
    const val STEP_LOCAL = "البحث في الشبكة المحلية"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme(colorScheme = OrabiColorScheme) {
        var discoveryState by remember { mutableStateOf<DiscoveryState>(DiscoveryState.Idle) }
        var showWebView by remember { mutableStateOf(false) }
        var runCounter by remember { mutableStateOf(0) }
        var activeRunId by remember { mutableStateOf(0) }
        var activeDiscoveryJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val engine = remember { DiscoveryEngine() }
        val cache = remember { TunnelCache() }

        fun trace(runId: Int, event: String, detail: String = "") {
            val suffix = if (detail.isBlank()) "" else " | $detail"
            println("[DiscoveryTrace][run=$runId] $event$suffix")
        }

        /**
         * Runs the full three-step discovery flow: Local → Cached → Cloud.
         *
         * Priority rationale:
         *  1. **Local scan** – fastest, lowest latency if the board is on the same LAN.
         *  2. **Cached URL** – only tried when it is a *remote/tunnel* URL.
         *     If the cached URL is a local IP we skip it because the local scan
         *     already exhausted the entire /24 subnet.
         *  3. **Cloud** – last resort; fetches a fresh tunnel URL from the worker.
         */
        fun runDiscovery(trigger: String) {
            showWebView = false
            activeDiscoveryJob?.cancel(CancellationException("Superseded by new discovery run"))

            runCounter += 1
            val runId = runCounter
            activeRunId = runId

            trace(runId, "run_start", "trigger=$trigger")
            discoveryState = DiscoveryState.Idle
            trace(runId, "state_update", "Idle")

            activeDiscoveryJob = scope.launch(Dispatchers.Default) {
                val runStartedAt = TimeSource.Monotonic.markNow()
                val completed = mutableListOf<StepResult>()

                fun runTrace(event: String, detail: String = "") {
                    val elapsedMs = runStartedAt.elapsedNow().inWholeMilliseconds
                    val elapsedDetail = if (detail.isBlank()) "elapsedMs=$elapsedMs" else "elapsedMs=$elapsedMs, $detail"
                    trace(runId, event, elapsedDetail)
                }

                fun isRunActive(): Boolean = activeRunId == runId

                try {
                    // ── Step 1: Local network scan ───────────────────
                    runTrace("step_start", "step=local_scan")
                    discoveryState = DiscoveryState.Discovering(
                        stepLabel = ArabicLabels.STEP_LOCAL,
                        stepDetail = "جارٍ فحص عناوين الشبكة المحلية…",
                        completedSteps = completed.toList(),
                        localScanProgress = 0,
                    )
                    runTrace("state_update", "Discovering step=local_scan")

                    val localUrl = engine.scanLocalNetwork(
                        port = DiscoveryConfig.BOARD_PORT,
                        onProgress = { scanned, total ->
                            if (!isRunActive()) return@scanLocalNetwork

                            if (scanned == 1 || scanned % 25 == 0 || scanned == total) {
                                runTrace("local_scan_progress", "scanned=$scanned, total=$total")
                            }

                            discoveryState = DiscoveryState.Discovering(
                                stepLabel = ArabicLabels.STEP_LOCAL,
                                stepDetail = "جارٍ فحص عناوين الشبكة المحلية…",
                                completedSteps = completed.toList(),
                                localScanProgress = scanned,
                            )
                        }
                    )

                    runTrace("local_scan_result", if (localUrl == null) "result=not_found" else "result=found, url=$localUrl")

                    if (localUrl != null) {
                        cache.saveCachedUrl(localUrl)
                        runTrace("cache_save", "source=local_scan, url=$localUrl")
                        completed += StepResult(ArabicLabels.STEP_LOCAL, true, "تم العثور على لوحة العدّ في الشبكة المحلية")
                        discoveryState = DiscoveryState.Connected(localUrl, ConnectionSource.LOCAL)
                        runTrace("state_update", "Connected via=local_scan, url=$localUrl")
                        return@launch
                    }
                    completed += StepResult(
                        ArabicLabels.STEP_LOCAL,
                        false,
                        "لم يتم العثور على لوحة العدّ في الشبكة المحلية"
                    )

                    if (!isRunActive()) {
                        runTrace("run_stale", "after_step=local_scan")
                        return@launch
                    }

                    // ── Step 2: Cached tunnel ────────────────────────
                    // Only attempt the cached URL when it is a remote/tunnel
                    // address. If it is a local IP, the local scan above
                    // already covered the entire subnet — retrying is pointless.
                    runTrace("step_start", "step=cached")
                    val cachedUrl = cache.getCachedUrl()
                    val cachedIsLocalIp = cachedUrl != null && isLocalNetworkUrl(cachedUrl)
                    runTrace(
                        "cache_lookup",
                        when {
                            cachedUrl == null -> "result=miss"
                            cachedIsLocalIp  -> "result=hit_local_skipped, url=$cachedUrl"
                            else             -> "result=hit_remote, url=$cachedUrl"
                        }
                    )

                    if (cachedUrl != null && !cachedIsLocalIp) {
                        discoveryState = DiscoveryState.Discovering(
                            stepLabel = ArabicLabels.STEP_CACHED,
                            stepDetail = "جارٍ محاولة الاتصال بالعنوان المحفوظ سابقاً…",
                            completedSteps = completed.toList(),
                        )
                        runTrace("state_update", "Discovering step=cached")

                        val ok = engine.verifyBoard(cachedUrl)
                        runTrace("verify_cached", "url=$cachedUrl, success=$ok")
                        if (ok) {
                            completed += StepResult(ArabicLabels.STEP_CACHED, true, "تم الاتصال بالعنوان المحفوظ بنجاح")
                            discoveryState = DiscoveryState.Connected(cachedUrl, ConnectionSource.CACHED)
                            runTrace("state_update", "Connected via=cached, url=$cachedUrl")
                            return@launch
                        }
                        completed += StepResult(ArabicLabels.STEP_CACHED, false, "العنوان المحفوظ غير متاح حالياً")
                    } else if (cachedIsLocalIp) {
                        completed += StepResult(ArabicLabels.STEP_CACHED, false, "العنوان المحفوظ محلي — تم فحصه بالفعل")
                    } else {
                        completed += StepResult(ArabicLabels.STEP_CACHED, false, "لا يوجد عنوان محفوظ مسبقاً")
                    }

                    if (!isRunActive()) {
                        runTrace("run_stale", "after_step=cached")
                        return@launch
                    }

                    // ── Step 3: Cloud discovery ──────────────────────
                    runTrace("step_start", "step=cloud")
                    discoveryState = DiscoveryState.Discovering(
                        stepLabel = ArabicLabels.STEP_CLOUD,
                        stepDetail = "جارٍ جلب عنوان النفق من الخادم السحابي…",
                        completedSteps = completed.toList(),
                    )
                    runTrace("state_update", "Discovering step=cloud fetch")

                    val tunnelUrl = engine.fetchTunnelUrl(DiscoveryConfig.CLOUD_BASE_URL)
                    runTrace("cloud_fetch", if (tunnelUrl == null) "result=failed" else "result=success, url=$tunnelUrl")

                    if (tunnelUrl != null) {
                        discoveryState = DiscoveryState.Discovering(
                            stepLabel = ArabicLabels.STEP_CLOUD,
                            stepDetail = "جارٍ التحقق من اتصال النفق السحابي…",
                            completedSteps = completed.toList(),
                        )
                        runTrace("state_update", "Discovering step=cloud verify")

                        val ok = engine.verifyBoard(tunnelUrl)
                        runTrace("verify_cloud", "url=$tunnelUrl, success=$ok")
                        if (ok) {
                            cache.saveCachedUrl(tunnelUrl)
                            runTrace("cache_save", "source=cloud, url=$tunnelUrl")
                            completed += StepResult(ArabicLabels.STEP_CLOUD, true, "تم الاتصال عبر النفق السحابي بنجاح")
                            discoveryState = DiscoveryState.Connected(tunnelUrl, ConnectionSource.CLOUD)
                            runTrace("state_update", "Connected via=cloud, url=$tunnelUrl")
                            return@launch
                        }
                        completed += StepResult(ArabicLabels.STEP_CLOUD, false, "تم العثور على النفق لكن لوحة العدّ لا تستجيب")
                    } else {
                        completed += StepResult(ArabicLabels.STEP_CLOUD, false, "تعذّر الوصول إلى الخادم السحابي")
                    }


                    // ── All failed ───────────────────────────────────
                    discoveryState = DiscoveryState.Failed(completed)
                    runTrace("state_update", "Failed completedSteps=${completed.size}")
                } catch (cancellation: CancellationException) {
                    runTrace("run_cancelled", "reason=${cancellation.message ?: "unknown"}")
                } catch (t: Throwable) {
                    runTrace(
                        "run_error",
                        "type=${t::class.simpleName ?: "Unknown"}, message=${t.message ?: "none"}"
                    )
                    discoveryState = DiscoveryState.Failed(
                        completed + StepResult("خطأ غير متوقع", false, t.message ?: "حدث خطأ غير متوقع")
                    )
                    runTrace("state_update", "Failed due_to=unexpected_error")
                } finally {
                    runTrace("run_finish")
                    if (activeRunId == runId) {
                        activeDiscoveryJob = null
                    }
                }
            }
        }

        // Auto-show WebView / open browser as soon as discovery connects
        LaunchedEffect(discoveryState) {
            if (discoveryState is DiscoveryState.Connected) {
                if (useExternalBrowser) {
                    openInExternalBrowser((discoveryState as DiscoveryState.Connected).boardUrl)
                } else {
                    showWebView = true
                }
            }
        }

        // Kick off discovery on first composition
        LaunchedEffect(Unit) {
            runDiscovery(trigger = "first_composition")
        }

        // Dispose engine when leaving composition
        DisposableEffect(Unit) {
            onDispose {
                activeDiscoveryJob?.cancel(CancellationException("App disposed"))
                trace(activeRunId, "dispose", "closing_discovery_engine=true")
                engine.close()
            }
        }

        // ── Render ───────────────────────────────────────────
        val currentState = discoveryState
        if (!useExternalBrowser && showWebView && currentState is DiscoveryState.Connected) {
            // Intercept system back → return to discovery screen
            PlatformBackHandler {
                trace(activeRunId, "back_from_webview", "url=${currentState.boardUrl}")
                showWebView = false
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "لوحة العدّ",
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { showWebView = false }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    },
                ) { padding ->
                    BoardWebView(
                        url = currentState.boardUrl,
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                    )
                }
            }
        } else {
            DiscoveryScreen(
                state = discoveryState,
                onRetry = { runDiscovery(trigger = "user_retry") },
                onOpenBoard = {
                    val connState = discoveryState as? DiscoveryState.Connected
                    if (connState != null && useExternalBrowser) {
                        openInExternalBrowser(connState.boardUrl)
                    } else {
                        showWebView = true
                    }
                },
            )
        }
    }
}