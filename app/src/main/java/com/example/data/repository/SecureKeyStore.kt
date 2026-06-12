package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureKeyStore(context: Context) {

    // Primary: EncryptedSharedPreferences for secure storage
    // Fallback: Regular SharedPreferences if encrypted fails (better than losing data)
    private val encryptedPrefs: SharedPreferences?
    private val fallbackPrefs: SharedPreferences

    init {
        // Try to initialize encrypted prefs
        encryptedPrefs = try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                "secure_posthog_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }

        // Fallback to regular prefs (better than losing data)
        // NOTE: Not encrypted in this case, but at least persists
        fallbackPrefs = context.applicationContext.getSharedPreferences(
            "posthog_keys_fallback",
            Context.MODE_PRIVATE
        )
    }

    fun saveApiKey(key: String): Boolean {
        return try {
            if (encryptedPrefs != null) {
                val editor = encryptedPrefs.edit()
                editor.putString(KEY_API_KEY, key)
                editor.commit()
            } else {
                // Fall back to regular prefs
                val editor = fallbackPrefs.edit()
                editor.putString(KEY_API_KEY, key)
                editor.commit()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun readApiKey(): String {
        return try {
            // Try encrypted prefs first
            encryptedPrefs?.getString(KEY_API_KEY, "")
                // Fall back to regular prefs
                ?: fallbackPrefs.getString(KEY_API_KEY, "")
                ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun clear() {
        try {
            encryptedPrefs?.edit()?.clear()?.commit()
        } catch (_: Exception) {}
        try {
            fallbackPrefs.edit().clear().commit()
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_API_KEY = "posthog_personal_api_key"
    }
}