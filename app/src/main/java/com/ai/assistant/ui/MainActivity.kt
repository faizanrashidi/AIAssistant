package com.ai.assistant.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
// Import your app resources
import com.ai.assistant.R
import com.ai.assistant.ai.AIBrainManager
import com.ai.assistant.data.local.EncryptedAppDatabase
import com.ai.assistant.data.repository.MemoryRepository
import com.ai.assistant.service.CoreForegroundService
import com.ai.assistant.util.LocationReminderManager
import com.ai.assistant.util.SystemControlUtil
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, SensorEventListener {

    private var systemUtil: SystemControlUtil? = null
    private var aiBrainManager: AIBrainManager? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var telecomManager: TelecomManager? = null
    private var audioManager: AudioManager? = null
    private var locationReminderManager: LocationReminderManager? = null

    private var sensorManager: SensorManager? = null
    private var accelValue = 0f
    private var accelCurrent = 0f
    private var accelLast = 0f

    private var awaitingCallDecision = false

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra("SENDER") ?: "Kisi"
            val message = intent?.getStringExtra("MESSAGE") ?: ""
            if (message.isNotEmpty()) {
                speakOut("$sender ka message aaya hai: $message")
            }
        }
    }

    private val cameraCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                analyzeImageWithVisionAI(bitmap)
            }
        }
    }

    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show()
                checkSystemSpecialPermissions()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Fixed parameter issue
        setContentView(R.layout.activity_main)

        try {
            systemUtil = SystemControlUtil(this)
            telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            locationReminderManager = LocationReminderManager(this)

            setupAIBrain()
            textToSpeech = TextToSpeech(this, this)
            setupSpeechRecognizer()
            setupCallListener()

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager?.registerListener(this, sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
            accelCurrent = SensorManager.GRAVITY_EARTH
            accelLast = SensorManager.GRAVITY_EARTH

            val filter = IntentFilter("com.ai.assistant.READ_NOTIFICATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notificationReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(notificationReceiver, filter)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Init Exception: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnSetupPermissions)?.setOnClickListener { requestAllPermissions() }
        findViewById<Button>(R.id.btnMic)?.setOnClickListener { startVoiceInput() }

        findViewById<Button>(R.id.btnTorchOn)?.setOnClickListener {
            systemUtil?.setTorch(true)
            speakOut("Torch chalu kar di hai.")
        }

        findViewById<Button>(R.id.btnTorchOff)?.setOnClickListener {
            systemUtil?.setTorch(false)
            speakOut("Torch band kar di hai.")
        }

        findViewById<Button>(R.id.btnSendToAI)?.setOnClickListener {
            val prompt = findViewById<EditText>(R.id.etUserPrompt)?.text?.toString()?.trim() ?: ""
            if (prompt.isNotEmpty()) {
                handleUserCommand(prompt, findViewById(R.id.tvAiResponse), findViewById(R.id.etUserPrompt))
            }
        }

        startCoreAgentService()

        if (intent.getBooleanExtra("START_VOICE", false)) {
            startVoiceInput()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("START_VOICE", false) == true) {
            startVoiceInput()
        }
    }

    private fun handleUserCommand(prompt: String, tvAiResponse: TextView?, etUserPrompt: EditText?) {
        val lowerText = prompt.lowercase()

        if (lowerText.contains("location reminder") || lowerText.contains("jagah par yaad")) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION), 101)
                speakOut("Permission allow karein.")
                return
            }
            val message = lowerText.replace("location reminder", "").replace("lagao", "").trim().ifEmpty { "Aap apni manchahi location par pahuche hain." }
            locationReminderManager?.addLocationReminder(
                id = "REMINDER_${System.currentTimeMillis()}",
                latitude = 28.6139,
                longitude = 77.2090,
                radiusInMeters = 200f,
                reminderText = message
            )
            speakOut("Thik hai, is location par pahunchne par main yaad dila dunga.")
            etUserPrompt?.setText("")
            return
        }

        if (lowerText.contains("dekho") || lowerText.contains("scan") || lowerText.contains("photo khincho")) {
            speakOut("Camera open kar raha hoon.")
            openCameraForVision()
            etUserPrompt?.setText("")
            return
        }

        if (awaitingCallDecision) {
            if (lowerText.contains("receive") || lowerText.contains("uthai")) {
                acceptIncomingCall()
                speakOut("Call receive kar di hai.")
            } else if (lowerText.contains("reject") || lowerText.contains("kato")) {
                rejectIncomingCall()
                speakOut("Call cut kar di hai.")
            }
            awaitingCallDecision = false
            return
        }

        if (lowerText.contains("whatsapp") && (lowerText.contains("message") || lowerText.contains("bhejo"))) {
            sendWhatsAppMessage(lowerText)
            etUserPrompt?.setText("")
            return
        }

        if (lowerText.contains("play") || lowerText.contains("youtube par")) {
            val query = lowerText.replace("play", "").replace("youtube par", "").replace("chalo", "").trim()
            playOnYouTube(query)
            etUserPrompt?.setText("")
            return
        }

        if (lowerText.contains("call karo") || lowerText.contains("dial")) {
            val number = lowerText.replace("[^0-9]".toRegex(), "")
            if (number.isNotEmpty()) makePhoneCall(number) else speakOut("Number nahi mila.")
            etUserPrompt?.setText("")
            return
        }

        if (lowerText.contains("battery")) {
            checkBatteryStatus()
            etUserPrompt?.setText("")
            return
        }

        if (lowerText.contains("volume up") || lowerText.contains("aawaz badhao")) {
            adjustVolume(true)
            etUserPrompt?.setText("")
            return
        } else if (lowerText.contains("volume down") || lowerText.contains("aawaz kam karo")) {
            adjustVolume(false)
            etUserPrompt?.setText("")
            return
        }

        tvAiResponse?.text = "AI Thinking..."
        lifecycleScope.launch {
            try {
                val responseText = aiBrainManager?.processUserQuery(prompt) ?: "Brain Error!"
                tvAiResponse?.text = responseText
                speakOut(responseText)
                etUserPrompt?.setText("")
            } catch (e: Exception) {
                tvAiResponse?.text = "Error: ${e.localizedMessage}"
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        accelLast = accelCurrent
        accelCurrent = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = accelCurrent - accelLast
        accelValue = accelValue * 0.9f + delta

        if (accelValue > 12) {
            accelValue = 0f
            speakOut("Haan boliye, main sun raha hoon.")
            startVoiceInput()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun openCameraForVision() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            cameraCaptureLauncher.launch(cameraIntent)
        }
    }

    private fun analyzeImageWithVisionAI(bitmap: Bitmap) {
        speakOut("Photo ko analyze kar raha hoon...")
        lifecycleScope.launch {
            try {
                val aiAnalysis = aiBrainManager?.processUserQuery("Describe photo in Hindi") ?: "Analyze failed"
                findViewById<TextView>(R.id.tvAiResponse)?.text = aiAnalysis
                speakOut(aiAnalysis)
            } catch (e: Exception) {
                speakOut("Error parsing photo.")
            }
        }
    }

    private fun sendWhatsAppMessage(command: String) {
        val digits = command.replace("[^0-9]".toRegex(), "")
        if (digits.length >= 10) {
            val phone = if (digits.length == 10) "91$digits" else digits
            val msg = command.substringAfter(digits).trim().ifEmpty { "Hello" }
            val url = "https://api.whatsapp.com/send?phone=$phone&text=${URLEncoder.encode(msg, "UTF-8")}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("com.whatsapp") })
            speakOut("Message bhej raha hoon.")
        }
    }

    private fun playOnYouTube(query: String) {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        speakOut("$query search kar raha hoon.")
    }

    private fun makePhoneCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            speakOut("Call laga raha hoon.")
        }
    }

    private fun checkBatteryStatus() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        speakOut("Battery $batLevel percent hai.")
    }

    private fun adjustVolume(increase: Boolean) {
        val dir = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        speakOut(if (increase) "Volume badha diya." else "Volume kam kar diya.")
    }

    private fun setupCallListener() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    awaitingCallDecision = true
                    speakOut("Call aa rahi hai. Receive karun ya reject karun?")
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
    }

    @SuppressLint("MissingPermission")
    private fun acceptIncomingCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) telecomManager?.acceptRingingCall()
    }

    @SuppressLint("MissingPermission")
    private fun rejectIncomingCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) telecomManager?.endCall()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    findViewById<EditText>(R.id.etUserPrompt)?.setText(text)
                    handleUserCommand(text, findViewById(R.id.tvAiResponse), findViewById(R.id.etUserPrompt))
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) textToSpeech?.language = Locale("hi", "IN")
    }

    private fun speakOut(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun setupAIBrain() {
        val db = EncryptedAppDatabase.getDatabase(applicationContext, "MyKey123".toByteArray())
        val repository = MemoryRepository(db.userMemoryDao())
        val apiKey = "YOUR_GEMINI_API_KEY_HERE"
        aiBrainManager = AIBrainManager(repository, apiKey)
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        runtimePermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkSystemSpecialPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
    }

    private fun startCoreAgentService() {
        val intent = Intent(this, CoreForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        unregisterReceiver(notificationReceiver)
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
