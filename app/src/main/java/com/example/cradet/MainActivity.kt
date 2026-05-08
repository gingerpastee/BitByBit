package com.example.cradet

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cradet.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorHelper: SensorHelper
    private var isCrashDetected = false
    private var peakGForce = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorHelper = SensorHelper(this)

        setupListeners()
        initStatus()
    }

    private fun initStatus() {
        if (!sensorHelper.hasGyroscope()) {
            binding.tvGyroStatus.text = "Gyroscope not available on this device"
            binding.tvGyroStatus.setTextColor(ContextCompat.getColor(this, R.color.status_crash))
        } else {
            binding.tvGyroStatus.text = "Gyroscope Active"
            binding.tvGyroStatus.setTextColor(ContextCompat.getColor(this, R.color.status_safe))
        }
        updateUI(isMonitoring = false)
    }

    private fun setupListeners() {
        binding.btnStart.setOnClickListener {
            sensorHelper.startMonitoring()
            updateUI(isMonitoring = true)
            Toast.makeText(this, "Monitoring Started", Toast.LENGTH_SHORT).show()
        }

        binding.btnStop.setOnClickListener {
            sensorHelper.stopMonitoring()
            updateUI(isMonitoring = false)
            Toast.makeText(this, "Monitoring Paused", Toast.LENGTH_SHORT).show()
        }

        binding.btnReset.setOnClickListener {
            isCrashDetected = false
            peakGForce = 1.0f
            binding.tvPeakG.text = String.format(Locale.US, "Peak recorded: %.2f G", peakGForce)
            binding.tvDetectionMsg.text = "No threats detected"
            binding.tvDetectionMsg.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            binding.cardStatus.strokeWidth = 0
            updateUI(isMonitoring = sensorHelper.isMonitoring)
            Toast.makeText(this, "Metrics Reset", Toast.LENGTH_SHORT).show()
        }

        sensorHelper.setListeners(
            onDataChanged = { accel, gyro, gForce ->
                runOnUiThread {
                    // Update Accelerometer
                    binding.tvAccelX.text = String.format(Locale.US, "X: %.2f", accel[0])
                    binding.tvAccelY.text = String.format(Locale.US, "Y: %.2f", accel[1])
                    binding.tvAccelZ.text = String.format(Locale.US, "Z: %.2f", accel[2])

                    // Update Gyroscope
                    binding.tvGyroX.text = String.format(Locale.US, "Rot X: %.2f", gyro[0])
                    binding.tvGyroY.text = String.format(Locale.US, "Rot Y: %.2f", gyro[1])
                    binding.tvGyroZ.text = String.format(Locale.US, "Rot Z: %.2f", gyro[2])

                    // Update G-Force
                    binding.tvCurrentG.text = String.format(Locale.US, "%.2f", gForce)
                    if (gForce > peakGForce) {
                        peakGForce = gForce
                        binding.tvPeakG.text = String.format(Locale.US, "Peak recorded: %.2f G", peakGForce)
                    }
                }
            },
            onCrash = {
                if (!isCrashDetected) {
                    isCrashDetected = true
                    runOnUiThread {
                        handleCrash()
                    }
                }
            },
            onHighG = { gValue ->
                runOnUiThread {
                    Log.w("CraDet", "High G-force detected: $gValue")
                    Toast.makeText(this, "High G detected: ${String.format("%.1f", gValue)}G", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun handleCrash() {
        binding.tvMonitoringStatus.text = "CRASH DETECTED"
        binding.tvMonitoringStatus.setTextColor(ContextCompat.getColor(this, R.color.status_crash))
        binding.tvDetectionMsg.text = "Possible Crash Detected! Stability verification failed."
        binding.tvDetectionMsg.setTextColor(ContextCompat.getColor(this, R.color.status_crash))
        
        binding.cardStatus.strokeColor = ContextCompat.getColor(this, R.color.status_crash)
        binding.cardStatus.strokeWidth = 6

        Toast.makeText(this, "🚨 EMERGENCY: Crash Detected!", Toast.LENGTH_LONG).show()
    }

    private fun updateUI(isMonitoring: Boolean) {
        if (isCrashDetected) {
            binding.tvMonitoringStatus.text = "CRASH RECORDED"
            binding.tvMonitoringStatus.setTextColor(ContextCompat.getColor(this, R.color.status_crash))
        } else if (isMonitoring) {
            binding.tvMonitoringStatus.text = "Monitoring Active"
            binding.tvMonitoringStatus.setTextColor(ContextCompat.getColor(this, R.color.status_monitoring))
            binding.tvDetectionMsg.text = "Analyzing motion..."
        } else {
            binding.tvMonitoringStatus.text = "System Ready"
            binding.tvMonitoringStatus.setTextColor(ContextCompat.getColor(this, R.color.status_safe))
        }

        binding.btnStart.isEnabled = !isMonitoring
        binding.btnStop.isEnabled = isMonitoring
    }

    override fun onPause() {
        super.onPause()
        sensorHelper.stopMonitoring()
    }
}
