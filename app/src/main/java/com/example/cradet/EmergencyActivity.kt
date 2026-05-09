package com.example.cradet

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.cradet.databinding.LayoutCountdownBinding

class EmergencyActivity : AppCompatActivity() {

    private lateinit var binding: LayoutCountdownBinding
    private lateinit var countdownManager: CountdownManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup Window Flags for Lock Screen visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        binding = LayoutCountdownBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        
        countdownManager = CountdownManager(
            this,
            onTickCallback = { seconds ->
                binding.tvTimer.text = seconds.toString()
            },
            onFinishedCallback = {
                binding.tvTimer.text = "0"
                executeEmergency()
            }
        )
        countdownManager.start()
    }

    private fun setupUI() {
        binding.btnCancelAlert.setOnClickListener {
            cancelEmergency()
        }
    }

    private fun cancelEmergency() {
        countdownManager.stop()
        val intent = Intent(this, MonitoringService::class.java).apply {
            putExtra("ACTION", "CONFIRM_SAFE")
        }
        startService(intent)
        finish()
    }

    private fun executeEmergency() {
        val intent = Intent(this, MonitoringService::class.java).apply {
            putExtra("ACTION", "EXECUTE_EMERGENCY")
        }
        startService(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownManager.release()
    }
}
