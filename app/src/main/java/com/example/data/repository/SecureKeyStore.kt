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
    private val context: Context = context.applicationContext
    private var prefs: SharedPreferences? = initPrefs()

    private fun initPrefs(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val created = EncryptedSharedPreferences.create(
                context,
                "secure_posthog_keys",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            if (BuildConfig.DEBUG) Log.d("SecureKeyStore", "EncryptedSharedPreferences initialized successfully")
            created
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Failed to initialize EncryptedSharedPreferences", e)
            null
        }
    }

    private fun ensurePrefs(): SharedPreferences? {
        if (prefs == null) {
            if (BuildConfig.DEBUG) Log.w("SecureKeyStore", "prefs is null, attempting to reinitialize")
            prefs = initPrefs()
        }
        return prefs
    }

    fun saveApiKey(key: String): Boolean {
        val currentPrefs = ensurePrefs()
        if (currentPrefs == null) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Cannot save API key: prefs is null after reinit attempt")
            return false
        }
        return try {
            val editor = currentPrefs.edit()
            editor.putString(KEY_API_KEY, key)
            val committed = editor.commit()
            if (BuildConfig.DEBUG) {
                Log.d("SecureKeyStore", "API key save result: $committed, key length: ${key.length}")
            }
            committed
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Failed to save API key", e)
            false
        }
    }

    fun readApiKey(): String {
        val currentPrefs = ensurePrefs()
        if (currentPrefs == null) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Cannot read API key: prefs is null after reinit attempt")
            return ""
        }
        val key = currentPrefs.getString(KEY_API_KEY, "") ?: ""
        if (BuildConfig.DEBUG) {
            Log.d("SecureKeyStore", "Read API key: isBlank=${key.isBlank()}, length=${key.length}")
        }
        return key
    }

    fun clear() {
        val currentPrefs = ensurePrefs()
        if (currentPrefs == null) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Cannot clear: prefs is null after reinit attempt")
            return
        }
        try {
            currentPrefs.edit().clear().commit()
            if (BuildConfig.DEBUG) Log.d("SecureKeyStore", "Cleared secure store successfully")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SecureKeyStore", "Failed to clear secure store", e)
        }
    }

    companion object {
        private const val KEY_API_KEY = "posthog_personal_api_key"
    }
}
