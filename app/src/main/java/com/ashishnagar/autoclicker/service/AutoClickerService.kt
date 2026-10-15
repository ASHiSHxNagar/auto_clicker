package com.ashishnagar.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Native AccessibilityService responsible for dispatching touch/click gestures.
 */
class AutoClickerService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickerService"

        @Volatile
        var instance: AutoClickerService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AutoClickerService connected and ready to dispatch gestures.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception needed for auto-clicking; gestures are programmatically dispatched.
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoClickerService interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.d(TAG, "AutoClickerService unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "AutoClickerService destroyed.")
        super.onDestroy()
    }

    /**
     * Dispatches a tap gesture at the specified screen coordinate (x, y).
     *
     * @param x Screen X coordinate in pixels
     * @param y Screen Y coordinate in pixels
     * @param durationMs Tap stroke duration (default 50ms)
     * @param onComplete Callback invoked with true if gesture was completed, false if cancelled or failed
     */
    fun clickAt(
        x: Float,
        y: Float,
        durationMs: Long = 50L,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }

        val safeDuration = durationMs.coerceIn(10L, 500L)
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture at ($x, $y) cancelled.")
                onComplete?.invoke(false)
            }
        }

        val dispatched = dispatchGesture(gesture, callback, null)
        if (!dispatched) {
            Log.e(TAG, "dispatchGesture returned false for coordinate ($x, $y).")
            onComplete?.invoke(false)
        }
    }

    /**
     * Dispatches a swipe gesture from (startX, startY) to (endX, endY).
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val safeDuration = durationMs.coerceIn(100L, 2000L)
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onComplete?.invoke(false)
            }
        }

        val dispatched = dispatchGesture(gesture, callback, null)
        if (!dispatched) {
            onComplete?.invoke(false)
        }
    }
}
