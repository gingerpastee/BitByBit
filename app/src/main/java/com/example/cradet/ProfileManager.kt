package com.example.cradet

import android.content.Context
import android.content.SharedPreferences

class ProfileManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    fun saveProfile(name: String, bloodGroup: String, allergies: String) {
        prefs.edit().apply {
            putString("name", name)
            putString("bloodGroup", bloodGroup)
            putString("allergies", allergies)
            apply()
        }
    }

    fun getProfile(): Map<String, String> {
        return mapOf(
            "name" to (prefs.getString("name", "Unknown User") ?: "Unknown User"),
            "bloodGroup" to (prefs.getString("bloodGroup", "Not Specified") ?: "Not Specified"),
            "allergies" to (prefs.getString("allergies", "None") ?: "None")
        )
    }

    fun saveContacts(contacts: List<Pair<String, String>>) {
        prefs.edit().apply {
            putInt("contact_count", contacts.size)
            contacts.forEachIndexed { index, pair ->
                putString("contact_name_$index", pair.first)
                putString("contact_phone_$index", pair.second)
            }
            apply()
        }
    }

    fun getContacts(): List<Pair<String, String>> {
        val count = prefs.getInt("contact_count", 0)
        val contacts = mutableListOf<Pair<String, String>>()
        for (i in 0 until count) {
            val name = prefs.getString("contact_name_$i", "") ?: ""
            val phone = prefs.getString("contact_phone_$i", "") ?: ""
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                contacts.add(name to phone)
            }
        }
        return contacts
    }
}
