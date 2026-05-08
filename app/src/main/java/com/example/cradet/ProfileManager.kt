package com.example.cradet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ProfileManager(context: Context) {
    private val prefs: SharedPreferences = try {
        createEncryptedPrefs(context)
    } catch (e: Exception) {
        Log.e("ProfileManager", "Failed to initialize EncryptedSharedPreferences, clearing and retrying", e)
        clearCorruptedPrefs(context)
        try {
            createEncryptedPrefs(context)
        } catch (e2: Exception) {
            Log.e("ProfileManager", "Persistent failure, falling back to plain", e2)
            context.getSharedPreferences("user_profile_plain", Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "user_profile_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearCorruptedPrefs(context: Context) {
        try {
            val sharedPrefsFile = java.io.File(context.filesDir.parent, "shared_prefs/user_profile_secure.xml")
            if (sharedPrefsFile.exists()) {
                sharedPrefsFile.delete()
            }
        } catch (e: Exception) {
            Log.e("ProfileManager", "Failed to delete corrupted prefs file", e)
        }
    }

    fun saveProfile(name: String, email: String, bloodGroup: String, allergies: String) {
        prefs.edit().apply {
            putString("name", name)
            putString("email", email)
            putString("bloodGroup", bloodGroup)
            putString("allergies", allergies)
            apply()
        }
    }

    fun getProfile(): Map<String, String> {
        return mapOf(
            "name" to (prefs.getString("name", "") ?: ""),
            "email" to (prefs.getString("email", "") ?: ""),
            "bloodGroup" to (prefs.getString("bloodGroup", "Not Specified") ?: "Not Specified"),
            "allergies" to (prefs.getString("allergies", "None") ?: "None")
        )
    }

    data class Contact(val name: String, val phone: String, val relationship: String)

    fun saveContacts(contacts: List<Contact>) {
        prefs.edit {
            putInt("contact_count", contacts.size)
            contacts.forEachIndexed { index, contact ->
                putString("contact_name_$index", contact.name)
                putString("contact_phone_$index", contact.phone)
                putString("contact_rel_$index", contact.relationship)
            }
        }
    }

    fun getContacts(): List<Contact> {
        val count = prefs.getInt("contact_count", 0)
        val contacts = mutableListOf<Contact>()
        for (i in 0 until count) {
            val name = prefs.getString("contact_name_$i", "") ?: ""
            val phone = prefs.getString("contact_phone_$i", "") ?: ""
            val rel = prefs.getString("contact_rel_$i", "") ?: ""
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                contacts.add(Contact(name, phone, rel))
            }
        }
        return contacts
    }

    fun deleteContact(index: Int) {
        val contacts = getContacts().toMutableList()
        if (index in contacts.indices) {
            contacts.removeAt(index)
            saveContacts(contacts)
        }
    }
}

// Extension for cleaner SharedPreferences edits
inline fun android.content.SharedPreferences.edit(action: android.content.SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    action(editor)
    editor.apply()
}
