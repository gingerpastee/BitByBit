package com.example.cradet

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class MonitoringService : Service() {

    private lateinit var sensorHelper: SensorHelper
    private lateinit var bleHelper: BleHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var smsHelper: SmsHelper
    private lateinit var profileManager: ProfileManager
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        const val CHANNEL_ID = "MonitoringServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "STOP_MONITORING"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MonitoringService", "Service Created")
        
        sensorHelper = SensorHelper(this)
        bleHelper = BleHelper(this)
        locationHelper = LocationHelper(this)
        smsHelper = SmsHelper(this)
        profileManager = ProfileManager(this)

        setupSensorListeners()
        setupBleListeners()
        startVoiceDetection()
        startMetricsSimulation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: intent?.getStringExtra("ACTION")
        Log.d("MonitoringService", "onStartCommand: action=$action")
        
        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            "CONFIRM_SAFE" -> {
                handleConfirmSafe()
                return START_STICKY
            }
            "SIMULATE_ACCIDENT" -> {
                startEmergencyActivity()
                return START_STICKY
            }
            "EXECUTE_EMERGENCY" -> {
                executeEmergencyProtocol()
                return START_STICKY
            }
        }

        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        if (!isRunning) {
            isRunning = true
            startMonitoring()
            bleHelper.checkExistingConnection() 
        }

        return START_STICKY
    }

    private fun startVoiceDetection() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e("MonitoringService", "RECORD_AUDIO permission not granted. Voice detection disabled.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        if (isListening) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                if (isRunning) handler.postDelayed({ startVoiceDetection() }, 3000)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { phrase ->
                    val upper = phrase.uppercase()
                    Log.d("MonitoringService", "Heard: $upper")
                    
                    val voiceIntent = Intent("VOICE_DETECTION").apply {
                        `package` = packageName
                        putExtra("phrase", phrase)
                    }
                    sendBroadcast(voiceIntent)

                    // Trigger only if "HELP HELP" is detected (two times)
                    if (upper.contains("HELP HELP")) {
                        Log.e("MonitoringService", "EMERGENCY PHRASE DETECTED: $phrase")
                        startEmergencyActivity()
                    }
                }
                if (isRunning) startVoiceDetection()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { phrase ->
                    val upper = phrase.uppercase()
                    // Update dashboard in real-time
                    val voiceIntent = Intent("VOICE_DETECTION").apply {
                        `package` = packageName
                        putExtra("phrase", "$phrase...")
                    }
                    sendBroadcast(voiceIntent)
                    
                    if (upper.contains("HELP HELP")) {
                        startEmergencyActivity()
                    }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("MonitoringService", "SpeechRecognizer failed: ${e.message}")
        }
    }

    private fun startMetricsSimulation() {
        handler.post(object : Runnable {
            override fun run() {
                if (bleHelper.isDeviceConnected()) {
                    // Realistic fluctuations
                    val hr = (72..110).random()
                    val spo2 = (95..100).random()
                    val sys = 110 + (hr - 72) / 2 + (0..10).random()
                    val dia = 70 + (hr - 72) / 4 + (0..5).random()

                    val intent = Intent("METRICS_UPDATE").apply {
                        `package` = packageName
                        putExtra("hr", hr)
                        putExtra("spo2", spo2)
                        putExtra("bp", "$sys/$dia")
                    }
                    sendBroadcast(intent)
                }
                if (isRunning) handler.postDelayed(this, 3000)
            }
        })
    }

    private fun handleConfirmSafe() {
        val profile = profileManager.getProfile()
        val contacts = profileManager.getContacts()
        val name = profile["name"] ?: "User"
        val safeMessage = "✅ UPDATE: $name is SAFE. Previous accident alert can be ignored."
        
        val contactPairs = contacts.map { it.name to it.phone }
        smsHelper.broadcastEmergency(contactPairs, safeMessage)
        sensorHelper.resetImpactState()
        
        sendBroadcast(Intent("EMERGENCY_CANCELLED").apply { `package` = packageName })
    }

    private fun startMonitoring() {
        sensorHelper.start()
    }

    private fun setupSensorListeners() {
        sensorHelper.setListeners(
            onUpdate = { accel, gyro, gForce, status ->
                Log.d("MonitoringService", "Sensor Update: G=$gForce")
                val intent = Intent("SENSOR_UPDATE").apply {
                    `package` = packageName
                }
                intent.putExtra("accel", accel)
                intent.putExtra("gyro", gyro)
                intent.putExtra("gForce", gForce)
                intent.putExtra("status", status)
                sendBroadcast(intent)
            },
            onCrash = {
                startEmergencyActivity()
            }
        )
        sensorHelper.isWatchStable = { bleHelper.isDeviceConnected() }
    }

    private fun setupBleListeners() {
        bleHelper.onConnectionStateChange = { connected, name ->
            val intent = Intent("BLE_CONNECTION_UPDATE").apply {
                `package` = packageName
            }
            intent.putExtra("connected", connected)
            intent.putExtra("name", name)
            sendBroadcast(intent)
        }
        bleHelper.onRssiUpdate = { rssi, distance ->
            val intent = Intent("BLE_RSSI_UPDATE").apply {
                `package` = packageName
            }
            intent.putExtra("rssi", rssi)
            intent.putExtra("distance", distance)
            sendBroadcast(intent)
        }
    }

    private fun startEmergencyActivity() {
        val intent = Intent(this, EmergencyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
        sendBroadcast(Intent("EMERGENCY_START").apply { `package` = packageName })
    }

    private fun executeEmergencyProtocol() {
        Log.e("MonitoringService", "Emergency protocol executing")
        val profile = profileManager.getProfile()
        val contacts = profileManager.getContacts()
        val name = profile["name"] ?: "User"
        
        val hospitals = listOf(
            "Apollo Emergency" to "911", // Using 911 as placeholder for demo
            "City Hospital" to "102",
            "Fortis Emergency" to "108"
        )
        
        locationHelper.fetchLastLocation { location ->
            val mapsLink = location?.let { locationHelper.getGoogleMapsLink(it) } ?: "Location unavailable"
            
            val message = """
                🚨 POSSIBLE ACCIDENT DETECTED
                
                User: $name
                
                Blood Group: ${profile["bloodGroup"]}
                
                Medical Info: ${profile["allergies"]}
                
                Location:
                $mapsLink
                
                Status: 
                Possible accident detected. 
                Immediate assistance may be required.
                
                Sent by CraDet System
            """.trimIndent()

            val contactPairs = contacts.map { it.name to it.phone }.toMutableList()
            contactPairs.addAll(hospitals)

            smsHelper.broadcastEmergency(contactPairs, message)
            
            val intent = Intent("EMERGENCY_EXECUTED").apply {
                `package` = packageName
            }
            intent.putExtra("lat", location?.latitude)
            intent.putExtra("lon", location?.longitude)
            sendBroadcast(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        sensorHelper.stop()
        bleHelper.disconnect()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Monitoring", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 CraDet Monitoring Active")
            .setContentText("Sensors and emergency protection running")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
