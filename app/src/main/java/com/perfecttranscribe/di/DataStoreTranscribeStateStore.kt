package com.perfecttranscribe.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTranscribeStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TranscribeStateStore {

    private companion object {
        val TRANSCRIPT = stringPreferencesKey("saved_transcript")
        val PENDING_SHARED_AUDIO_PATH = stringPreferencesKey("pending_shared_audio_path")
    }

    override val state: Flow<PersistedTranscribeState> = dataStore.data.map { preferences ->
        PersistedTranscribeState(
            transcript = preferences[TRANSCRIPT].orEmpty(),
            pendingSharedAudioPath = preferences[PENDING_SHARED_AUDIO_PATH],
        )
    }

    override suspend fun saveTranscript(transcript: String) {
        dataStore.edit { preferences ->
            if (transcript.isEmpty()) {
                preferences.remove(TRANSCRIPT)
            } else {
                preferences[TRANSCRIPT] = transcript
            }
        }
    }

    override suspend fun clearTranscript() {
        dataStore.edit { preferences ->
            preferences.remove(TRANSCRIPT)
        }
    }

    override suspend fun savePendingSharedAudioPath(path: String) {
        dataStore.edit { preferences ->
            preferences[PENDING_SHARED_AUDIO_PATH] = path
        }
    }

    override suspend fun clearPendingSharedAudio() {
        dataStore.edit { preferences ->
            preferences.remove(PENDING_SHARED_AUDIO_PATH)
        }
    }
}
