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
        private const val KEY_MODEL = "transcription_model"
        private const val KEY_VOCABULARY_HINTS = "vocabulary_hints"
        const val DEFAULT_MODEL = "whisper-large-v3"
    }

    override fun getApiKey(): String? = encryptedPrefs.getString(KEY_API_KEY, null)

    override fun saveApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_API_KEY, key).commit()
    }

    override fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    override fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).commit()
    }

    override fun getModel(): String =
        encryptedPrefs.getString(KEY_MODEL, null) ?: DEFAULT_MODEL

    override fun saveModel(model: String) {
        encryptedPrefs.edit().putString(KEY_MODEL, model).commit()
    }

    override fun getVocabularyHints(): String =
        encryptedPrefs.getString(KEY_VOCABULARY_HINTS, null).orEmpty()

    override fun saveVocabularyHints(hints: String) {
        encryptedPrefs.edit().putString(KEY_VOCABULARY_HINTS, hints).commit()
    }
}
