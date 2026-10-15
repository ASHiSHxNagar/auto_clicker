package com.ashishnagar.autoclicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ashishnagar.autoclicker.MainActivity
import com.ashishnagar.autoclicker.R
import com.ashishnagar.autoclicker.model.ClickTarget
import com.ashishnagar.autoclicker.util.PermissionUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground Service responsible for:
 * 1. Managing the floating menu bar overlay.
 * 2. Managing draggable numbered target pins.
 * 3. Executing the safe, non-blocking click loop via AutoClickerService.
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val NOTIFICATION_CHANNEL_ID = "auto_clicker_overlay_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.ashishnagar.autoclicker.ACTION_START"
        const val ACTION_STOP_SERVICE = "com.ashishnagar.autoclicker.ACTION_STOP_SERVICE"
        const val ACTION_TOGGLE_CLICK = "com.ashishnagar.autoclicker.ACTION_TOGGLE_CLICK"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"

        @Volatile
        var isOverlayRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clickJob: Job? = null

    private lateinit var windowManager: WindowManager
    private var menuView: View? = null
    private var menuLayoutParams: WindowManager.LayoutParams? = null

    private var settingsDialogView: View? = null

    // Target coordinate pins
    private val targets = mutableListOf<ClickTarget>()

    // Click configuration
    private var isPlaying = false
    private var clickIntervalMs = 300L
    private var clickDurationMs = 50L

    // Screen dimensions
    private var screenWidth = 1080
    private var screenHeight = 1920

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        measureScreen()
        createNotificationChannel()
        startAsForeground()
        createFloatingMenu()
        isOverlayRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_STOP_SERVICE -> {
                    stopAndDestroyService()
                    return START_NOT_STICKY
                }
                ACTION_TOGGLE_CLICK -> {
                    toggleClicking()
                }
                else -> {
                    val customInterval = it.getLongExtra(EXTRA_INTERVAL_MS, -1L)
                    if (customInterval >= 50L) {
                        clickIntervalMs = customInterval
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun measureScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }
    }

    // ==========================================
    // NOTIFICATION & FOREGROUND SETUP
    // ==========================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val appIntent = Intent(this, MainActivity::class.java)
        val pendingAppIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_touch_app)
            .setContentIntent(pendingAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_close, getString(R.string.action_stop), pendingStopIntent)
            .build()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ==========================================
    // FLOATING MENU WIDGET
    // ==========================================

    private fun createFloatingMenu() {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            Toast.makeText(this, "Overlay permission missing", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_menu, null)
        menuView = view

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = screenHeight / 4
        }
        menuLayoutParams = layoutParams

        setupMenuInteractions(view, layoutParams)
        safeAddView(view, layoutParams)
    }

    private fun setupMenuInteractions(view: View, params: WindowManager.LayoutParams) {
        val dragHandle = view.findViewById<ImageView>(R.id.ivDragHandle)
        val btnPlayPause = view.findViewById<ImageButton>(R.id.btnPlayPause)
        val btnAddPin = view.findViewById<ImageButton>(R.id.btnAddPin)
        val btnRemovePin = view.findViewById<ImageButton>(R.id.btnRemovePin)
        val btnSettings = view.findViewById<ImageButton>(R.id.btnSettings)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        // Draggable floating bar handle
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        safeUpdateView(view, params)
                        return true
                    }
                }
                return false
            }
        })

        // Play/Pause button
        btnPlayPause.setOnClickListener {
            toggleClicking()
        }

        // Add Target Pin button
        btnAddPin.setOnClickListener {
            addTargetPin()
        }

        // Remove Target Pin button
        btnRemovePin.setOnClickListener {
            removeTargetPin()
        }

        // Settings Dialog button
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        // Emergency Close button
        btnClose.setOnClickListener {
            stopAndDestroyService()
        }
    }

    // ==========================================
    // TARGET PIN (COORDINATE MARKERS) MANAGEMENT
    // ==========================================

    private fun addTargetPin() {
        val nextId = targets.size + 1
        val inflater = LayoutInflater.from(this)
        val pinView = inflater.inflate(R.layout.target_pin, null)

        val tvPinIndex = pinView.findViewById<TextView>(R.id.tvPinIndex)
        tvPinIndex.text = nextId.toString()

        // Calculate offset so consecutive pins don't overlap exactly
        val initialX = (screenWidth / 2) - 60 + ((nextId % 5) * 40)
        val initialY = (screenHeight / 3) + ((nextId % 5) * 40)

        val pinParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val target = ClickTarget(
            id = nextId,
            x = initialX + 60, // Approximate center offset
            y = initialY + 60,
            view = pinView,
            layoutParams = pinParams
        )

        pinView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = pinParams.x
                        initialY = pinParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        pinParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        pinParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        safeUpdateView(pinView, pinParams)

                        // Update click coordinate to the center of the target pin
                        val pinW = if (pinView.width > 0) pinView.width else 120
                        val pinH = if (pinView.height > 0) pinView.height else 120
                        target.x = pinParams.x + (pinW / 2)
                        target.y = pinParams.y + (pinH / 2)
                        return true
                    }
                }
                return false
            }
        })

        safeAddView(pinView, pinParams)
        synchronized(targets) {
            targets.add(target)
        }

        Toast.makeText(this, "Target #$nextId added. Drag to position.", Toast.LENGTH_SHORT).show()
    }

    private fun removeTargetPin() {
        synchronized(targets) {
            if (targets.isNotEmpty()) {
                val lastTarget = targets.removeAt(targets.size - 1)
                safeRemoveView(lastTarget.view)
                Toast.makeText(this, "Target #${lastTarget.id} removed", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No targets to remove", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearAllPins() {
        synchronized(targets) {
            targets.forEach { target ->
                safeRemoveView(target.view)
            }
            targets.clear()
        }
    }

    // ==========================================
    // SAFE CLICKING LOOP & GESTURE DISPATCH
    // ==========================================

    private fun toggleClicking() {
        if (isPlaying) {
            pauseClicking()
        } else {
            startClicking()
        }
    }

    private fun startClicking() {
        // Safety verification: accessibility service must be active
        val autoClicker = AutoClickerService.instance
        if (autoClicker == null) {
            Toast.makeText(
                this,
                "Accessibility Service is not active! Please enable it.",
                Toast.LENGTH_LONG
            ).show()
            PermissionUtils.requestAccessibilityPermission(this)
            return
        }

        val targetListSnapshot = synchronized(targets) { targets.toList() }
        if (targetListSnapshot.isEmpty()) {
            Toast.makeText(
                this,
                "Please add at least one target pin (+) first!",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        isPlaying = true
        updatePlayPauseButtonUI(true)

        clickJob?.cancel()
        clickJob = serviceScope.launch {
            Log.d(TAG, "Click loop started with interval $clickIntervalMs ms")
            while (isActive && isPlaying) {
                val currentTargets = synchronized(targets) { targets.toList() }
                if (currentTargets.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        pauseClicking()
                    }
                    break
                }

                for (target in currentTargets) {
                    if (!isActive || !isPlaying) break

                    // Visual feedback: briefly highlight active pin
                    withContext(Dispatchers.Main) {
                        highlightPin(target.view, true)
                    }

                    val clickFinished = CompletableDeferred<Boolean>()
                    val activeService = AutoClickerService.instance

                    if (activeService != null) {
                        activeService.clickAt(
                            x = target.x.toFloat(),
                            y = target.y.toFloat(),
                            durationMs = clickDurationMs
                        ) { success ->
                            clickFinished.complete(success)
                        }

                        // Wait for tap completion or timeout safety
                        withTimeoutOrNull(clickDurationMs + 150L) {
                            clickFinished.await()
                        }
                    } else {
                        clickFinished.complete(false)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@OverlayService,
                                "Accessibility Service disconnected.",
                                Toast.LENGTH_SHORT
                            ).show()
                            pauseClicking()
                        }
                        break
                    }

                    // Reset pin highlight
                    withContext(Dispatchers.Main) {
                        highlightPin(target.view, false)
                    }

                    // Delay between clicks with strict minimum safeguard (50ms)
                    delay(clickIntervalMs.coerceAtLeast(50L))
                }
            }
            Log.d(TAG, "Click loop exited.")
        }
    }

    private fun pauseClicking() {
        isPlaying = false
        clickJob?.cancel()
        clickJob = null
        updatePlayPauseButtonUI(false)

        // Ensure all pin highlights are reset
        synchronized(targets) {
            targets.forEach { highlightPin(it.view, false) }
        }
    }

    private fun updatePlayPauseButtonUI(playing: Boolean) {
        menuView?.let { root ->
            val btnPlayPause = root.findViewById<ImageButton>(R.id.btnPlayPause)
            if (playing) {
                btnPlayPause.setImageResource(R.drawable.ic_pause)
                btnPlayPause.setBackgroundResource(R.drawable.bg_pause_button)
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play)
                btnPlayPause.setBackgroundResource(R.drawable.bg_play_button)
            }
        }
    }

    private fun highlightPin(pinView: View, active: Boolean) {
        try {
            val circle = pinView.findViewById<View>(R.id.pinCircle)
            if (active) {
                circle.setBackgroundResource(R.drawable.bg_circle_pin_active)
            } else {
                circle.setBackgroundResource(R.drawable.bg_circle_pin)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update pin highlight: ${e.message}")
        }
    }

    // ==========================================
    // SETTINGS POPUP / CONFIGURATION
    // ==========================================

    private fun showSettingsDialog() {
        if (settingsDialogView != null) {
            dismissSettingsDialog()
            return
        }

        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_settings, null)
        settingsDialogView = dialogView

        val etInterval = dialogView.findViewById<EditText>(R.id.etInterval)
        val etDuration = dialogView.findViewById<EditText>(R.id.etDuration)
        val btn100 = dialogView.findViewById<Button>(R.id.btnPreset100)
        val btn300 = dialogView.findViewById<Button>(R.id.btnPreset300)
        val btn500 = dialogView.findViewById<Button>(R.id.btnPreset500)
        val btn1000 = dialogView.findViewById<Button>(R.id.btnPreset1000)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelSettings)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveSettings)

        etInterval.setText(clickIntervalMs.toString())
        etDuration.setText(clickDurationMs.toString())

        btn100.setOnClickListener { etInterval.setText("100") }
        btn300.setOnClickListener { etInterval.setText("300") }
        btn500.setOnClickListener { etInterval.setText("500") }
        btn1000.setOnClickListener { etInterval.setText("1000") }

        btnCancel.setOnClickListener {
            dismissSettingsDialog()
        }

        btnSave.setOnClickListener {
            val interval = etInterval.text.toString().toLongOrNull() ?: 300L
            val duration = etDuration.text.toString().toLongOrNull() ?: 50L

            // Enforce minimum safe delay of 50ms to prevent freezing user screen
            clickIntervalMs = interval.coerceAtLeast(50L)
            clickDurationMs = duration.coerceIn(10L, 500L)

            Toast.makeText(
                this,
                "Saved: Interval = ${clickIntervalMs}ms, Duration = ${clickDurationMs}ms",
                Toast.LENGTH_SHORT
            ).show()
            dismissSettingsDialog()
        }

        val dialogParams = WindowManager.LayoutParams(
            (screenWidth * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        safeAddView(dialogView, dialogParams)
    }

    private fun dismissSettingsDialog() {
        settingsDialogView?.let {
            safeRemoveView(it)
            settingsDialogView = null
        }
    }

    // ==========================================
    // WINDOW MANAGER SAFE VIEW HELPERS
    // ==========================================

    private fun safeAddView(view: View, params: WindowManager.LayoutParams) {
        try {
            if (view.parent == null) {
                windowManager.addView(view, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding view to WindowManager: ${e.message}")
        }
    }

    private fun safeUpdateView(view: View, params: WindowManager.LayoutParams) {
        try {
            if (view.parent != null) {
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating view in WindowManager: ${e.message}")
        }
    }

    private fun safeRemoveView(view: View) {
        try {
            if (view.parent != null) {
                windowManager.removeView(view)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing view from WindowManager: ${e.message}")
        }
    }

    // ==========================================
    // EMERGENCY STOP & SERVICE LIFECYCLE
    // ==========================================

    private fun stopAndDestroyService() {
        pauseClicking()
        dismissSettingsDialog()
        clearAllPins()

        menuView?.let {
            safeRemoveView(it)
            menuView = null
        }

        isOverlayRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pauseClicking()
        dismissSettingsDialog()
        clearAllPins()

        menuView?.let {
            safeRemoveView(it)
            menuView = null
        }

        serviceScope.cancel()
        isOverlayRunning = false
        Log.d(TAG, "OverlayService destroyed completely.")
        super.onDestroy()
    }
}
