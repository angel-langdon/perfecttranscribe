package com.perfecttranscribe.di

import kotlinx.coroutines.flow.Flow

data class PersistedTranscribeState(
    val transcript: String = "",
    val pendingSharedAudioPath: String? = null,
)

interface TranscribeStateStore {
    val state: Flow<PersistedTranscribeState>

    suspend fun saveTranscript(transcript: String)

    suspend fun clearTranscript()

    suspend fun savePendingSharedAudioPath(path: String)

    suspend fun clearPendingSharedAudio()
}
