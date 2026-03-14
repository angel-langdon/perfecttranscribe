package com.perfecttranscribe.di

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyManager @Inject constructor(
    private val encryptedPrefs: SharedPreferences,
) : ApiKeyStore {
    companion object {
        private const val KEY_API_KEY = "groq_api_key"
    }

    override fun getApiKey(): String? = encryptedPrefs.getString(KEY_API_KEY, null)

    override fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, key).apply()
    }

    override fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    override fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).apply()
    }
}
