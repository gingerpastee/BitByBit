package com.example.cradet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

class AuthManager(context: Context) {
    private val prefs: SharedPreferences = try {
        createEncryptedPrefs(context)
    } catch (e: Exception) {
        Log.e("AuthManager", "Failed to initialize EncryptedSharedPreferences, clearing data and retrying", e)
        // If it fails (likely BAD_DECRYPT due to corrupted keys), clear the file and try again
        clearCorruptedPrefs(context)
        try {
            createEncryptedPrefs(context)
        } catch (e2: Exception) {
            Log.e("AuthManager", "Persistent failure in EncryptedSharedPreferences, falling back to plain", e2)
            context.getSharedPreferences("auth_prefs_plain", Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "auth_prefs_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearCorruptedPrefs(context: Context) {
        try {
            // Delete the shared preferences file manually if EncryptedSharedPreferences.create fails
            val sharedPrefsFile = java.io.File(context.filesDir.parent, "shared_prefs/auth_prefs_secure.xml")
            if (sharedPrefsFile.exists()) {
                sharedPrefsFile.delete()
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Failed to delete corrupted prefs file", e)
        }
    }

    fun signUp(email: String, pass: String): Boolean {
        if (prefs.contains(email)) {
            android.util.Log.d("AuthManager", "SignUp failed: Email $email already exists")
            return false
        }
        prefs.edit().putString(email, pass).apply()
        android.util.Log.d("AuthManager", "SignUp success for: $email")
        return true
    }

    fun login(email: String, pass: String): Boolean {
        val storedPass = prefs.getString(email, null)
        val success = storedPass != null && storedPass == pass
        android.util.Log.d("AuthManager", "Login attempt for $email: success=$success")
        return success
    }

    fun setLoggedIn(email: String) {
        prefs.edit().putString("current_user", email).apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getString("current_user", null) != null
    }

    fun logout() {
        prefs.edit().remove("current_user").apply()
    }

    fun getCurrentUser(): String? {
        return prefs.getString("current_user", null)
    }
}
