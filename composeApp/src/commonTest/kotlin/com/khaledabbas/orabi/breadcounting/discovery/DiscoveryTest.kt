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
            stepLabel = "Cloud Discovery",
            stepDetail = "Fetching tunnel URL from cloud…",
            completedSteps = listOf(
                StepResult("Cached Connection", false, "unreachable")
            ),
        )
        assertEquals("Cloud Discovery", state.stepLabel)
        assertEquals(1, state.completedSteps.size)
        assertNull(state.localScanProgress)
    }

    @Test
    fun discoveringCarriesLocalScanProgress() {
        val state = DiscoveryState.Discovering(
            stepLabel = "Local Network Scan",
            stepDetail = "Scanning…",
            localScanProgress = 42,
        )
        assertNotNull(state.localScanProgress)
        assertEquals(42, state.localScanProgress)
    }

    @Test
    fun connectedHoldsBoardUrl() {
        val state = DiscoveryState.Connected("http://192.168.1.100")
        assertEquals("http://192.168.1.100", state.boardUrl)
    }

    @Test
    fun failedContainsAllStepResults() {
        val steps = listOf(
            StepResult("Cached", false, "unreachable"),
            StepResult("Cloud", false, "timeout"),
            StepResult("Local", false, "no board found"),
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
    fun cloudTimeoutExceedsLocalScanTimeout() {
        assert(DiscoveryConfig.CLOUD_TIMEOUT_MS >= DiscoveryConfig.LOCAL_SCAN_TIMEOUT_MS)
    }

    @Test
    fun boardPortIsReasonable() {
        assert(DiscoveryConfig.BOARD_PORT in 1..65535)
    }
}
