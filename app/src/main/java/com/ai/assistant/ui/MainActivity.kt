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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ai.assistant.R
import com.ai.assistant.ai.AIBrainManager
import com.ai.assistant.data.local.EncryptedAppDatabase
import com.ai.assistant.data.repository.MemoryRepository
import com.ai.assistant.service.AutomationAccessibilityService
import com.ai.assistant.service.CoreForegroundService
import com.ai.assistant.util.SystemControlUtil
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var systemUtil: SystemControlUtil
    private lateinit var aiBrainManager: AIBrainManager

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
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    systemUtil = SystemControlUtil(this)
    setupAIBrain()

    val btnSetup = findViewById<Button>(R.id.btnSetupPermissions)

    btnSetup.setOnClickListener {
        requestAllPermissions()
    }

    startCoreAgentService()
    }

        val btnSetup = findViewById<Button>(R.id.btnSetupPermissions)
        val btnTorchOn = findViewById<Button>(R.id.btnTorchOn)
        val btnTorchOff = findViewById<Button>(R.id.btnTorchOff)
        val btnSendToAI = findViewById<Button>(R.id.btnSendToAI)
        val etUserPrompt = findViewById<EditText>(R.id.etUserPrompt)
        val tvAiResponse = findViewById<TextView>(R.id.tvAiResponse)

        btnSetup.setOnClickListener { requestAllPermissions() }

        btnTorchOn.setOnClickListener {
            try { systemUtil.setTorch(true) } catch (e: Exception) { Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show() }
        }

        btnTorchOff.setOnClickListener {
            try { systemUtil.setTorch(false) } catch (e: Exception) { Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show() }
        }

        btnSendToAI.setOnClickListener {
            val prompt = etUserPrompt.text.toString().trim()
            if (prompt.isNotEmpty()) {
                tvAiResponse.text = "AI Process Kar Raha Hai..."
                lifecycleScope.launch {
                    val response = aiBrainManager.processUserQuery(prompt)
                    tvAiResponse.text = response
                    etUserPrompt.setText("")
                }
            }
        }

        startCoreAgentService()
    }

    private fun setupAIBrain() {
        val passphrase = "MySecureEncryptionKey123".toByteArray()
        val db = EncryptedAppDatabase.getDatabase(this, passphrase)
        val repository = MemoryRepository(db.userMemoryDao())
        
        // APNI REAL GEMINI API KEY YAHAN DALAIN
        val apiKey = "AQ.Ab8RN6INuS3aGgfEcxGq3xfxbAgG6mVmNNR4FvurFDIoHLaukQ"
        
        aiBrainManager = AIBrainManager(repository, apiKey)
    }

    private fun requestAllPermissions() {
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
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean = AutomationAccessibilityService.instance != null

    private fun startCoreAgentService() {
        val intent = Intent(this, CoreForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}
