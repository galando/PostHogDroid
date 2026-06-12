package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

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

    fun saveApiKey(key: String): Boolean {
        return try {
            prefs?.edit()?.putString(KEY_API_KEY, key)?.apply() ?: false
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Failed to save API key", e)
            false
        }
    }

    fun readApiKey(): String = prefs?.getString(KEY_API_KEY, "") ?: ""

    fun clear() {
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Failed to clear secure store", e)
        }
    }

    companion object {
        private const val KEY_API_KEY = "posthog_personal_api_key"
    }
}
