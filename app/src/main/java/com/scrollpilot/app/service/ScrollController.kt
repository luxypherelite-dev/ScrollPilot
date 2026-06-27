package com.scrollpilot.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityNodeInfo
import com.scrollpilot.app.data.GlobalSettings
import com.scrollpilot.app.data.ScrollEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScrollState { IDLE, SCROLLING_UP, SCROLLING_DOWN }

object ScrollController {

    // ── reference set by the accessibility service ──────────────────────────
    @Volatile var service: ScrollPilotAccessibilityService? = null
    @Volatile var settings: GlobalSettings = GlobalSettings()

    // ── observable state ─────────────────────────────────────────────────────
    private val _state = MutableStateFlow(ScrollState.IDLE)
    val state: StateFlow<ScrollState> = _state.asStateFlow()

    private val _speed = MutableStateFlow(3000f)   // conceptual px/s, 200 – 20 000
    val speed: StateFlow<Float> = _speed.asStateFlow()

    const val MIN_SPEED  = 200f
    const val MAX_SPEED  = 20_000f
    const val SPEED_STEP = 500f

    // ── internals ────────────────────────────────────────────────────────────
    private val scope     = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scrollJob: Job? = null

    // ── direction controls ────────────────────────────────────────────────────
    /** Press Up: IDLE→UP, UP→IDLE, DOWN→UP */
    fun pressUp() {
        _state.value = when (_state.value) {
            ScrollState.IDLE          -> ScrollState.SCROLLING_UP
            ScrollState.SCROLLING_UP  -> ScrollState.IDLE
            ScrollState.SCROLLING_DOWN -> ScrollState.SCROLLING_UP
        }
        restartLoop()
    }

    /** Press Down: IDLE→DOWN, DOWN→IDLE, UP→DOWN */
    fun pressDown() {
        _state.value = when (_state.value) {
            ScrollState.IDLE           -> ScrollState.SCROLLING_DOWN
            ScrollState.SCROLLING_DOWN -> ScrollState.IDLE
            ScrollState.SCROLLING_UP   -> ScrollState.SCROLLING_DOWN
        }
        restartLoop()
    }

    /** Pause immediately – user touched the screen or external trigger */
    fun pause() {
        if (_state.value == ScrollState.IDLE) return
        _state.value = ScrollState.IDLE
        scrollJob?.cancel()
    }

    /** Alias for UI "STOP" action */
    fun stop() = pause()

    // ── speed controls (never change direction) ───────────────────────────────
    fun setSpeed(s: Float)  { _speed.value = s.coerceIn(MIN_SPEED, MAX_SPEED) }
    fun speedUp()            { setSpeed(_speed.value + SPEED_STEP) }
    fun speedDown()          { setSpeed(_speed.value - SPEED_STEP) }

    // ── internal loop ─────────────────────────────────────────────────────────
    private fun restartLoop() {
        scrollJob?.cancel()
        if (_state.value == ScrollState.IDLE) return
        scrollJob = scope.launch {
            while (isActive && _state.value != ScrollState.IDLE) {
                val svc = service ?: break
                try {
                    performScroll(svc)
                } catch (e: CancellationException) {
                    break
                } catch (_: Exception) {
                    delay(500)
                }
            }
        }
    }

    // ── dispatch one scroll action ────────────────────────────────────────────
    private suspend fun performScroll(svc: ScrollPilotAccessibilityService) {
        val scrollingUp = _state.value == ScrollState.SCROLLING_UP
        val spd         = _speed.value
        val cfg         = settings

        val effectiveEngine = when (cfg.scrollEngine) {
            ScrollEngine.HYBRID ->
                if (spd >= cfg.swipeToFlingThreshold) ScrollEngine.FLING else ScrollEngine.SWIPE
            else -> cfg.scrollEngine
        }

        when (effectiveEngine) {
            ScrollEngine.SWIPE    -> doSwipe(svc, scrollingUp, spd)
            ScrollEngine.FLING    -> doFling(svc, scrollingUp, spd, cfg.flingWaitMs)
            ScrollEngine.PAGE_JUMP -> doPageJump(svc, scrollingUp, spd)
            ScrollEngine.HYBRID  -> doSwipe(svc, scrollingUp, spd) // shouldn't reach here
        }
    }

    // ── SWIPE engine ──────────────────────────────────────────────────────────
    // SCROLLING_UP = see older/higher content → finger drags DOWN (startY < endY)
    // SCROLLING_DOWN = see newer/lower content → finger drags UP  (startY > endY)
    private suspend fun doSwipe(svc: ScrollPilotAccessibilityService, up: Boolean, spd: Float) {
        val m  = svc.resources.displayMetrics
        val sw = m.widthPixels.toFloat()
        val sh = m.heightPixels.toFloat()

        val frac     = (spd / MAX_SPEED).coerceIn(0.05f, 1f)
        val dist     = (frac * sh * 0.55f).coerceIn(sh * 0.08f, sh * 0.6f)
        val gestMs   = 130L
        val cx       = sw / 2f

        val startY   = if (up) sh * 0.35f else sh * 0.65f
        val endY     = if (up) startY + dist else startY - dist

        val path   = Path().apply { moveTo(cx, startY); lineTo(cx, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, gestMs)
        val gest   = GestureDescription.Builder().addStroke(stroke).build()

        awaitGesture(svc, gest)

        val delayMs = ((dist / spd * 1000f) - gestMs).toLong().coerceIn(16L, 2000L)
        delay(delayMs)
    }

    // ── FLING engine ──────────────────────────────────────────────────────────
    // Full-screen fast sweep → triggers app momentum physics
    private suspend fun doFling(svc: ScrollPilotAccessibilityService, up: Boolean, spd: Float, waitMs: Long) {
        val m  = svc.resources.displayMetrics
        val sw = m.widthPixels.toFloat()
        val sh = m.heightPixels.toFloat()

        // Faster speed → shorter wait between flings (more flings/sec)
        val wait  = (waitMs * (1f - (spd - 8000f) / 12000f * 0.5f)).toLong().coerceIn(400L, 2000L)
        val flingMs = 60L
        val cx    = sw / 2f

        val startY = if (up) sh * 0.1f else sh * 0.9f
        val endY   = if (up) sh * 0.9f else sh * 0.1f

        val path   = Path().apply { moveTo(cx, startY); lineTo(cx, endY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, flingMs)
        val gest   = GestureDescription.Builder().addStroke(stroke).build()

        awaitGesture(svc, gest)
        delay(wait)
    }

    // ── PAGE JUMP engine ──────────────────────────────────────────────────────
    private suspend fun doPageJump(svc: ScrollPilotAccessibilityService, up: Boolean, spd: Float) {
        val action   = if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                       else    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val root     = svc.rootInActiveWindow
        val success  = root?.let { findScrollable(it)?.performAction(action) } ?: false
        root?.recycle()

        if (!success) {
            // Fallback to fling when page jump isn't supported
            doFling(svc, up, spd, settings.flingWaitMs)
        } else {
            val waitMs = (800f * (MAX_SPEED / spd.coerceAtLeast(1f))).toLong().coerceIn(200L, 2000L)
            delay(waitMs)
        }
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child  = node.getChild(i) ?: continue
            val result = findScrollable(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    // ── Coroutine gesture dispatcher ──────────────────────────────────────────
    private suspend fun awaitGesture(svc: AccessibilityService, gest: GestureDescription) {
        val done = CompletableDeferred<Unit>()
        val dispatched = svc.dispatchGesture(gest, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { done.complete(Unit) }
            override fun onCancelled(g: GestureDescription?) { done.complete(Unit) }
        }, null)
        if (!dispatched) { delay(100); return }
        done.await()
    }
}
