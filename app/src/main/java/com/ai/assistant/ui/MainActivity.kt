package com.ai.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ai.assistant.R
import com.ai.assistant.service.AutomationAccessibilityService
import com.ai.assistant.service.CoreForegroundService

class MainActivity : AppCompatActivity() {

    // Standard Runtime Permissions Engine
    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "All Standard Permissions Granted!", Toast.LENGTH_SHORT).show()
                checkSystemSpecialPermissions()
            } else {
                Toast.makeText(this, "Permissions are required for AI Agent to work.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        setContentView(R.layout.activity_main)

        val btnSetup = findViewById<Button>(R.id.btnSetupPermissions)
        btnSetup.setOnClickListener {
            requestAllPermissions()
        }

        // Core Foreground Service Start
        startCoreAgentService()
    }

    private fun requestAllPermissions() {
        // Step 1: Standard Runtime Permissions array
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        runtimePermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkSystemSpecialPermissions() {
        // Step 2: Overlay Permission (Display over other apps)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Enable 'Display Over Other Apps' Permission", Toast.LENGTH_LONG).show()
            return
        }

        // Step 3: Accessibility Service Permission Check
        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'AI System Agent' in Accessibility Services", Toast.LENGTH_LONG).show()
            return
        }

        // Step 4: Ignore Battery Optimization (Background Persistence)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }

        Toast.makeText(this, "AI Assistant fully configured and running!", Toast.LENGTH_SHORT).show()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AutomationAccessibilityService.instance != null
    }

    private fun startCoreAgentService() {
        val intent = Intent(this, CoreForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}
