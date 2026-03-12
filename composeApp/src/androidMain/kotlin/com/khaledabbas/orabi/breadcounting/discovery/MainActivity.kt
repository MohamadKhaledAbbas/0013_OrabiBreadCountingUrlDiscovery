package com.khaledabbas.orabi.breadcounting.discovery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize cache context before any composable is rendered
        TunnelCache.appContext = applicationContext

        setContent {
            App()
        }
    }
}