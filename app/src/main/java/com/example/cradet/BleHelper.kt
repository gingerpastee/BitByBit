package com.example.cradet

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import kotlin.math.pow

/**
 * SimulatedBleHelper: Provides a stable simulation of smartwatch connectivity.
 * Replaces real Bluetooth APIs for hackathon reliability.
 */
class BleHelper(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    // Targeted Smartwatch Info (For display only)
    private val TARGET_DEVICE_NAME = "FB BGS003"
    private val TARGET_MAC_ADDRESS = "56:75:DE:1D:5C:2B"

    // Callbacks
    var onConnectionStateChange: ((Boolean, String?) -> Unit)? = null
    var onRssiUpdate: ((Int, Double) -> Unit)? = null

    private var isConnected = false
    private val simulationInterval = 3000L

    private val simulationRunnable = object : Runnable {
        override fun run() {
            if (isConnected) {
                // Simulate RSSI between -48 and -75
                val rssi = (-75..-48).random()
                val distance = calculateSimulatedDistance(rssi)
                onRssiUpdate?.invoke(rssi, distance)
            }
            handler.postDelayed(this, simulationInterval)
        }
    }

    fun checkExistingConnection() {
        if (isConnected) return
        
        Log.d("BleHelper", "Simulating connection to $TARGET_DEVICE_NAME...")
        
        // Simulate a short delay for connection
        handler.postDelayed({
            isConnected = true
            onConnectionStateChange?.invoke(true, TARGET_DEVICE_NAME)
            startSimulation()
        }, 1500)
    }

    fun startWatchdog() {
        // No watchdog needed for simulation, but kept for compatibility
        checkExistingConnection()
    }

    fun stopWatchdog() {
        // No watchdog needed
    }

    fun startScan() {
        checkExistingConnection()
    }

    fun stopScan() {
        // No-op
    }

    fun disconnect() {
        isConnected = false
        onConnectionStateChange?.invoke(false, null)
        stopSimulation()
    }

    private fun startSimulation() {
        handler.removeCallbacks(simulationRunnable)
        handler.post(simulationRunnable)
    }

    private fun stopSimulation() {
        handler.removeCallbacks(simulationRunnable)
    }

    private fun calculateSimulatedDistance(rssi: Int): Double {
        // Strong RSSI (-48) -> ~0.7m
        // Weak RSSI (-75) -> ~4.5m
        // Linear mapping for simplicity in demo
        val minRssi = -75.0
        val maxRssi = -48.0
        val minDistance = 0.5
        val maxDistance = 5.0
        
        val normalized = (rssi - minRssi) / (maxRssi - minRssi)
        // Invert because higher RSSI means lower distance
        return maxDistance - (normalized * (maxDistance - minDistance))
    }

    fun isDeviceConnected(): Boolean = isConnected
    fun getTargetName(): String = TARGET_DEVICE_NAME
    fun getTargetMac(): String = TARGET_MAC_ADDRESS
}
