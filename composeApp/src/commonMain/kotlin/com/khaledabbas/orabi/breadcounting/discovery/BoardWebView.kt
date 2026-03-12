package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform-specific composable that renders a full WebView pointing at [url]. */
@Composable
expect fun BoardWebView(url: String, modifier: Modifier = Modifier)
