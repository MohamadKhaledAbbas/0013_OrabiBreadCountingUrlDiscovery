package com.khaledabbas.orabi.breadcounting.discovery

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TunnelResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesValidJson() {
        val raw = """
            {
                "tunnelUrl": "https://example.trycloudflare.com",
                "updatedAt": "2026-03-12T12:34:56.000Z"
            }
        """.trimIndent()
        val response = json.decodeFromString<TunnelResponse>(raw)
        assertEquals("https://example.trycloudflare.com", response.tunnelUrl)
        assertEquals("2026-03-12T12:34:56.000Z", response.updatedAt)
    }

    @Test
    fun parsesJsonWithExtraFields() {
        val raw = """
            {
                "tunnelUrl": "https://example.trycloudflare.com",
                "updatedAt": "2026-03-12T12:34:56.000Z",
                "extra": "ignored"
            }
        """.trimIndent()
        val response = json.decodeFromString<TunnelResponse>(raw)
        assertEquals("https://example.trycloudflare.com", response.tunnelUrl)
    }
}

class DiscoveryStateTest {

    @Test
    fun idleIsInitialState() {
        val state: DiscoveryState = DiscoveryState.Idle
        assert(state is DiscoveryState.Idle)
    }

    @Test
    fun discoveringCarriesStepInfo() {
        val state = DiscoveryState.Discovering(
            stepLabel = ArabicLabels.STEP_CACHED,
            stepDetail = "جارٍ محاولة الاتصال بالعنوان المحفوظ سابقاً…",
            completedSteps = listOf(
                StepResult(ArabicLabels.STEP_LOCAL, false, "لم يتم العثور على لوحة العدّ في الشبكة المحلية")
            ),
        )
        assertEquals(ArabicLabels.STEP_CACHED, state.stepLabel)
        assertEquals(1, state.completedSteps.size)
        assertNull(state.localScanProgress)
    }

    @Test
    fun discoveringCarriesLocalScanProgress() {
        val state = DiscoveryState.Discovering(
            stepLabel = ArabicLabels.STEP_LOCAL,
            stepDetail = "جارٍ فحص عناوين الشبكة المحلية…",
            localScanProgress = 42,
        )
        assertNotNull(state.localScanProgress)
        assertEquals(42, state.localScanProgress)
    }

    @Test
    fun connectedHoldsBoardUrl() {
        val state = DiscoveryState.Connected("http://192.168.1.100", ConnectionSource.LOCAL)
        assertEquals("http://192.168.1.100", state.boardUrl)
        assertEquals(ConnectionSource.LOCAL, state.source)
    }

    @Test
    fun failedContainsAllStepResults() {
        val steps = listOf(
            StepResult(ArabicLabels.STEP_LOCAL, false, "لم يتم العثور على لوحة العدّ في الشبكة المحلية"),
            StepResult(ArabicLabels.STEP_CACHED, false, "لا يوجد عنوان محفوظ مسبقاً"),
            StepResult(ArabicLabels.STEP_CLOUD, false, "تعذّر الوصول إلى الخادم السحابي"),
        )
        val state = DiscoveryState.Failed(steps)
        assertEquals(3, state.completedSteps.size)
    }
}

class DiscoveryConfigTest {

    @Test
    fun defaultTimeoutsArePositive() {
        assert(DiscoveryConfig.CLOUD_TIMEOUT_MS > 0)
        assert(DiscoveryConfig.CLOUD_CONNECT_TIMEOUT_MS > 0)
        assert(DiscoveryConfig.LOCAL_SCAN_TIMEOUT_MS > 0)
        assert(DiscoveryConfig.LOCAL_SCAN_CONNECT_TIMEOUT_MS > 0)
    }

    @Test
    fun cloudTimeoutIsAtLeastLocalScanTimeout() {
        assert(DiscoveryConfig.CLOUD_TIMEOUT_MS >= DiscoveryConfig.LOCAL_SCAN_TIMEOUT_MS)
    }

    @Test
    fun boardPortIsReasonable() {
        assert(DiscoveryConfig.BOARD_PORT in 1..65535)
    }
}
