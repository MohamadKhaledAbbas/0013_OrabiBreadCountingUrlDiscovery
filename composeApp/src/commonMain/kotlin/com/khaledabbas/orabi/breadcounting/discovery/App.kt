package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        val scope = rememberCoroutineScope()
        val engine = remember { DiscoveryEngine() }
        val cache = remember { TunnelCache() }

        /**
         * Runs the full three-step discovery flow: Cached → Cloud → Local.
         * Always starts from the beginning on every call (including retries).
         */
        fun runDiscovery() {
            discoveryState = DiscoveryState.Idle
            scope.launch(Dispatchers.Default) {
                val completed = mutableListOf<StepResult>()

                // ── Step 1: Cached tunnel ────────────────────────
                val cachedUrl = cache.getCachedUrl()
                if (cachedUrl != null) {
                    discoveryState = DiscoveryState.Discovering(
                        stepLabel = ArabicLabels.STEP_CACHED,
                        stepDetail = "جارٍ محاولة الاتصال بالعنوان المحفوظ سابقاً…",
                        completedSteps = completed.toList(),
                    )
                    val ok = engine.verifyBoard(cachedUrl)
                    if (ok) {
                        completed += StepResult(ArabicLabels.STEP_CACHED, true, "تم الاتصال بالعنوان المحفوظ بنجاح")
                        discoveryState = DiscoveryState.Connected(cachedUrl)
                        return@launch
                    }
                    completed += StepResult(ArabicLabels.STEP_CACHED, false, "العنوان المحفوظ غير متاح حالياً")
                } else {
                    completed += StepResult(ArabicLabels.STEP_CACHED, false, "لا يوجد عنوان محفوظ مسبقاً")
                }

                // ── Step 2: Cloud discovery ──────────────────────
                discoveryState = DiscoveryState.Discovering(
                    stepLabel = ArabicLabels.STEP_CLOUD,
                    stepDetail = "جارٍ جلب عنوان النفق من الخادم السحابي…",
                    completedSteps = completed.toList(),
                )
                val tunnelUrl = engine.fetchTunnelUrl(DiscoveryConfig.CLOUD_BASE_URL)
                if (tunnelUrl != null) {
                    discoveryState = DiscoveryState.Discovering(
                        stepLabel = ArabicLabels.STEP_CLOUD,
                        stepDetail = "جارٍ التحقق من اتصال النفق السحابي…",
                        completedSteps = completed.toList(),
                    )
                    val ok = engine.verifyBoard(tunnelUrl)
                    if (ok) {
                        cache.saveCachedUrl(tunnelUrl)
                        completed += StepResult(ArabicLabels.STEP_CLOUD, true, "تم الاتصال عبر النفق السحابي بنجاح")
                        discoveryState = DiscoveryState.Connected(tunnelUrl)
                        return@launch
                    }
                    completed += StepResult(ArabicLabels.STEP_CLOUD, false, "تم العثور على النفق لكن لوحة العدّ لا تستجيب")
                } else {
                    completed += StepResult(ArabicLabels.STEP_CLOUD, false, "تعذّر الوصول إلى الخادم السحابي")
                }

                // ── Step 3: Local network scan ───────────────────
                discoveryState = DiscoveryState.Discovering(
                    stepLabel = ArabicLabels.STEP_LOCAL,
                    stepDetail = "جارٍ فحص عناوين الشبكة المحلية…",
                    completedSteps = completed.toList(),
                    localScanProgress = 0,
                )

                val localUrl = engine.scanLocalNetwork(
                    port = DiscoveryConfig.BOARD_PORT,
                    onProgress = { scanned, _ ->
                        discoveryState = DiscoveryState.Discovering(
                            stepLabel = ArabicLabels.STEP_LOCAL,
                            stepDetail = "جارٍ فحص عناوين الشبكة المحلية…",
                            completedSteps = completed.toList(),
                            localScanProgress = scanned,
                        )
                    }
                )
                if (localUrl != null) {
                    cache.saveCachedUrl(localUrl)
                    completed += StepResult(ArabicLabels.STEP_LOCAL, true, "تم العثور على لوحة العدّ في الشبكة المحلية")
                    discoveryState = DiscoveryState.Connected(localUrl)
                    return@launch
                }
                completed += StepResult(
                    ArabicLabels.STEP_LOCAL,
                    false,
                    "لم يتم العثور على لوحة العدّ في الشبكة المحلية"
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