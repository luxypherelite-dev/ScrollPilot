package com.scrollpilot.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.scrollpilot.app.R
import com.scrollpilot.app.data.GlobalSettings
import com.scrollpilot.app.data.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class ScrollPilotAccessibilityService : AccessibilityService() {

    private var overlayManager: OverlayManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentSettings = GlobalSettings()
    private var currentPkg = ""
    private var overlayVisible = false

    // Volume-key emergency combo tracking
    private var volUpHeld   = false
    private var volDownHeld = false

    // ── lifecycle ─────────────────────────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()
        ScrollController.service = this
        overlayManager = OverlayManager(this)

        // Request key events so we can intercept volume keys
        serviceInfo = serviceInfo?.also { info ->
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        createNotificationChannel()
        showPersistentNotification()
        registerSystemReceivers()

        scope.launch {
            SettingsDataStore.observe(this@ScrollPilotAccessibilityService)
                .collectLatest { settings ->
                    currentSettings = settings
                    ScrollController.settings = settings
                    overlayManager?.updateSettings(settings)
                    updateOverlayVisibility()
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            // User touched the screen → immediately stop scrolling
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                if (ScrollController.state.value != ScrollState.IDLE) {
                    ScrollController.pause()
                }
            }

            // App changed → update overlay visibility
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                if (pkg != currentPkg) {
                    currentPkg = pkg
                    // Also stop scrolling when switching apps
                    ScrollController.stop()
                    updateOverlayVisibility()
                }
            }

            // Input method (keyboard) appeared/disappeared
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (currentSettings.hideOnKeyboard) {
                    val keyboardOpen = isKeyboardVisible()
                    if (keyboardOpen && overlayVisible) {
                        ScrollController.stop()
                        overlayManager?.hide()
                        overlayVisible = false
                    } else if (!keyboardOpen) {
                        updateOverlayVisibility()
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        ScrollController.stop()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP   -> volUpHeld   = event.action == KeyEvent.ACTION_DOWN
            KeyEvent.KEYCODE_VOLUME_DOWN -> volDownHeld = event.action == KeyEvent.ACTION_DOWN
        }
        // Both volume keys held simultaneously → emergency stop
        if (volUpHeld && volDownHeld && ScrollController.state.value != ScrollState.IDLE) {
            ScrollController.stop()
            return true
        }
        return false
    }

    override fun onUnbind(intent: Intent): Boolean {
        ScrollController.stop()
        ScrollController.service = null
        overlayManager?.destroy()
        overlayManager = null
        scope.cancel()
        return super.onUnbind(intent)
    }

    // ── overlay visibility logic ──────────────────────────────────────────────
    private fun updateOverlayVisibility() {
        val shouldShow = currentPkg.isNotEmpty() && currentSettings.enabledApps.contains(currentPkg)
        when {
            shouldShow && !overlayVisible  -> { overlayManager?.show(); overlayVisible = true  }
            !shouldShow && overlayVisible  -> { overlayManager?.hide(); overlayVisible = false }
        }
    }

    private fun isKeyboardVisible(): Boolean {
        val wm = getSystemService(Context.WINDOW_SERVICE)
                as android.view.WindowManager
        val windows = windows ?: return false
        return windows.any { it.type == android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD }
    }

    // ── persistent notification ───────────────────────────────────────────────
    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "ScrollPilot Controls",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager().createNotificationChannel(ch)
    }

    private fun showPersistentNotification() {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val stopIntent = PendingIntent.getBroadcast(this, 0,
            Intent(ACTION_STOP), flags)
        val hideIntent = PendingIntent.getBroadcast(this, 1,
            Intent(ACTION_HIDE), flags)
        val exitIntent = PendingIntent.getBroadcast(this, 2,
            Intent(ACTION_EXIT), flags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ScrollPilot is running")
            .setContentText("Tap an action to control scrolling")
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "STOP SCROLL", stopIntent)
            .addAction(0, "HIDE OVERLAY", hideIntent)
            .addAction(0, "EXIT", exitIntent)
            .build()

        notificationManager().notify(NOTIF_ID, notification)
    }

    private fun notificationManager() =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    // ── system receivers ──────────────────────────────────────────────────────
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> ScrollController.stop()
                ACTION_STOP  -> ScrollController.stop()
                ACTION_HIDE  -> { overlayManager?.hide(); overlayVisible = false }
                ACTION_EXIT  -> {
                    ScrollController.stop()
                    overlayManager?.destroy()
                    notificationManager().cancel(NOTIF_ID)
                    disableSelf()
                }
            }
        }
    }

    private fun registerSystemReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(ACTION_STOP)
            addAction(ACTION_HIDE)
            addAction(ACTION_EXIT)
        }
        registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    companion object {
        private const val CHANNEL_ID = "scrollpilot_controls"
        private const val NOTIF_ID   = 9001
        const val ACTION_STOP        = "com.scrollpilot.STOP"
        const val ACTION_HIDE        = "com.scrollpilot.HIDE"
        const val ACTION_EXIT        = "com.scrollpilot.EXIT"
    }
}
