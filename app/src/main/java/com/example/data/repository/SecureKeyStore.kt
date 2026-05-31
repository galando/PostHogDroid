package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Wrapper around EncryptedSharedPreferences for storing the PostHog API key
 * in the Android Keystore rather than in the Room database.
 */
class SecureKeyStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_posthog_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun readApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "posthog_personal_api_key"
    }
}
