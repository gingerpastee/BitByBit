package com.example.cradet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sqrt

/**
 * SensorHelper: Manages multi-sensor data with adaptive fallback logic.
 * Implements smart crash detection by combining G-force spikes and post-impact inactivity.
 */
class SensorHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Primary Sensors
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    // Callbacks
    private var onDataUpdate: ((accel: FloatArray, gyro: FloatArray, gForce: Float, status: String) -> Unit)? = null
    private var onPotentialCrash: (() -> Unit)? = null

    // State
    private var lastAccel = FloatArray(3)
    private var lastGyro = FloatArray(3)
    private var currentGForce = 1.0f
    private var isMonitoring = false
    private var impactDetected = false
    
    // Detection Constants
    private val IMPACT_G_THRESHOLD = 3.5f       // Trigger impact state above 3.5G
    private val STILLNESS_G_THRESHOLD = 1.2f    // Back to near 1G means still
    private val STILLNESS_GYRO_THRESHOLD = 0.5f // Low rotation means still
    private val INACTIVITY_DELAY = 2500L        // Wait 2.5s to confirm inactivity

    private val handler = Handler(Looper.getMainLooper())

    fun setListeners(
        onUpdate: (FloatArray, FloatArray, Float, String) -> Unit,
        onCrash: () -> Unit
    ) {
        this.onDataUpdate = onUpdate
        this.onPotentialCrash = onCrash
    }

    fun getSensorStatus(): String {
        return when {
            accelerometer != null && gyroscope != null -> "Full Monitoring Active"
            accelerometer != null -> "Gyroscope Unavailable - Fallback Mode"
            else -> "No compatible sensors found"
        }
    }

    fun hasGyroscope(): Boolean = gyroscope != null

    fun start() {
        if (isMonitoring) return
        
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        isMonitoring = true
        impactDetected = false
        Log.d("SensorHelper", "Monitoring started. Status: ${getSensorStatus()}")
    }

    fun stop() {
        if (!isMonitoring) return
        sensorManager.unregisterListener(this)
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        Log.d("SensorHelper", "Monitoring stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMonitoring || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccel = event.values.clone()
                // G-Force = Magnitude / Gravity (9.81)
                val mag = sqrt(lastAccel[0] * lastAccel[0] + lastAccel[1] * lastAccel[1] + lastAccel[2] * lastAccel[2])
                currentGForce = mag / 9.81f
                checkImpact(currentGForce)
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyro = event.values.clone()
            }
        }

        onDataUpdate?.invoke(lastAccel, lastGyro, currentGForce, getSensorStatus())
    }

    private fun checkImpact(gForce: Float) {
        if (gForce > IMPACT_G_THRESHOLD && !impactDetected) {
            impactDetected = true
            Log.w("SensorHelper", "High G-force impact detected: $gForce G")
            
            // Multi-sensor Confirmation: Wait to see if phone is inactive after impact
            handler.postDelayed({
                confirmCrash()
            }, INACTIVITY_DELAY)
        }
    }

    private fun confirmCrash() {
        if (!isMonitoring) return

        val gyroMag = sqrt(lastGyro[0] * lastGyro[0] + lastGyro[1] * lastGyro[1] + lastGyro[2] * lastGyro[2])
        
        // Logical Check: If current G-Force is back to normal AND rotation is low
        // It means a massive impact happened followed by the phone not moving (unconscious/dropped)
        val isStill = currentGForce < STILLNESS_G_THRESHOLD && 
                      (!hasGyroscope() || gyroMag < STILLNESS_GYRO_THRESHOLD)

        if (isStill) {
            Log.e("SensorHelper", "Crash Confirmed: Post-impact inactivity detected.")
            onPotentialCrash?.invoke()
        } else {
            Log.d("SensorHelper", "Crash Rejected: Movement detected after impact.")
            impactDetected = false
        }
    }

    fun resetImpactState() {
        impactDetected = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
