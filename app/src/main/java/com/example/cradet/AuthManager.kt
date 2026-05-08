package com.example.cradet

import android.content.Context
import android.content.SharedPreferences

class AuthManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

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
