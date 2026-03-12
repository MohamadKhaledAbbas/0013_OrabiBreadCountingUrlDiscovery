package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop does not have a system back button; handled via the UI back arrow.
}

