package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * On Android the board URL is opened in the system browser instead of an
 * embedded WebView.  This composable only exists to satisfy the expect/actual
 * contract – it will fire the browser and show a simple placeholder.
 */
@Composable
actual fun BoardWebView(url: String, modifier: Modifier) {
    LaunchedEffect(url) {
        openInExternalBrowser(url)
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("تم فتح لوحة العدّ في المتصفح")
    }
}
