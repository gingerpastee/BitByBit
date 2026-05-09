package com.example.cradet

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cradet.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var profileManager: ProfileManager
    private lateinit var authManager: AuthManager

    private var peakGForce = 1.0f
    private val PERMISSION_REQUEST_CODE = 101

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Broadcast received: ${intent?.action}")
            when (intent?.action) {
                "SENSOR_UPDATE" -> {
                    val accel = intent.getFloatArrayExtra("accel") ?: floatArrayOf(0f, 0f, 0f)
                    val gyro = intent.getFloatArrayExtra("gyro") ?: floatArrayOf(0f, 0f, 0f)
                    val gForce = intent.getFloatExtra("gForce", 1.0f)
                    val status = intent.getStringExtra("status") ?: ""
                    updateSensorUI(accel, gyro, gForce, status)
                }
                "BLE_CONNECTION_UPDATE" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    val name = intent.getStringExtra("name")
                    updateBleStatusUI(connected, name)
                }
                "BLE_RSSI_UPDATE" -> {
                    val rssi = intent.getIntExtra("rssi", 0)
                    val distance = intent.getDoubleExtra("distance", 0.0)
                    updateRssiUI(rssi, distance)
                }
                "METRICS_UPDATE" -> {
                    val hr = intent.getIntExtra("hr", 0)
                    val spo2 = intent.getIntExtra("spo2", 0)
                    val bp = intent.getStringExtra("bp") ?: "--/--"
                    updateVitalsUI(hr, spo2, bp)
                }
                "VOICE_DETECTION" -> {
                    val phrase = intent.getStringExtra("phrase") ?: ""
                    if (phrase.isNotEmpty()) {
                        binding.tvVoiceStatus.text = "🎤 HEARD: ${phrase.uppercase()}"
                        if (phrase.uppercase().contains("HELP HELP")) {
                            binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_crash))
                        } else {
                            binding.tvVoiceStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.black))
                        }
                    }
                }
                "EMERGENCY_EXECUTED" -> {
                    showEmergencyActiveOverlay()
                }
                "EMERGENCY_CANCELLED" -> {
                    hideOverlay()
                    binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.status_safe)
                    binding.tvSystemStatus.text = "🟢 Monitoring Active"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fix potential data corruption issues
        DataStoreFixer.cleanCorruptedRegistries(this)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)
        authManager = AuthManager(this)

        checkPermissions()
        setupUI()
        loadProfileData()
        
        // Start monitoring automatically
        startMonitoringService()
    }

    private fun startMonitoringService() {
        val intent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.VIBRATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val audioIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
            if (audioIndex != -1 && grantResults[audioIndex] == PackageManager.PERMISSION_GRANTED) {
                // Restart service to enable voice detection
                startMonitoringService()
            }
        }
    }

    private fun setupUI() {
        binding.btnSimulateAccident.setOnClickListener {
            val intent = Intent(this, MonitoringService::class.java)
            intent.putExtra("ACTION", "SIMULATE_ACCIDENT")
            startService(intent)
        }

        binding.btnViewHospitals.setOnClickListener {
            startActivity(Intent(this, NearbyHospitalsActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            authManager.logout()
            stopService(Intent(this, MonitoringService::class.java))
            val loginIntent = Intent(this, LoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
        }

        binding.includeEmergencyActive.btnImSafe.setOnClickListener {
            val intent = Intent(this, MonitoringService::class.java)
            intent.putExtra("ACTION", "CONFIRM_SAFE")
            startService(intent)
        }
        
        binding.btnManageContacts.setOnClickListener {
            showProfileEditor()
        }

        binding.tvGreeting.setOnLongClickListener {
            showProfileEditor()
            true
        }

        binding.includeProfileEditor.btnSaveProfile.setOnClickListener {
            saveProfileData()
        }
        binding.includeProfileEditor.btnCancelProfile.setOnClickListener {
            hideOverlay()
        }
    }

    private fun updateSensorUI(accel: FloatArray, gyro: FloatArray, gForce: Float, status: String) {
        binding.tvGValue.text = String.format(Locale.US, "%.2f G", gForce)
        if (gForce > peakGForce) {
            peakGForce = gForce
            binding.tvPeakG.text = String.format(Locale.US, "Peak: %.2f", peakGForce)
        }
        
        binding.tvAccelValues.text = String.format(Locale.US, "X: %.2f  Y: %.2f  Z: %.2f", accel[0], accel[1], accel[2])
        
        if (status.contains("Unavailable")) {
            binding.tvGyroValues.text = "❌ Gyroscope Not Available"
            binding.tvGyroValues.setTextColor(ContextCompat.getColor(this, R.color.status_crash))
        } else {
            binding.tvGyroValues.text = String.format(Locale.US, "X: %.2f  Y: %.2f  Z: %.2f", gyro[0], gyro[1], gyro[2])
            binding.tvGyroValues.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun updateVitalsUI(hr: Int, spo2: Int, bp: String) {
        binding.tvHeartRate.text = hr.toString()
        binding.tvSpo2.text = "$spo2%"
        binding.tvBp.text = bp
    }

    private fun updateBleStatusUI(connected: Boolean, name: String?) {
        if (connected) {
            binding.tvWatchName.text = "⌚ ${name ?: "Watch"} Connected"
            binding.tvWatchStatus.text = "System Link Established"
            binding.ivWatchIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.primary)
        } else {
            binding.tvWatchName.text = "Watch Disconnected"
            binding.tvWatchStatus.text = "Searching for Link..."
            binding.ivWatchIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
        }
    }

    private fun updateRssiUI(rssi: Int, distance: Double) {
        binding.tvRssi.text = "$rssi dBm"
        binding.tvDistance.text = String.format(Locale.US, "%.1f meters", distance)
    }

    private fun showEmergencyActiveOverlay() {
        binding.overlayContainer.visibility = View.VISIBLE
        binding.includeEmergencyActive.root.visibility = View.VISIBLE
        binding.includeProfileEditor.root.visibility = View.GONE
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_crash)
        binding.tvSystemStatus.text = "🔴 EMERGENCY ACTIVE"
    }

    private fun showProfileEditor() {
        binding.overlayContainer.visibility = View.VISIBLE
        binding.includeEmergencyActive.root.visibility = View.GONE
        binding.includeProfileEditor.root.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        binding.overlayContainer.visibility = View.GONE
    }

    private fun loadProfileData() {
        val profile = profileManager.getProfile()
        val name = profile["name"] ?: "User"
        binding.tvGreeting.text = "Hello, $name"

        binding.includeProfileEditor.etName.setText(name)
        binding.includeProfileEditor.etBlood.setText(profile["bloodGroup"])
        binding.includeProfileEditor.etAllergies.setText(profile["allergies"])

        val contacts = profileManager.getContacts()
        if (contacts.size >= 1) {
            binding.includeProfileEditor.etContactName1.setText(contacts[0].name)
            binding.includeProfileEditor.etContactPhone1.setText(contacts[0].phone)
            binding.includeProfileEditor.etContactRel1.setText(contacts[0].relationship)
        }
        if (contacts.size >= 2) {
            binding.includeProfileEditor.etContactName2.setText(contacts[1].name)
            binding.includeProfileEditor.etContactPhone2.setText(contacts[1].phone)
            binding.includeProfileEditor.etContactRel2.setText(contacts[1].relationship)
        }
        if (contacts.size >= 3) {
            binding.includeProfileEditor.etContactName3.setText(contacts[2].name)
            binding.includeProfileEditor.etContactPhone3.setText(contacts[2].phone)
            binding.includeProfileEditor.etContactRel3.setText(contacts[2].relationship)
        }
        if (contacts.size >= 4) {
            binding.includeProfileEditor.etContactName4.setText(contacts[3].name)
            binding.includeProfileEditor.etContactPhone4.setText(contacts[3].phone)
            binding.includeProfileEditor.etContactRel4.setText(contacts[3].relationship)
        }
        if (contacts.size >= 5) {
            binding.includeProfileEditor.etContactName5.setText(contacts[4].name)
            binding.includeProfileEditor.etContactPhone5.setText(contacts[4].phone)
            binding.includeProfileEditor.etContactRel5.setText(contacts[4].relationship)
        }
    }

    private fun saveProfileData() {
        val name = binding.includeProfileEditor.etName.text.toString().trim()
        val blood = binding.includeProfileEditor.etBlood.text.toString().trim()
        val allergies = binding.includeProfileEditor.etAllergies.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show()
            return
        }

        profileManager.saveProfile(name, profileManager.getProfile()["email"] ?: "", blood, allergies)

        val contacts = mutableListOf<ProfileManager.Contact>()
        
        fun addContact(n: String, p: String, r: String) {
            if (n.isNotEmpty() && p.isNotEmpty()) {
                contacts.add(ProfileManager.Contact(n, p, r))
            }
        }

        addContact(
            binding.includeProfileEditor.etContactName1.text.toString(),
            binding.includeProfileEditor.etContactPhone1.text.toString(),
            binding.includeProfileEditor.etContactRel1.text.toString()
        )
        addContact(
            binding.includeProfileEditor.etContactName2.text.toString(),
            binding.includeProfileEditor.etContactPhone2.text.toString(),
            binding.includeProfileEditor.etContactRel2.text.toString()
        )
        addContact(
            binding.includeProfileEditor.etContactName3.text.toString(),
            binding.includeProfileEditor.etContactPhone3.text.toString(),
            binding.includeProfileEditor.etContactRel3.text.toString()
        )
        addContact(
            binding.includeProfileEditor.etContactName4.text.toString(),
            binding.includeProfileEditor.etContactPhone4.text.toString(),
            binding.includeProfileEditor.etContactRel4.text.toString()
        )
        addContact(
            binding.includeProfileEditor.etContactName5.text.toString(),
            binding.includeProfileEditor.etContactPhone5.text.toString(),
            binding.includeProfileEditor.etContactRel5.text.toString()
        )

        profileManager.saveContacts(contacts)

        loadProfileData()
        hideOverlay()
        Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction("SENSOR_UPDATE")
            addAction("BLE_CONNECTION_UPDATE")
            addAction("BLE_RSSI_UPDATE")
            addAction("METRICS_UPDATE")
            addAction("VOICE_DETECTION")
            addAction("EMERGENCY_START")
            addAction("EMERGENCY_EXECUTED")
            addAction("EMERGENCY_CANCELLED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }
}
