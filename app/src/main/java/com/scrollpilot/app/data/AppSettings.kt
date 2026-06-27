package com.scrollpilot.app.data

// GlobalSettings, ScrollEngine, and OverlaySize are now in GlobalSettings.kt

data class PerAppSettings(
    val packageName: String,
    val overrideGlobal: Boolean = false,
)
