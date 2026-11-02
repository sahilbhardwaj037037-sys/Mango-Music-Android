package com.mangomusic.ui.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Debug-only entry point for interactive v2.0 UI prototypes.
 * Isolated from production MainActivity and navigation.
 */
class PrototypeActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrototypeGallery()
        }
    }
}
