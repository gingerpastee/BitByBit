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
    private lateinit var locationHelper: LocationHelper

    private var peakGForce = 1.0f
    private val PERMISSION_REQUEST_CODE = 100

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "SENSOR_UPDATE" -> {
                    val accel = intent.getFloatArrayExtra("accel") ?: floatArrayOf(0f, 0f, 0f)
                    val gForce = intent.getFloatExtra("gForce", 1.0f)
                    updateSensorUI(accel, gForce)
                }
                "BLE_CONNECTION_UPDATE" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    val name = intent.getStringExtra("name")
                    updateBleUI(connected, name)
                }
                "METRICS_UPDATE" -> {
                    val rssi = intent.getIntExtra("rssi", 0)
                    val distance = intent.getDoubleExtra("distance", 0.0)
                    val hr = intent.getIntExtra("hr", 0)
                    val spo2 = intent.getIntExtra("spo2", 0)
                    val bp = intent.getStringExtra("bp") ?: "--/--"
                    
                    updateRssiUI(rssi, distance)
                    updateVitalsUI(hr, spo2, bp)
                }
                "VOICE_DETECTION" -> {
                    val phrase = intent.getStringExtra("phrase") ?: "HELP"
                    binding.tvVoiceStatus.text = phrase
                    Toast.makeText(this@MainActivity, "🎤 Distress Detected: $phrase", Toast.LENGTH_LONG).show()
                }
                "COUNTDOWN_TICK" -> {
                    val seconds = intent.getIntExtra("seconds", 20)
                    binding.includeCountdown.tvTimer.text = seconds.toString()
                }
                "EMERGENCY_START" -> {
                    showOverlay(binding.includeCountdown.root)
                    binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.status_crash)
                }
                "EMERGENCY_EXECUTED" -> {
                    showOverlay(binding.includeEmergencyActive.root)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fix for potential "BAD_DECRYPT" / "RegistryDataStore" errors
        DataStoreFixer.cleanCorruptedRegistries(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)
        authManager = AuthManager(this)
        locationHelper = LocationHelper(this)

        checkPermissions()
        setupUI()
        loadProfileData()
        startMonitoringService()
    }

    private fun startMonitoringService() {
        val serviceIntent = Intent(this, MonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Log.d("MainActivity", "Foreground service started for auto-monitoring")
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.VIBRATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupUI() {
        binding.btnResetMetrics.setOnClickListener {
            peakGForce = 1.0f
            binding.tvPeakG.text = String.format(Locale.US, "Peak: %.2f", peakGForce)
            Toast.makeText(this, "Analytics Reset", Toast.LENGTH_SHORT).show()
        }

        binding.btnEditProfile.setOnClickListener {
            showOverlay(binding.includeProfileEditor.root)
        }

        binding.btnLogout.setOnClickListener {
            authManager.logout()
            val stopIntent = Intent(this, MonitoringService::class.java)
            stopService(stopIntent)
            val loginIntent = Intent(this, LoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(loginIntent)
            finish()
        }

        binding.includeCountdown.btnCancelAlert.setOnClickListener {
            val stopIntent = Intent(this, MonitoringService::class.java)
            stopService(stopIntent)
            startMonitoringService() // Restart for fresh state
            hideOverlay()
            binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_monitoring)
            Toast.makeText(this, "Emergency Alert Cancelled", Toast.LENGTH_SHORT).show()
        }

        binding.includeEmergencyActive.btnImSafe.setOnClickListener {
            confirmUserSafe()
        }

        binding.includeProfileEditor.btnSaveProfile.setOnClickListener {
            saveProfileData()
        }
        binding.includeProfileEditor.btnCancelProfile.setOnClickListener {
            hideOverlay()
        }

        binding.btnSimulateAccident.setOnClickListener {
            Log.d("MainActivity", "Simulate Accident button clicked.")
            Toast.makeText(this, "🚨 Simulation: Accident Detected!", Toast.LENGTH_SHORT).show()
            val simIntent = Intent(this, MonitoringService::class.java).apply {
                putExtra("ACTION", "SIMULATE_ACCIDENT")
            }
            startService(simIntent)
        }
    }

    private fun updateSensorUI(accel: FloatArray, gForce: Float) {
        binding.tvAccX.text = String.format(Locale.US, "X: %.2f", accel[0])
        binding.tvAccY.text = String.format(Locale.US, "Y: %.2f", accel[1])
        binding.tvAccZ.text = String.format(Locale.US, "Z: %.2f", accel[2])
        binding.tvGValue.text = String.format(Locale.US, "%.2f G", gForce)

        if (gForce > peakGForce) {
            peakGForce = gForce
            binding.tvPeakG.text = String.format(Locale.US, "Peak: %.2f", peakGForce)
        }
    }

    private fun updateVitalsUI(hr: Int, spo2: Int, bp: String) {
        binding.tvHeartRate.text = hr.toString()
        binding.tvSpo2.text = "$spo2%"
        binding.tvBp.text = bp
    }

    private fun updateBleUI(connected: Boolean, name: String?) {
        if (connected) {
            binding.tvWatchName.text = "Connected: ${name ?: "Watch"}"
            binding.tvWatchStatus.text = "Watch Linked [56:75:DE:1D:5C:2B]"
            binding.ivWatchIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.status_safe)
            binding.layoutWatchDetails.visibility = View.VISIBLE
            binding.tvVitalsLabel.visibility = View.VISIBLE
            binding.gridVitals.visibility = View.VISIBLE
        } else {
            binding.tvWatchName.text = "Watch Not Connected"
            binding.tvWatchStatus.text = "Searching for FB BGS003..."
            binding.ivWatchIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.status_crash)
            binding.layoutWatchDetails.visibility = View.GONE
            binding.tvVitalsLabel.visibility = View.GONE
            binding.gridVitals.visibility = View.GONE
        }
    }

    private fun updateRssiUI(rssi: Int, distance: Double) {
        binding.tvRssi.text = "$rssi dBm"
        binding.tvDistance.text = String.format(Locale.US, "%.1f meters", distance)
    }

    private fun showOverlay(view: View) {
        binding.overlayContainer.visibility = View.VISIBLE
        binding.includeCountdown.root.visibility = View.GONE
        binding.includeEmergencyActive.root.visibility = View.GONE
        binding.includeProfileEditor.root.visibility = View.GONE
        view.visibility = View.VISIBLE
    }

    private fun hideOverlay() {
        binding.overlayContainer.visibility = View.GONE
    }

    private fun loadProfileData() {
        val profile = profileManager.getProfile()
        val contacts = profileManager.getContacts()
        val name = profile["name"] ?: "User"
        binding.tvGreeting.text = "Hello, $name"
        
        if (contacts.isNotEmpty()) {
            val contactList = contacts.joinToString("\n") { "• ${it.name}: ${it.phone} (${it.relationship})" }
            binding.tvContactInfo.text = contactList
        } else {
            binding.tvContactInfo.text = "No emergency contacts found."
        }

        binding.includeProfileEditor.etName.setText(name)
        binding.includeProfileEditor.etBlood.setText(profile["bloodGroup"])
        binding.includeProfileEditor.etAllergies.setText(profile["allergies"])
        
        val contactFields = listOf(
            Triple(binding.includeProfileEditor.etContactName1, binding.includeProfileEditor.etContactPhone1, binding.includeProfileEditor.etContactRel1),
            Triple(binding.includeProfileEditor.etContactName2, binding.includeProfileEditor.etContactPhone2, binding.includeProfileEditor.etContactRel2),
            Triple(binding.includeProfileEditor.etContactName3, binding.includeProfileEditor.etContactPhone3, binding.includeProfileEditor.etContactRel3),
            Triple(binding.includeProfileEditor.etContactName4, binding.includeProfileEditor.etContactPhone4, binding.includeProfileEditor.etContactRel4),
            Triple(binding.includeProfileEditor.etContactName5, binding.includeProfileEditor.etContactPhone5, binding.includeProfileEditor.etContactRel5)
        )

        contacts.forEachIndexed { index, contact ->
            if (index < contactFields.size) {
                contactFields[index].first.setText(contact.name)
                contactFields[index].second.setText(contact.phone)
                contactFields[index].third.setText(contact.relationship)
            }
        }
    }

    private fun saveProfileData() {
        val name = binding.includeProfileEditor.etName.text.toString().trim()
        val blood = binding.includeProfileEditor.etBlood.text.toString().trim()
        val allergies = binding.includeProfileEditor.etAllergies.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        val contacts = mutableListOf<ProfileManager.Contact>()
        val contactFields = listOf(
            Triple(binding.includeProfileEditor.etContactName1, binding.includeProfileEditor.etContactPhone1, binding.includeProfileEditor.etContactRel1),
            Triple(binding.includeProfileEditor.etContactName2, binding.includeProfileEditor.etContactPhone2, binding.includeProfileEditor.etContactRel2),
            Triple(binding.includeProfileEditor.etContactName3, binding.includeProfileEditor.etContactPhone3, binding.includeProfileEditor.etContactRel3),
            Triple(binding.includeProfileEditor.etContactName4, binding.includeProfileEditor.etContactPhone4, binding.includeProfileEditor.etContactRel4),
            Triple(binding.includeProfileEditor.etContactName5, binding.includeProfileEditor.etContactPhone5, binding.includeProfileEditor.etContactRel5)
        )

        contactFields.forEach { fields ->
            val cName = fields.first.text.toString().trim()
            val cPhone = fields.second.text.toString().trim()
            val cRel = fields.third.text.toString().trim()
            if (cName.isNotEmpty() && cPhone.isNotEmpty()) {
                contacts.add(ProfileManager.Contact(cName, cPhone, cRel))
            }
        }

        val email = profileManager.getProfile()["email"] ?: ""
        profileManager.saveProfile(name, email, blood, allergies)
        profileManager.saveContacts(contacts)
        
        loadProfileData()
        hideOverlay()
        Toast.makeText(this, "Profile Saved", Toast.LENGTH_SHORT).show()
    }

    private fun confirmUserSafe() {
        val intent = Intent(this, MonitoringService::class.java)
        intent.putExtra("ACTION", "CONFIRM_SAFE")
        startService(intent)
        
        hideOverlay()
        Toast.makeText(this, "Safe confirmation sent to contacts", Toast.LENGTH_LONG).show()
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_safe)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction("SENSOR_UPDATE")
            addAction("BLE_CONNECTION_UPDATE")
            addAction("BLE_RSSI_UPDATE")
            addAction("METRICS_UPDATE")
            addAction("VOICE_DETECTION")
            addAction("COUNTDOWN_TICK")
            addAction("EMERGENCY_START")
            addAction("EMERGENCY_EXECUTED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }
}

