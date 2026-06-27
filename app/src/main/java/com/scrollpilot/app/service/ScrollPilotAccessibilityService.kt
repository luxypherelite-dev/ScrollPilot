package com.scrollpilot.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.scrollpilot.app.data.AccelerationMode
import com.scrollpilot.app.data.ScrollDirection
import com.scrollpilot.app.data.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ScrollPilotAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ScrollPilotAccessibilityService? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlayManager: OverlayManager? = null
    private var scrollJob: Job? = null
    private var currentForegroundPkg = ""
    private var selectedApps: Set<String> = emptySet()
    private var keyboardVisible = false
    private var hideWithKeyboard = true

    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = OverlayManager(this)

        serviceScope.launch {
            SettingsDataStore.globalSettings(applicationContext).collect { settings ->
                selectedApps     = settings.selectedApps
                hideWithKeyboard = settings.hideWithKeyboard
                ScrollController.applySettings(
                    targetSpeed = settings.defaultSpeed,
                    maxSpeed    = settings.maxSpeed,
                    mode        = settings.accelerationMode,
                    customAccel = settings.customAccel,
                    customDecel = settings.customDecel,
                    infinite    = settings.infiniteAcceleration,
                )
                overlayManager?.updateSettings(settings)
                updateOverlayVisibility()
            }
        }

        startScrollLoop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg != currentForegroundPkg && pkg != packageName) {
                    currentForegroundPkg = pkg
                    ScrollController.pause()
                    updateOverlayVisibility()
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                detectKeyboard()
            }
        }
    }

    private fun detectKeyboard() {
        val hasKeyboard = windows?.any { w ->
            w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        } ?: false

        if (hasKeyboard != keyboardVisible) {
            keyboardVisible = hasKeyboard
            updateOverlayVisibility()
        }
    }

    private fun updateOverlayVisibility() {
        val isSelectedApp = currentForegroundPkg in selectedApps
        val shouldHide = keyboardVisible && hideWithKeyboard
        val shouldShow = isSelectedApp && !shouldHide
        if (shouldShow) overlayManager?.show() else overlayManager?.hide()
    }

    private fun startScrollLoop() {
        scrollJob?.cancel()
        scrollJob = serviceScope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                val now   = SystemClock.elapsedRealtime()
                val delta = now - lastTick
                lastTick  = now

                val speed = ScrollController.tick(delta)
                val state = ScrollController.state.value

                if (state.direction != ScrollDirection.PAUSED && speed > 0f) {
                    val distance = (speed * delta / 1000f).coerceIn(20f, 800f)
                    performScroll(state.direction, distance.toInt())
                }

                val sleepMs = when {
                    speed < 200  -> 80L
                    speed < 1000 -> 50L
                    else         -> 30L
                }
                delay(sleepMs)
            }
        }
    }

    fun performScroll(direction: ScrollDirection, distancePx: Int) {
        val cx = screenW / 2f
        val halfDist = (distancePx / 2f).coerceAtLeast(10f)
        val midY = screenH / 2f

        val path = Path().apply {
            when (direction) {
                ScrollDirection.DOWN -> {
                    // Finger swipes UP → content scrolls down (reads more)
                    moveTo(cx, midY + halfDist)
                    lineTo(cx, midY - halfDist)
                }
                ScrollDirection.UP -> {
                    // Finger swipes DOWN → content scrolls up (goes back)
                    moveTo(cx, midY - halfDist)
                    lineTo(cx, midY + halfDist)
                }
                ScrollDirection.PAUSED -> return
            }
        }

        val gestureDuration = (distancePx / 10L).coerceIn(30L, 150L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, gestureDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        ScrollController.pause()
        overlayManager?.hide()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        ScrollController.pause()
        overlayManager?.destroy()
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        ScrollController.pause()
        overlayManager?.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }
}
