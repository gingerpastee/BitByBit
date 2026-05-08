package com.example.cradet

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/**
 * SensorHelper: Manages accelerometer and gyroscope sensor data, 
 * calculates G-force, and detects potential accidents.
 */
class SensorHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var onSensorDataChanged: ((accel: FloatArray, gyro: FloatArray, gForce: Float) -> Unit)? = null
    private var onCrashDetected: (() -> Unit)? = null
    private var onHighGDetected: ((gForce: Float) -> Unit)? = null

    private var lastAccel = FloatArray(3)
    private var lastGyro = FloatArray(3)
    private var currentGForce = 1.0f
    
    // Thresholds
    private val G_THRESHOLD = 3.5f // 3.5G for impact detection
    private val GYRO_STABILITY_THRESHOLD = 0.5f // Rotation below this is considered "still"
    private val ACCEL_STABILITY_THRESHOLD = 1.2f // G-Force near 1.0 is considered "still"
    
    private var impactDetected = false
    private val handler = Handler(Looper.getMainLooper())

    var isMonitoring = false
        private set

    fun hasGyroscope(): Boolean = gyroscope != null

    fun setListeners(
        onDataChanged: (accel: FloatArray, gyro: FloatArray, gForce: Float) -> Unit,
        onCrash: () -> Unit,
        onHighG: (Float) -> Unit
    ) {
        this.onSensorDataChanged = onDataChanged
        this.onCrashDetected = onCrash
        this.onHighGDetected = onHighG
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            isMonitoring = true
            impactDetected = false
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            sensorManager.unregisterListener(this)
            isMonitoring = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMonitoring || event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccel = event.values.clone()
                // G-force Calculation: sqrt(x^2 + y^2 + z^2) / 9.81
                val magnitude = sqrt(lastAccel[0] * lastAccel[0] + 
                                     lastAccel[1] * lastAccel[1] + 
                                     lastAccel[2] * lastAccel[2])
                currentGForce = magnitude / 9.81f
                
                checkImpact(currentGForce)
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyro = event.values.clone()
            }
        }

        onSensorDataChanged?.invoke(lastAccel, lastGyro, currentGForce)
    }

    /**
     * Better Crash Detection Logic:
     * 1. Detect high G-force impact.
     * 2. After impact, check for inactivity (phone is still) after a short delay.
     */
    private fun checkImpact(gForce: Float) {
        if (gForce > G_THRESHOLD && !impactDetected) {
            impactDetected = true
            onHighGDetected?.invoke(gForce)
            
            // Wait 2 seconds to see if there's inactivity after the impact
            handler.postDelayed({
                verifyCrash()
            }, 2000)
        }
    }

    private fun verifyCrash() {
        if (!isMonitoring) return
        
        // Calculate current stability
        val gyroMagnitude = sqrt(lastGyro[0] * lastGyro[0] + lastGyro[1] * lastGyro[1] + lastGyro[2] * lastGyro[2])
        
        // If phone is relatively still after impact, it's likely a crash
        if (currentGForce < ACCEL_STABILITY_THRESHOLD && gyroMagnitude < GYRO_STABILITY_THRESHOLD) {
            onCrashDetected?.invoke()
        } else {
            // It was just a bump or movement continued
            impactDetected = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
