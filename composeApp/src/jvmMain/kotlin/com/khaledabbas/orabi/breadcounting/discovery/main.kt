package com.khaledabbas.orabi.breadcounting.discovery

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "OrabiBreadCountingUrlDiscovery",
    ) {
        App()
    }
}