package com.example.cradet

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*
import kotlin.math.pow

class BleHelper(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    // Targeted Smartwatch Info
    private val TARGET_DEVICE_NAME = "FB BGS003"
    private val TARGET_MAC_ADDRESS = "56:75:DE:1D:5C:2B"

    // Callbacks
    var onConnectionStateChange: ((Boolean, String?) -> Unit)? = null
    var onRssiUpdate: ((Int, Double) -> Unit)? = null

    private var lastRssi = 0
    private var isConnected = false
    private var connectionAttemptCount = 0
    private val RECONNECT_INTERVAL = 15000L // Check every 15 seconds

    private val reconnectionRunnable = object : Runnable {
        override fun run() {
            if (!isConnected) {
                Log.d("BleHelper", "Watchdog: Device not connected. Checking again...")
                checkExistingConnection()
            }
            handler.postDelayed(this, RECONNECT_INTERVAL)
        }
    }

    fun startWatchdog() {
        handler.removeCallbacks(reconnectionRunnable)
        handler.post(reconnectionRunnable)
    }

    fun stopWatchdog() {
        handler.removeCallbacks(reconnectionRunnable)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d("BleHelper", "onConnectionStateChange: status=$status, newState=$newState")
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                connectionAttemptCount = 0
                Log.i("BleHelper", "Connected to GATT server: ${gatt.device.name}")
                onConnectionStateChange?.invoke(true, gatt.device.name ?: TARGET_DEVICE_NAME)
                startRssiPolling()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                Log.i("BleHelper", "Disconnected from GATT server.")
                onConnectionStateChange?.invoke(false, null)
                stopRssiPolling()
                
                // Optional: Auto-reconnect if it was an unexpected disconnect
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w("BleHelper", "Unexpected disconnect. Attempting auto-reconnect...")
                    handler.postDelayed({ checkExistingConnection() }, 5000)
                }
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                lastRssi = rssi
                val distance = calculateDistance(rssi)
                onRssiUpdate?.invoke(rssi, distance)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown"
            Log.d("BleHelper", "Scan result: $deviceName [${device.address}]")
            
            if (device.address == TARGET_MAC_ADDRESS || deviceName.contains(TARGET_DEVICE_NAME, ignoreCase = true)) {
                Log.d("BleHelper", "Target device found via scan: $deviceName [${device.address}]")
                stopScan()
                connectToDevice(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkExistingConnection() {
        if (isConnected) {
            Log.d("BleHelper", "Already connected. Skipping check.")
            return
        }
        
        Log.d("BleHelper", "Checking existing/paired connections for $TARGET_DEVICE_NAME")
        
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        
        // 1. Check devices already connected by the system (GATT)
        val connectedGattDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
        Log.d("BleHelper", "Connected GATT devices found: ${connectedGattDevices.size}")
        for (device in connectedGattDevices) {
            val deviceName = device.name ?: ""
            Log.d("BleHelper", "Checking connected device: $deviceName [${device.address}]")
            if (device.address == TARGET_MAC_ADDRESS || deviceName.contains(TARGET_DEVICE_NAME, ignoreCase = true)) {
                Log.d("BleHelper", "Target device $TARGET_DEVICE_NAME is already connected to the system. Binding app to it.")
                connectToDevice(device)
                return
            }
        }

        // 2. Check bonded (paired) devices
        val pairedDevices = bluetoothAdapter?.bondedDevices
        Log.d("BleHelper", "Paired devices found: ${pairedDevices?.size ?: 0}")
        pairedDevices?.forEach { device ->
            val deviceName = device.name ?: ""
            Log.d("BleHelper", "Checking paired device: $deviceName [${device.address}]")
            if (device.address == TARGET_MAC_ADDRESS || deviceName.contains(TARGET_DEVICE_NAME, ignoreCase = true)) {
                Log.d("BleHelper", "Target device $TARGET_DEVICE_NAME is paired. Attempting connection.")
                connectToDevice(device)
                return
            }
        }

        // 3. If not found in paired/connected, start a fresh scan
        Log.d("BleHelper", "Device not found in system connections. Starting fresh scan.")
        startScan()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning || isConnected) return
        
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e("BleHelper", "Bluetooth LE Scanner not available")
            return
        }
        
        isScanning = true
        scanner.startScan(scanCallback)
        Log.d("BleHelper", "Started BLE scanning for $TARGET_DEVICE_NAME")

        // Stop scanning after 30 seconds to save battery
        handler.postDelayed({
            if (isScanning) {
                stopScan()
                Log.d("BleHelper", "Scan timed out.")
            }
        }, 30000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d("BleHelper", "Stopped BLE scanning")
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d("BleHelper", "Attempting to connect to ${device.address}")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
    }

    private fun startRssiPolling() {
        handler.post(object : Runnable {
            @SuppressLint("MissingPermission")
            override fun run() {
                if (isConnected) {
                    bluetoothGatt?.readRemoteRssi()
                    handler.postDelayed(this, 2000) // Poll every 2 seconds
                }
            }
        })
    }

    private fun stopRssiPolling() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun calculateDistance(rssi: Int): Double {
        // Simple path loss model for distance estimation
        // d = 10 ^ ((Measured Power - RSSI) / (10 * N))
        // Measured Power: RSSI at 1 meter (approx -60 to -70)
        // N: Path loss exponent (2 to 4)
        val measuredPower = -65
        val n = 2.5
        return 10.0.pow((measuredPower - rssi) / (10 * n))
    }

    fun isDeviceConnected(): Boolean = isConnected
    fun getTargetName(): String = TARGET_DEVICE_NAME
    fun getTargetMac(): String = TARGET_MAC_ADDRESS
}
