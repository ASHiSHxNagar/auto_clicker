package com.ashishnagar.autoclicker.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.ashishnagar.autoclicker.service.AutoClickerService

object PermissionUtils {

    /**
     * Checks if SYSTEM_ALERT_WINDOW permission is granted.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Navigates to system overlay permission management screen.
     */
    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Checks whether the AutoClickerService is enabled in Accessibility Settings.
     */
    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService> = AutoClickerService::class.java
    ): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.name}"
        val simpleComponentName = "${context.packageName}/.${serviceClass.simpleName}"

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                componentName.equals(simpleComponentName, ignoreCase = true) ||
                componentName.contains(serviceClass.simpleName, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Opens system Accessibility settings.
     */
    fun requestAccessibilityPermission(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
