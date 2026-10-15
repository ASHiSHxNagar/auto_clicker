package com.ashishnagar.autoclicker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ashishnagar.autoclicker.databinding.ActivityMainBinding
import com.ashishnagar.autoclicker.service.OverlayService
import com.ashishnagar.autoclicker.util.PermissionUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()
    }

    private fun setupListeners() {
        binding.btnGrantOverlay.setOnClickListener {
            if (!PermissionUtils.hasOverlayPermission(this)) {
                PermissionUtils.requestOverlayPermission(this)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEnableAccessibility.setOnClickListener {
            if (!PermissionUtils.isAccessibilityServiceEnabled(this)) {
                PermissionUtils.requestAccessibilityPermission(this)
            } else {
                Toast.makeText(this, "Accessibility service is already enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Quick speed preset buttons
        binding.btnMain100.setOnClickListener { binding.etMainInterval.setText("100") }
        binding.btnMain300.setOnClickListener { binding.etMainInterval.setText("300") }
        binding.btnMain500.setOnClickListener { binding.etMainInterval.setText("500") }
        binding.btnMain1000.setOnClickListener { binding.etMainInterval.setText("1000") }

        // Start / Stop Overlay button
        binding.btnStartOverlay.setOnClickListener {
            val hasOverlay = PermissionUtils.hasOverlayPermission(this)
            val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(this)

            if (!hasOverlay) {
                Toast.makeText(this, "Please grant Floating Overlay permission first", Toast.LENGTH_SHORT).show()
                PermissionUtils.requestOverlayPermission(this)
                return@setOnClickListener
            }

            if (!hasAccessibility) {
                Toast.makeText(this, "Please enable Auto Clicker in Accessibility Settings", Toast.LENGTH_SHORT).show()
                PermissionUtils.requestAccessibilityPermission(this)
                return@setOnClickListener
            }

            if (OverlayService.isOverlayRunning) {
                // Stop overlay
                val stopIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP_SERVICE
                }
                startService(stopIntent)
                Toast.makeText(this, "Floating overlay stopped", Toast.LENGTH_SHORT).show()
                refreshPermissionStatuses()
            } else {
                // Start overlay
                val interval = binding.etMainInterval.text.toString().toLongOrNull() ?: 300L
                val safeInterval = interval.coerceAtLeast(50L)

                val startIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                    putExtra(OverlayService.EXTRA_INTERVAL_MS, safeInterval)
                }
                ContextCompat.startForegroundService(this, startIntent)
                Toast.makeText(this, "Floating overlay launched!", Toast.LENGTH_SHORT).show()

                // Minimize activity so user is ready on their home screen or target game/app
                moveTaskToBack(true)
            }
        }
    }

    private fun refreshPermissionStatuses() {
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        val hasAccessibility = PermissionUtils.isAccessibilityServiceEnabled(this)

        // Overlay status UI
        if (hasOverlay) {
            binding.ivOverlayStatus.setImageResource(R.drawable.ic_check)
            binding.tvOverlayStatus.text = getString(R.string.permission_granted)
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnGrantOverlay.isEnabled = false
            binding.btnGrantOverlay.text = "Granted"
        } else {
            binding.ivOverlayStatus.setImageResource(R.drawable.ic_warning)
            binding.tvOverlayStatus.text = getString(R.string.permission_missing)
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            binding.btnGrantOverlay.isEnabled = true
            binding.btnGrantOverlay.text = getString(R.string.grant_permission)
        }

        // Accessibility status UI
        if (hasAccessibility) {
            binding.ivAccessibilityStatus.setImageResource(R.drawable.ic_check)
            binding.tvAccessibilityStatus.text = getString(R.string.permission_granted)
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnEnableAccessibility.isEnabled = false
            binding.btnEnableAccessibility.text = "Active"
        } else {
            binding.ivAccessibilityStatus.setImageResource(R.drawable.ic_warning)
            binding.tvAccessibilityStatus.text = getString(R.string.permission_missing)
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            binding.btnEnableAccessibility.isEnabled = true
            binding.btnEnableAccessibility.text = getString(R.string.enable_service)
        }

        // Toggle button UI based on running state
        if (OverlayService.isOverlayRunning) {
            binding.btnStartOverlay.text = getString(R.string.stop_overlay)
            binding.btnStartOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_red))
            binding.btnStartOverlay.setIconResource(R.drawable.ic_close)
        } else {
            binding.btnStartOverlay.text = getString(R.string.start_overlay)
            binding.btnStartOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
            binding.btnStartOverlay.setIconResource(R.drawable.ic_play)
        }
    }
}
