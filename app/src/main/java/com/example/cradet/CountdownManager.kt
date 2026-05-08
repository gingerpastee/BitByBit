package com.example.cradet

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Handles the 20-second emergency countdown and loud warning beep system.
 */
class CountdownManager(
    private val context: android.content.Context,
    private val onTickCallback: (secondsLeft: Int) -> Unit,
    private val onFinishCallback: () -> Unit
) {
    private var timer: CountDownTimer? = null
    // Use STREAM_ALARM for maximum volume and TONE_CDMA_HIGH_L for urgency
    private var toneGenerator: ToneGenerator? = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
    
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d("CountdownManager", "Emergency countdown started (20s)")
        
        timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt() + 1
                Log.d("CountdownManager", "T-minus: $seconds")
                onTickCallback(seconds)
                playUrgentAlert()
                vibrateStrong()
            }

            override fun onFinish() {
                isRunning = false
                Log.d("CountdownManager", "Countdown finished! Triggering emergency protocol.")
                onTickCallback(0)
                onFinishCallback()
            }
        }.start()
    }

    fun stop() {
        if (isRunning) {
            Log.d("CountdownManager", "Countdown cancelled by user.")
        }
        timer?.cancel()
        timer = null
        isRunning = false
        vibrator.cancel()
    }

    private fun playUrgentAlert() {
        try {
            // TONE_CDMA_EMERGENCY_RINGBACK is very loud and distinctive
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
        } catch (e: Exception) {
            Log.e("CountdownManager", "Error playing beep: ${e.message}")
        }
    }

    private fun vibrateStrong() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Intense vibration pattern: [delay, vibrate, sleep, vibrate]
            val pattern = longArrayOf(0, 500, 200, 300)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    fun release() {
        stop()
        toneGenerator?.release()
        toneGenerator = null
    }
}
