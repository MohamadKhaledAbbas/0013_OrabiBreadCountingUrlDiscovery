package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlin.time.TimeSource

// Arabic step labels (shared between App flow and tests)
internal object ArabicLabels {
    const val STEP_CACHED = "الاتصال المحفوظ"
    const val STEP_CLOUD = "الاتصال السحابي"
    const val STEP_LOCAL = "البحث في الشبكة المحلية"
}

@Composable
fun App() {
    MaterialTheme {
        var discoveryState by remember { mutableStateOf<DiscoveryState>(DiscoveryState.Idle) }
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
         * Runs the full three-step discovery flow: Cached → Cloud → Local.
         * Always starts from the beginning on every call (including retries).
         */
        fun runDiscovery(trigger: String) {
            activeDiscoveryJob?.cancel("Superseded by new discovery run")

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
                    // ── Step 1: Cached tunnel ────────────────────────
                    runTrace("step_start", "step=cached")
                    val cachedUrl = cache.getCachedUrl()
                    runTrace("cache_lookup", if (cachedUrl == null) "result=miss" else "result=hit, url=$cachedUrl")

                    if (cachedUrl != null) {
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
                            discoveryState = DiscoveryState.Connected(cachedUrl)
                            runTrace("state_update", "Connected via=cached, url=$cachedUrl")
                            return@launch
                        }
                        completed += StepResult(ArabicLabels.STEP_CACHED, false, "العنوان المحفوظ غير متاح حالياً")
                    } else {
                        completed += StepResult(ArabicLabels.STEP_CACHED, false, "لا يوجد عنوان محفوظ مسبقاً")
                    }

                    if (!isRunActive()) {
                        runTrace("run_stale", "after_step=cached")
                        return@launch
                    }

                    // ── Step 2: Cloud discovery ──────────────────────
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
                            discoveryState = DiscoveryState.Connected(tunnelUrl)
                            runTrace("state_update", "Connected via=cloud, url=$tunnelUrl")
                            return@launch
                        }
                        completed += StepResult(ArabicLabels.STEP_CLOUD, false, "تم العثور على النفق لكن لوحة العدّ لا تستجيب")
                    } else {
                        completed += StepResult(ArabicLabels.STEP_CLOUD, false, "تعذّر الوصول إلى الخادم السحابي")
                    }

                    if (!isRunActive()) {
                        runTrace("run_stale", "after_step=cloud")
                        return@launch
                    }

                    // ── Step 3: Local network scan ───────────────────
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
                        discoveryState = DiscoveryState.Connected(localUrl)
                        runTrace("state_update", "Connected via=local_scan, url=$localUrl")
                        return@launch
                    }
                    completed += StepResult(
                        ArabicLabels.STEP_LOCAL,
                        false,
                        "لم يتم العثور على لوحة العدّ في الشبكة المحلية"
                    )

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

        // Kick off discovery on first composition
        LaunchedEffect(Unit) {
            runDiscovery(trigger = "first_composition")
        }

        // Dispose engine when leaving composition
        DisposableEffect(Unit) {
            onDispose {
                activeDiscoveryJob?.cancel("App disposed")
                trace(activeRunId, "dispose", "closing_discovery_engine=true")
                engine.close()
            }
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
                    onRetry = { runDiscovery(trigger = "user_retry") },
                )
            }
        }
    }
}