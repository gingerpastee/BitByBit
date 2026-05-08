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
    private lateinit var countdownManager: CountdownManager
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

        countdownManager = CountdownManager(
            this,
            onTickCallback = { seconds ->
                val intent = Intent("COUNTDOWN_TICK")
                intent.putExtra("seconds", seconds)
                sendBroadcast(intent)
            },
            onFinishCallback = {
                executeEmergencyProtocol()
            }
        )

        setupSensorListeners()
        setupBleListeners()
        startVoiceDetection()
        startMetricsSimulation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: intent?.getStringExtra("ACTION")
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
                startEmergencyCountdown()
                return START_STICKY
            }
        }

        Log.d("MonitoringService", "Service Started")
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
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("MonitoringService", "Speech Recognition not available")
            return
        }
        
        if (isListening) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                // Restart on error if service is still running after a small delay
                if (isRunning) {
                    handler.postDelayed({ startVoiceDetection() }, 2000)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.forEach { phrase ->
                    val upper = phrase.uppercase()
                    if (upper.contains("HELP") || upper.contains("ACCIDENT") || upper.contains("EMERGENCY")) {
                        Log.e("MonitoringService", "Distress Voice Detected: $phrase")
                        val voiceIntent = Intent("VOICE_DETECTION")
                        voiceIntent.putExtra("phrase", phrase)
                        sendBroadcast(voiceIntent)
                        startEmergencyCountdown()
                    }
                }
                if (isRunning) startVoiceDetection()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { phrase ->
                    val upper = phrase.uppercase()
                    if (upper.contains("HELP")) {
                         Log.e("MonitoringService", "Partial Distress Detected: $phrase")
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
                    val rssi = (-75..-48).random()
                    val distance = (0.5 + (5.0 - 0.5) * Random().nextDouble())
                    val hr = (72..110).random()
                    val spo2 = (95..100).random()
                    val sys = (110..130).random()
                    val dia = (70..90).random()

                    val intent = Intent("METRICS_UPDATE").apply {
                        putExtra("rssi", rssi)
                        putExtra("distance", distance)
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
        
        countdownManager.stop()
        sensorHelper.resetImpactState()
    }

    private fun startMonitoring() {
        sensorHelper.start()
        Log.d("MonitoringService", "Sensors started automatically")
    }

    private fun setupSensorListeners() {
        sensorHelper.setListeners(
            onUpdate = { accel, gyro, gForce, status ->
                val intent = Intent("SENSOR_UPDATE")
                intent.putExtra("accel", accel)
                intent.putExtra("gyro", gyro)
                intent.putExtra("gForce", gForce)
                intent.putExtra("status", status)
                sendBroadcast(intent)
            },
            onCrash = {
                startEmergencyCountdown()
            }
        )
        sensorHelper.isWatchStable = {
            bleHelper.isDeviceConnected()
        }
    }

    private fun setupBleListeners() {
        bleHelper.onConnectionStateChange = { connected, name ->
            val intent = Intent("BLE_CONNECTION_UPDATE")
            intent.putExtra("connected", connected)
            intent.putExtra("name", name)
            sendBroadcast(intent)
        }
    }

    private fun startEmergencyCountdown() {
        val intent = Intent("EMERGENCY_START")
        sendBroadcast(intent)
        countdownManager.start()
    }

    private fun executeEmergencyProtocol() {
        Log.e("MonitoringService", "Emergency protocol executing")
        val profile = profileManager.getProfile()
        val contacts = profileManager.getContacts()
        val name = profile["name"] ?: "User"
        
        locationHelper.fetchLastLocation { location ->
            val mapsLink = location?.let { locationHelper.getGoogleMapsLink(it) } ?: "Location unavailable"
            
            val message = """
                🚨 EMERGENCY: Possible accident detected for $name
                
                👤 Medical Info:
                Blood: ${profile["bloodGroup"]}
                Allergies: ${profile["allergies"]}
                
                📍 LIVE LOCATION:
                $mapsLink
                
                ⌚ Watch: ${if (bleHelper.isDeviceConnected()) "Connected (FB BGS003)" else "Disconnected"}
                🎤 Voice: Distress phrase detected
                
                Sent by CraDet System
            """.trimIndent()

            val contactPairs = contacts.map { it.name to it.phone }
            smsHelper.broadcastEmergency(contactPairs, message)
            
            val intent = Intent("EMERGENCY_EXECUTED")
            sendBroadcast(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MonitoringService", "Service Destroyed")
        isRunning = false
        sensorHelper.stop()
        bleHelper.stopWatchdog()
        bleHelper.disconnect()
        countdownManager.release()
        speechRecognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "CraDet Monitoring Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 CraDet Monitoring Active")
            .setContentText("Sensors and emergency protection running")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Monitoring", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
