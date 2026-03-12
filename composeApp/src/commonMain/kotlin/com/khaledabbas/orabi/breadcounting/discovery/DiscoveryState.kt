package com.khaledabbas.orabi.breadcounting.discovery

/** Result of a single discovery step. */
data class StepResult(
    val label: String,
    val success: Boolean,
    val detail: String = ""
)

/** Describes which discovery strategy found the board. */
enum class ConnectionSource {
    /** Found via local /24 subnet scan. */
    LOCAL,
    /** Found via a previously-cached (remote/tunnel) URL. */
    CACHED,
    /** Found via a fresh Cloudflare Worker tunnel lookup. */
    CLOUD,
}

/** Overall discovery UI state. */
sealed class DiscoveryState {

    /** Initial state before discovery starts. */
    data object Idle : DiscoveryState()

    /** Discovery is actively running. */
    data class Discovering(
        val stepLabel: String,
        val stepDetail: String = "",
        val completedSteps: List<StepResult> = emptyList(),
        /** Progress of local scan: 0..254, or null if not scanning locally. */
        val localScanProgress: Int? = null
    ) : DiscoveryState()

    /** A board was found – show the WebView. */
    data class Connected(
        val boardUrl: String,
        val source: ConnectionSource,
    ) : DiscoveryState()

    /** All discovery attempts failed. */
    data class Failed(val completedSteps: List<StepResult>) : DiscoveryState()
}
