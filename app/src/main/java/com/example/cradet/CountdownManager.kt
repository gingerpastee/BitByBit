package com.example.cradet

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer

/**
 * Handles the 20-second emergency countdown and warning beep system.
 */
class CountdownManager(
    private val onTick: (secondsLeft: Int) -> Unit,
    private val onFinish: () -> Unit
) {
    private var timer: CountDownTimer? = null
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        
        timer = object : CountDownTimer(20000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                onTick(seconds)
                playWarningBeep()
            }

            override fun onFinish() {
                isRunning = false
                onFinish()
            }
        }.start()
    }

    fun stop() {
        timer?.cancel()
        timer = null
        isRunning = false
    }

    private fun playWarningBeep() {
        // Simple high-pitched beep
        try {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        stop()
        toneGenerator.release()
    }
}
