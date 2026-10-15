package com.ashishnagar.autoclicker.model

import android.view.View
import android.view.WindowManager

/**
 * Represents a draggable click marker on screen.
 */
data class ClickTarget(
    val id: Int,
    var x: Int,
    var y: Int,
    val view: View,
    val layoutParams: WindowManager.LayoutParams
)
