package com.scrollpilot.app.data

enum class AccelerationMode {
    INSTANT, SMOOTH, ADAPTIVE, AGGRESSIVE, CUSTOM
}

enum class ScrollDirection {
    UP, DOWN, PAUSED
}

data class GlobalSettings(
    val selectedApps: Set<String> = emptySet(),
    val defaultSpeed: Float = 800f,
    val maxSpeed: Float = 5000f,
    val accelerationMode: AccelerationMode = AccelerationMode.SMOOTH,
    val customAccel: Float = 3f,
    val customDecel: Float = 3f,
    val hideWithKeyboard: Boolean = true,
    val showUpButton: Boolean = true,
    val showDownButton: Boolean = true,
    val showSpeedSlider: Boolean = true,
    val showSpeedAnimation: Boolean = true,
    val rememberLastPosition: Boolean = true,
    val keepCompact: Boolean = false,
    val infiniteAcceleration: Boolean = false,
    val overlayX: Int = -1,
    val overlayY: Int = -1,
)

data class PerAppSettings(
    val packageName: String,
    val overrideGlobal: Boolean = false,
    val defaultSpeed: Float = 800f,
    val accelerationMode: AccelerationMode = AccelerationMode.SMOOTH,
    val hideWithKeyboard: Boolean = true,
    val showUpButton: Boolean = true,
    val showDownButton: Boolean = true,
)
