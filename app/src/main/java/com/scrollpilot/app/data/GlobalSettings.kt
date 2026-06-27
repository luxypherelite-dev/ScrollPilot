package com.scrollpilot.app.data

enum class ScrollEngine { SWIPE, FLING, HYBRID, PAGE_JUMP }

enum class OverlaySize(val scale: Float, val label: String) {
    COMPACT(0.75f, "Compact"),
    NORMAL(1.00f,  "Normal"),
    LARGE(1.25f,   "Large"),
    CUSTOM(0.00f,  "Custom"),
}

data class GlobalSettings(
    val enabledApps: Set<String>        = emptySet(),
    val overlayX: Int                   = -1,
    val overlayY: Int                   = -1,
    val rememberLastPosition: Boolean   = true,
    val hideOnKeyboard: Boolean         = true,
    val overlaySize: OverlaySize        = OverlaySize.COMPACT,
    val customScale: Float              = 0.75f,
    val scrollEngine: ScrollEngine      = ScrollEngine.HYBRID,
    val flingWaitMs: Long               = 900L,
    val swipeToFlingThreshold: Float    = 8000f,
    val defaultSpeed: Float             = 3000f,
) {
    val effectiveScale: Float get() = if (overlaySize == OverlaySize.CUSTOM) customScale else overlaySize.scale
}
