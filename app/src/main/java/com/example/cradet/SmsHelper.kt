package com.example.cradet

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast

class SmsHelper(private val context: Context) {

    fun sendSms(phoneNumber: String, message: String): Boolean {
        if (phoneNumber.isBlank()) return false
        
        return try {
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Use sendMultipartTextMessage for longer messages (>160 chars)
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            
            Log.d("SmsHelper", "SMS sent successfully to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e("SmsHelper", "FAILED to send SMS to $phoneNumber: ${e.message}")
            false
        }
    }

    fun broadcastEmergency(contacts: List<Pair<String, String>>, message: String): Int {
        Log.d("SmsHelper", "Broadcasting emergency message to ${contacts.size} contacts")
        var sentCount = 0
        contacts.forEach { contact ->
            val phone = contact.second.trim()
            if (phone.isNotEmpty()) {
                if (sendSms(phone, message)) {
                    sentCount++
                }
            } else {
                Log.w("SmsHelper", "Skipping contact with empty phone number: ${contact.first}")
            }
        }
        
        if (sentCount > 0) {
            Toast.makeText(context, "Emergency alerts sent to $sentCount contacts", Toast.LENGTH_LONG).show()
        } else if (contacts.isNotEmpty()) {
            Toast.makeText(context, "Failed to send emergency alerts. Check permissions/SIM.", Toast.LENGTH_LONG).show()
        }

        return sentCount
    }
}
