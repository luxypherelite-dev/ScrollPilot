package com.scrollpilot.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.scrollpilot.app.data.GlobalSettings
import com.scrollpilot.app.data.SettingsDataStore
import com.scrollpilot.app.ui.overlay.FloatingOverlay
import com.scrollpilot.app.ui.theme.ScrollPilotTheme
import kotlinx.coroutines.*

class OverlayManager(private val ctx: Context) {

    private val wm by lazy { ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var isShowing = false

    // Reactive settings state — changes trigger Compose recomposition automatically
    private val settingsState = mutableStateOf(GlobalSettings())

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val lifecycleOwner = OverlayLifecycleOwner()

    init {
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun updateSettings(settings: GlobalSettings) {
        settingsState.value = settings
    }

    fun show() {
        if (isShowing) return
        buildView()
        try {
            wm.addView(composeView, params)
            isShowing = true
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (_: Exception) {}
    }

    fun hide() {
        if (!isShowing) return
        try { wm.removeView(composeView) } catch (_: Exception) {}
        composeView = null
        isShowing   = false
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        ScrollController.pause()
    }

    fun destroy() {
        hide()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        scope.cancel()
    }

    private fun buildView() {
        val metrics = ctx.resources.displayMetrics
        val savedX  = settingsState.value.overlayX
        val savedY  = settingsState.value.overlayY

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX >= 0) savedX else (metrics.widthPixels - 250)
            y = if (savedY >= 0) savedY else (metrics.heightPixels / 2)
        }
        params = lp

        val view = ComposeView(ctx).apply {
            setContent {
                ScrollPilotTheme {
                    // settingsState.value is a Compose state — recompose auto-triggers on change
                    FloatingOverlay(settings = settingsState.value)
                }
            }
        }

        // Wire up lifecycle so Compose works inside WindowManager
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        val recomposer = Recomposer(AndroidUiDispatcher.CurrentThread)
        view.compositionContext = recomposer
        scope.launch(AndroidUiDispatcher.CurrentThread) {
            recomposer.runRecomposeAndApplyChanges()
        }

        // Drag handler — translate the window when finger drags
        var startRawX = 0f; var startRawY = 0f
        var startLpX  = 0;  var startLpY  = 0
        var isDragging = false

        view.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = ev.rawX; startRawY = ev.rawY
                    startLpX  = lp.x;   startLpY  = lp.y
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - startRawX).toInt()
                    val dy = (ev.rawY - startRawY).toInt()
                    if (!isDragging && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) isDragging = true
                    if (isDragging) {
                        lp.x = startLpX + dx
                        lp.y = startLpY + dy
                        try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
                        if (settingsState.value.rememberLastPosition) {
                            scope.launch { SettingsDataStore.setOverlayPosition(ctx, lp.x, lp.y) }
                        }
                    }
                    isDragging
                }
                else -> false
            }
        }

        composeView = view
    }
}

private class OverlayLifecycleOwner : SavedStateRegistryOwner, LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private val ssrc     = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle                    get() = registry
    override val savedStateRegistry: SavedStateRegistry  get() = ssrc.savedStateRegistry

    fun performRestore(bundle: Bundle?) = ssrc.performRestore(bundle)
    fun handleLifecycleEvent(e: Lifecycle.Event) = registry.handleLifecycleEvent(e)
}
