package com.perfecttranscribe.viewmodel

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.perfecttranscribe.api.TranscriptionRepository
import com.perfecttranscribe.audio.Recorder
import com.perfecttranscribe.di.ApiKeyStore
import com.perfecttranscribe.transcription.normalizeTranscript
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscribeUiState(
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcript: String = "",
    val error: String? = null,
    val hasApiKey: Boolean = false,
    val recordingSeconds: Int = 0,
    val autoCopyToClipboard: Boolean = false,
)

@HiltViewModel
class TranscribeViewModel @Inject constructor(
    private val audioRecorder: Recorder,
    private val groqRepository: TranscriptionRepository,
    private val apiKeyManager: ApiKeyStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscribeUiState())
    val uiState: StateFlow<TranscribeUiState> = _uiState.asStateFlow()

    private var durationJob: Job? = null

    init {
        _uiState.update { it.copy(hasApiKey = apiKeyManager.hasApiKey()) }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(autoCopy: Boolean = false): Boolean {
        val apiKey = apiKeyManager.getApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.update { it.copy(error = "Please set your Groq API key in Settings") }
            return false
        }

        try {
            audioRecorder.start()
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message ?: "Failed to start recording") }
            return false
        }

        _uiState.update {
            it.copy(
                isRecording = true,
                error = null,
                transcript = "",
                recordingSeconds = 0,
                autoCopyToClipboard = autoCopy,
            )
        }

        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingSeconds = it.recordingSeconds + 1) }
            }
        }

        return true
    }

    fun stopRecording() {
        durationJob?.cancel()
        durationJob = null

        val file = audioRecorder.stop()
        _uiState.update { it.copy(isRecording = false) }

        if (file == null || !file.exists() || file.length() == 0L) return

        val apiKey = apiKeyManager.getApiKey() ?: return

        _uiState.update { it.copy(isTranscribing = true) }

        viewModelScope.launch {
            groqRepository.transcribe(apiKey, file)
                .onSuccess { text ->
                    _uiState.update {
                        it.copy(
                            transcript = normalizeTranscript(text),
                            isTranscribing = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Transcription failed",
                            isTranscribing = false,
                        )
                    }
                }
            file.delete()
        }
    }

    fun clearTranscript() {
        _uiState.update { it.copy(transcript = "") }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveApiKey(key: String) {
        apiKeyManager.saveApiKey(key)
        _uiState.update { it.copy(hasApiKey = true) }
    }

    fun getApiKey(): String = apiKeyManager.getApiKey().orEmpty()

    fun clearApiKey() {
        apiKeyManager.clearApiKey()
        _uiState.update { it.copy(hasApiKey = false) }
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
        audioRecorder.stop()?.delete()
    }
}
