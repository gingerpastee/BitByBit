package com.example.cradet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
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
    private lateinit var sensorHelper: SensorHelper
    private lateinit var countdownManager: CountdownManager
    private lateinit var profileManager: ProfileManager
    private lateinit var authManager: AuthManager
    private lateinit var locationHelper: LocationHelper
    private lateinit var smsHelper: SmsHelper

    private var isMonitoring = false
    private var isEmergencyMode = false
    private var peakGForce = 1.0f
    private var lastLocation: Location? = null

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Helpers
        sensorHelper = SensorHelper(this)
        profileManager = ProfileManager(this)
        authManager = AuthManager(this)
        locationHelper = LocationHelper(this)
        smsHelper = SmsHelper(this)
        
        countdownManager = CountdownManager(
            onTickCallback = { seconds ->
                runOnUiThread {
                    binding.includeCountdown.tvTimer.text = seconds.toString()
                }
            },
            onFinishCallback = {
                runOnUiThread {
                    executeEmergencyProtocol()
                }
            }
        )

        checkPermissions()
        setupUI()
        setupSensorListeners()
        loadProfileData()
        refreshLocation()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupUI() {
        // Main Dashboard Controls
        binding.btnToggle.setOnClickListener {
            if (isMonitoring) stopMonitoring() else startMonitoring()
        }

        binding.btnResetMetrics.setOnClickListener {
            peakGForce = 1.0f
            binding.tvPeakG.text = String.format(Locale.US, "Peak: %.2f", peakGForce)
            Toast.makeText(this, "Analytics Reset", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "Metrics reset by user")
        }

        binding.btnEditProfile.setOnClickListener {
            showOverlay(binding.includeProfileEditor.root)
        }

        binding.btnLogout.setOnClickListener {
            Log.d("MainActivity", "User logging out")
            authManager.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Countdown Controls
        binding.includeCountdown.btnCancelAlert.setOnClickListener {
            cancelEmergencyCountdown()
        }

        // Post-Alert Controls
        binding.includeEmergencyActive.btnImSafe.setOnClickListener {
            confirmUserSafe()
        }

        // Profile Editor Controls
        binding.includeProfileEditor.btnSaveProfile.setOnClickListener {
            saveProfileData()
        }
        binding.includeProfileEditor.btnCancelProfile.setOnClickListener {
            hideOverlay()
        }
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

        binding.tvGreeting.text = getString(R.string.greeting_format, profile["name"])
        
        if (contacts.isNotEmpty()) {
            binding.tvContactInfo.text = "Primary: ${contacts[0].first} (${contacts[0].second})"
        } else {
            binding.tvContactInfo.text = getString(R.string.no_contacts)
        }

        // Pre-fill editor
        binding.includeProfileEditor.etName.setText(profile["name"])
        binding.includeProfileEditor.etBlood.setText(profile["bloodGroup"])
        binding.includeProfileEditor.etAllergies.setText(profile["allergies"])
        
        if (contacts.isNotEmpty()) {
            binding.includeProfileEditor.etContactName.setText(contacts[0].first)
            binding.includeProfileEditor.etContactPhone.setText(contacts[0].second)
        }
    }

    private fun saveProfileData() {
        val name = binding.includeProfileEditor.etName.text.toString().trim()
        val blood = binding.includeProfileEditor.etBlood.text.toString().trim()
        val allergies = binding.includeProfileEditor.etAllergies.text.toString().trim()
        val cName = binding.includeProfileEditor.etContactName.text.toString().trim()
        val cPhone = binding.includeProfileEditor.etContactPhone.text.toString().trim()

        if (name.isEmpty() || cPhone.isEmpty()) {
            Toast.makeText(this, "Name and Contact Phone are required", Toast.LENGTH_SHORT).show()
            return
        }

        profileManager.saveProfile(name, blood, allergies)
        profileManager.saveContacts(listOf(cName to cPhone))
        
        loadProfileData()
        hideOverlay()
        Toast.makeText(this, "Profile Saved Successfully", Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "Profile updated for: $name")
    }

    private fun startMonitoring() {
        if (isEmergencyMode) return
        isMonitoring = true
        sensorHelper.start()
        refreshLocation()
        binding.btnToggle.text = getString(R.string.btn_stop)
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_monitoring)
        binding.tvSystemStatus.text = "Monitoring Active"
        binding.tvStatusDesc.text = getString(R.string.monitoring_desc)
        Log.d("MainActivity", "Monitoring STARTED")
    }

    private fun stopMonitoring() {
        isMonitoring = false
        sensorHelper.stop()
        binding.btnToggle.text = getString(R.string.btn_start)
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_safe)
        binding.tvSystemStatus.text = getString(R.string.system_ready)
        binding.tvStatusDesc.text = getString(R.string.idle_desc)
        Log.d("MainActivity", "Monitoring STOPPED")
    }

    private fun setupSensorListeners() {
        sensorHelper.setListeners(
            onUpdate = { accel, _, gForce, _ ->
                runOnUiThread {
                    binding.tvAccX.text = String.format(Locale.US, "X: %.2f", accel[0])
                    binding.tvAccY.text = String.format(Locale.US, "Y: %.2f", accel[1])
                    binding.tvAccZ.text = String.format(Locale.US, "Z: %.2f", accel[2])
                    binding.tvGValue.text = String.format(Locale.US, "%.2f G", gForce)

                    if (gForce > peakGForce) {
                        peakGForce = gForce
                        binding.tvPeakG.text = String.format(Locale.US, "Peak: %.2f", peakGForce)
                    }
                }
            },
            onCrash = {
                runOnUiThread {
                    startEmergencyCountdown()
                }
            }
        )
    }

    private fun refreshLocation() {
        locationHelper.fetchLastLocation { location ->
            runOnUiThread {
                lastLocation = location
                if (location != null) {
                    binding.tvLocationCoords.text = String.format(
                        Locale.US, "Lat: %.5f, Long: %.5f",
                        location.latitude, location.longitude
                    )
                    Log.d("MainActivity", "Location updated: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    private fun startEmergencyCountdown() {
        if (isEmergencyMode) return
        Log.w("MainActivity", "CRASH DETECTED! Starting countdown.")
        showOverlay(binding.includeCountdown.root)
        binding.tvSystemStatus.text = getString(R.string.impact_detected)
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_crash)
        countdownManager.start()
        refreshLocation() 
    }

    private fun cancelEmergencyCountdown() {
        Log.d("MainActivity", "Emergency countdown cancelled by user.")
        countdownManager.stop()
        sensorHelper.resetImpactState()
        hideOverlay()
        binding.tvSystemStatus.text = "Monitoring Resumed"
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_monitoring)
    }

    private fun executeEmergencyProtocol() {
        Log.e("MainActivity", "Countdown finished. EXECUTING EMERGENCY PROTOCOL.")
        isEmergencyMode = true
        isMonitoring = false
        sensorHelper.stop()
        
        showOverlay(binding.includeEmergencyActive.root)
        
        val profile = profileManager.getProfile()
        val contacts = profileManager.getContacts()
        val name = profile["name"] ?: "Unknown User"
        
        val mapsLink = lastLocation?.let { locationHelper.getGoogleMapsLink(it) } ?: "⚠️ Location unavailable"
        
        val message = """
            🚨 CRADET EMERGENCY ALERT 🚨
            Possible accident detected for $name.
            
            ❤️ MEDICAL INFO:
            Blood Group: ${profile["bloodGroup"]}
            Allergies: ${profile["allergies"]}
            
            📍 LIVE LOCATION:
            $mapsLink
            
            Immediate assistance may be required.
        """.trimIndent()

        Log.d("MainActivity", "Sending emergency SMS...")
        smsHelper.broadcastEmergency(contacts, message)
        Toast.makeText(this, "EMERGENCY ALERT SENT!", Toast.LENGTH_LONG).show()
    }

    private fun confirmUserSafe() {
        Log.d("MainActivity", "User confirmed SAFE.")
        val profile = profileManager.getProfile()
        val contacts = profileManager.getContacts()
        val name = profile["name"] ?: "User"
        
        val safeMessage = """
            ✅ UPDATE:
            $name is SAFE.
            
            Previous accident alert can be ignored.
        """.trimIndent()
        
        Log.d("MainActivity", "Sending safety confirmation SMS...")
        smsHelper.broadcastEmergency(contacts, safeMessage)
        
        isEmergencyMode = false
        hideOverlay()
        stopMonitoring()
        Toast.makeText(this, "Safety confirmed. Contacts notified.", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("MainActivity", "All permissions granted")
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("MainActivity", "Permissions denied")
                Toast.makeText(this, "Permissions Denied. Emergency features will not work.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
        if (isMonitoring && !isEmergencyMode) sensorHelper.start()
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause")
        sensorHelper.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy")
        countdownManager.release()
    }
}
