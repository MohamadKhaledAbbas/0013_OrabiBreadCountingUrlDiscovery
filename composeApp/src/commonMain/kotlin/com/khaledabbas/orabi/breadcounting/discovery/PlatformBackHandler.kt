package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.runtime.Composable

/** Platform-specific system back-button handler. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

