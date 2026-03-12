package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI
import javax.swing.JEditorPane
import javax.swing.JScrollPane

@Composable
actual fun BoardWebView(url: String, modifier: Modifier) {
    // On JVM Desktop we embed a basic Swing HTML pane.
    // If the system supports a full browser, we also open it externally
    // so the user gets a proper WebView experience.
    LaunchedEffect(url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (_: Exception) {
            // Ignore – the embedded pane is the fallback
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Board UI – $url",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(8.dp),
        )

        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val editorPane = JEditorPane().apply {
                    isEditable = false
                    contentType = "text/html"
                    text = """
                        <html><body style="font-family:sans-serif;text-align:center;padding:48px;">
                        <h2>Board UI opened in your browser</h2>
                        <p>URL: <a href="$url">$url</a></p>
                        <p style="color:#666;">If the browser did not open, copy the URL above.</p>
                        </body></html>
                    """.trimIndent()
                }
                JScrollPane(editorPane)
            },
        )
    }
}
