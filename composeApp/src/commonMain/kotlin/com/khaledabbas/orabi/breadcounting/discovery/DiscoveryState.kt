package com.khaledabbas.orabi.breadcounting.discovery

/** Result of a single discovery step. */
data class StepResult(
    val label: String,
    val success: Boolean,
    val detail: String = ""
)

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
    data class Connected(val boardUrl: String) : DiscoveryState()

    /** All discovery attempts failed. */
    data class Failed(val completedSteps: List<StepResult>) : DiscoveryState()
}
