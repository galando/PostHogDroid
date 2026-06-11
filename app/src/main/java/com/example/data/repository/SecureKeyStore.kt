package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureKeyStore(context: Context) {

    // EncryptedSharedPreferences (backed by Tink) can fail in release builds when R8 strips
    // Tink's reflection-registered key managers, or after keystore state changes (backup/restore,
    // key invalidation).  Fall back to null so construction never throws — callers treat a blank
    // key as "not logged in" and prompt for credentials rather than crashing.
    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_posthog_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    fun saveApiKey(key: String) {
        prefs?.edit()?.putString(KEY_API_KEY, key)?.apply()
    }

    fun readApiKey(): String = prefs?.getString(KEY_API_KEY, "") ?: ""

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    companion object {
        private const val KEY_API_KEY = "posthog_personal_api_key"
    }
}
