package com.example.cradet

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer
import android.util.Log

/**
 * Handles the 20-second emergency countdown and loud warning beep system.
 */
class CountdownManager(
    private val onTickCallback: (secondsLeft: Int) -> Unit,
    private val onFinishCallback: () -> Unit
) {
    private var timer: CountDownTimer? = null
    // Use STREAM_ALARM for maximum volume and TONE_CDMA_HIGH_L for urgency
    private var toneGenerator: ToneGenerator? = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    
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
                playUrgentBeep()
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
    }

    private fun playUrgentBeep() {
        try {
            // TONE_SUP_PIP is loud and sharp
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_PIP, 200)
        } catch (e: Exception) {
            Log.e("CountdownManager", "Error playing beep: ${e.message}")
        }
    }

    fun release() {
        stop()
        toneGenerator?.release()
        toneGenerator = null
    }
}
