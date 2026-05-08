package com.example.cradet

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.cradet.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorHelper: SensorHelper
    private lateinit var countdownManager: CountdownManager
    
    private var isMonitoring = false
    private var peakGForce = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorHelper = SensorHelper(this)
        countdownManager = CountdownManager(
            onTick = { seconds ->
                binding.tvTimer.text = seconds.toString()
            },
            onFinish = {
                triggerEmergency()
            }
        )

        setupUI()
        setupSensorListeners()
    }

    private fun setupUI() {
        binding.tvSensorStatus.text = sensorHelper.getSensorStatus()

        binding.btnToggle.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }

        binding.btnResetMetrics.setOnClickListener {
            peakGForce = 1.0f
            binding.tvPeakG.text = getString(R.string.peak_impact, peakGForce)
            Toast.makeText(this, "Metrics reset", Toast.LENGTH_SHORT).show()
        }

        binding.btnCancelAlert.setOnClickListener {
            cancelEmergency()
        }
    }

    private fun startMonitoring() {
        isMonitoring = true
        sensorHelper.start()
        binding.btnToggle.text = getString(R.string.btn_stop)
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_monitoring)
        binding.tvSystemStatus.text = getString(R.string.status_active)
        Toast.makeText(this, "Safety Monitoring Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        isMonitoring = false
        sensorHelper.stop()
        binding.btnToggle.text = getString(R.string.btn_start)
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_safe)
        binding.tvSystemStatus.text = getString(R.string.status_idle)
    }

    private fun setupSensorListeners() {
        sensorHelper.setListeners(
            onUpdate = { accel, gyro, gForce, _ ->
                runOnUiThread {
                    // Update raw values
                    binding.tvAccX.text = String.format(Locale.US, "X: %.2f", accel[0])
                    binding.tvAccY.text = String.format(Locale.US, "Y: %.2f", accel[1])
                    binding.tvAccZ.text = String.format(Locale.US, "Z: %.2f", accel[2])

                    binding.tvGyroX.text = String.format(Locale.US, "X: %.2f", gyro[0])
                    binding.tvGyroY.text = String.format(Locale.US, "Y: %.2f", gyro[1])
                    binding.tvGyroZ.text = String.format(Locale.US, "Z: %.2f", gyro[2])

                    // Update G-Force
                    binding.tvGValue.text = String.format(Locale.US, "%.2f G", gForce)
                    if (gForce > peakGForce) {
                        peakGForce = gForce
                        binding.tvPeakG.text = getString(R.string.peak_impact, peakGForce)
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

    private fun startEmergencyCountdown() {
        // Switch to countdown UI
        binding.layoutCountdown.visibility = View.VISIBLE
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_crash)
        binding.tvSystemStatus.text = getString(R.string.status_crash)
        
        countdownManager.start()
    }

    private fun cancelEmergency() {
        countdownManager.stop()
        sensorHelper.resetImpactState()
        binding.layoutCountdown.visibility = View.GONE
        binding.tvSystemStatus.text = "Monitoring Resumed"
        binding.indicatorDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_monitoring)
        Toast.makeText(this, "Alert Cancelled. Monitoring continues.", Toast.LENGTH_SHORT).show()
    }

    private fun triggerEmergency() {
        binding.tvAlertTitle.text = getString(R.string.alert_sent_title)
        binding.tvAlertDesc.text = getString(R.string.alert_sent_desc)
        binding.tvTimer.text = "!!!"
        binding.btnCancelAlert.text = getString(R.string.btn_close)
        binding.btnCancelAlert.setOnClickListener {
            binding.layoutCountdown.visibility = View.GONE
            stopMonitoring()
            // Reset UI for next time
            binding.tvAlertTitle.text = getString(R.string.crash_detected_title)
            binding.tvAlertDesc.text = getString(R.string.crash_detected_desc)
            binding.btnCancelAlert.text = getString(R.string.btn_cancel_alert)
            binding.btnCancelAlert.setOnClickListener { cancelEmergency() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isMonitoring) sensorHelper.start()
    }

    override fun onPause() {
        super.onPause()
        sensorHelper.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownManager.release()
    }
}
