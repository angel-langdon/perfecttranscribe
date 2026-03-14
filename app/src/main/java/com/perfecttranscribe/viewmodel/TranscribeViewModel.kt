package com.perfecttranscribe.viewmodel

import android.Manifest
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.perfecttranscribe.api.TranscriptionRepository
import com.perfecttranscribe.audio.Recorder
import com.perfecttranscribe.debug.PipelineLogger
import com.perfecttranscribe.di.ApiKeyStore
import com.perfecttranscribe.di.TranscribeStateStore
import com.perfecttranscribe.share.SharedAudioCache
import com.perfecttranscribe.transcription.normalizeTranscript
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TranscribeUiState(
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val activityMessage: String? = null,
    val transcript: String = "",
    val error: String? = null,
    val hasApiKey: Boolean = false,
    val recordingSeconds: Int = 0,
    val autoCopyToClipboard: Boolean = false,
    val hasPendingSharedAudio: Boolean = false,
    val shouldOpenSettingsForSharedAudio: Boolean = false,
)

@HiltViewModel
class TranscribeViewModel @Inject constructor(
    private val audioRecorder: Recorder,
    private val groqRepository: TranscriptionRepository,
    private val apiKeyManager: ApiKeyStore,
    private val sharedAudioCache: SharedAudioCache,
    private val transcribeStateStore: TranscribeStateStore,
) : ViewModel() {

    private companion object {
        private const val PREPARING_AUDIO_MESSAGE = "Preparing audio…"
        private const val TRANSCRIBING_MESSAGE = "Transcribing…"
    }

    private val _uiState = MutableStateFlow(TranscribeUiState())
    val uiState: StateFlow<TranscribeUiState> = _uiState.asStateFlow()

    private var durationJob: Job? = null
    private var pendingSharedAudioFile: File? = null

    init {
        val hasApiKey = apiKeyManager.hasApiKey()
        _uiState.update { it.copy(hasApiKey = hasApiKey) }

        viewModelScope.launch {
            restorePersistedState(hasApiKey)
        }
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
                activityMessage = null,
                error = null,
                transcript = "",
                recordingSeconds = 0,
                autoCopyToClipboard = autoCopy,
            )
        }
        viewModelScope.launch {
            transcribeStateStore.clearTranscript()
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
        val operationId = PipelineLogger.newOperationId("recording")
        val operationStartTimeNs = PipelineLogger.now()

        PipelineLogger.log(
            operationId,
            "recording.transcribe.start",
            "file=${file.name} size_bytes=${file.length()}",
        )

        _uiState.update {
            it.copy(
                isTranscribing = true,
                activityMessage = TRANSCRIBING_MESSAGE,
            )
        }

        val model = apiKeyManager.getModel()

        viewModelScope.launch {
            groqRepository.transcribe(apiKey, file, model = model, operationId = operationId)
                .onSuccess { text ->
                    val normalizedTranscript = normalizeTranscript(text)
                    transcribeStateStore.saveTranscript(normalizedTranscript)
                    PipelineLogger.logDuration(
                        operationId,
                        "recording.transcribe.success",
                        operationStartTimeNs,
                        "transcript_chars=${normalizedTranscript.length}",
                    )
                    _uiState.update {
                        it.copy(
                            transcript = normalizedTranscript,
                            isTranscribing = false,
                            activityMessage = null,
                        )
                    }
                }
                .onFailure { e ->
                    PipelineLogger.logDuration(
                        operationId,
                        "recording.transcribe.failed",
                        operationStartTimeNs,
                        "error=${e.message ?: e::class.java.simpleName}",
                    )
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Transcription failed",
                            isTranscribing = false,
                            activityMessage = null,
                        )
                    }
                }
            file.delete()
        }
    }

    fun transcribeAudioUri(uri: Uri) {
        val operationId = PipelineLogger.newOperationId("share")
        val totalStartTimeNs = PipelineLogger.now()
        PipelineLogger.log(operationId, "share.received", "uri=$uri")

        _uiState.update {
            it.copy(
                isTranscribing = true,
                activityMessage = PREPARING_AUDIO_MESSAGE,
                transcript = "",
                error = null,
                autoCopyToClipboard = false,
                shouldOpenSettingsForSharedAudio = false,
            )
        }

        viewModelScope.launch {
            transcribeStateStore.clearTranscript()
            val prepareStartTimeNs = PipelineLogger.now()
            val file = sharedAudioCache.copyToCache(uri, operationId = operationId)

            if (file == null || !file.exists() || file.length() == 0L) {
                val hasPendingSharedAudio = pendingSharedAudioFile != null
                PipelineLogger.logDuration(
                    operationId,
                    "share.prepare.failed",
                    prepareStartTimeNs,
                )
                PipelineLogger.logDuration(
                    operationId,
                    "share.finished",
                    totalStartTimeNs,
                    "result=prepare_failed",
                )
                _uiState.update {
                    it.copy(
                        error = "Failed to prepare shared media",
                        isTranscribing = false,
                        activityMessage = null,
                        hasPendingSharedAudio = hasPendingSharedAudio,
                        shouldOpenSettingsForSharedAudio = hasPendingSharedAudio &&
                            apiKeyManager.getApiKey().isNullOrBlank(),
                    )
                }
                return@launch
            }

            PipelineLogger.logDuration(
                operationId,
                "share.prepare.success",
                prepareStartTimeNs,
                "file=${file.name} size_bytes=${file.length()}",
            )
            replacePendingSharedAudio(file)

            val apiKey = apiKeyManager.getApiKey()
            if (apiKey.isNullOrBlank()) {
                PipelineLogger.logDuration(
                    operationId,
                    "share.queued_for_api_key",
                    totalStartTimeNs,
                    "file=${file.name}",
                )
                _uiState.update {
                    it.copy(
                        isTranscribing = false,
                        activityMessage = null,
                        error = "Please set your Groq API key in Settings",
                        transcript = "",
                        autoCopyToClipboard = false,
                        hasPendingSharedAudio = true,
                        shouldOpenSettingsForSharedAudio = true,
                    )
                }
                return@launch
            }

            transcribePreparedSharedAudio(
                file = file,
                apiKey = apiKey,
                operationId = operationId,
                totalStartTimeNs = totalStartTimeNs,
            )
        }
    }

    fun clearTranscript() {
        _uiState.update { it.copy(transcript = "", activityMessage = null) }
        viewModelScope.launch {
            transcribeStateStore.clearTranscript()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveApiKey(key: String) {
        apiKeyManager.saveApiKey(key)
        _uiState.update { it.copy(hasApiKey = true, error = null) }

        pendingSharedAudioFile?.let { pendingFile ->
            val operationId = PipelineLogger.newOperationId("queued-share")
            val totalStartTimeNs = PipelineLogger.now()
            PipelineLogger.log(
                operationId,
                "share.resume_after_api_key",
                "file=${pendingFile.name} size_bytes=${pendingFile.length()}",
            )
            viewModelScope.launch {
                transcribePreparedSharedAudio(
                    file = pendingFile,
                    apiKey = key,
                    operationId = operationId,
                    totalStartTimeNs = totalStartTimeNs,
                )
            }
        }
    }

    fun getApiKey(): String = apiKeyManager.getApiKey().orEmpty()

    fun getModel(): String = apiKeyManager.getModel()

    fun saveModel(model: String) {
        apiKeyManager.saveModel(model)
    }

    fun clearApiKey() {
        apiKeyManager.clearApiKey()
        _uiState.update {
            it.copy(
                hasApiKey = false,
                activityMessage = null,
                shouldOpenSettingsForSharedAudio = pendingSharedAudioFile != null,
            )
        }
    }

    fun onSharedAudioSettingsNavigationHandled() {
        _uiState.update { it.copy(shouldOpenSettingsForSharedAudio = false) }
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
        audioRecorder.stop()?.delete()
    }

    private suspend fun restorePersistedState(hasApiKey: Boolean) {
        val persistedState = transcribeStateStore.state.first()
        val restoredPendingFile = persistedState.pendingSharedAudioPath
            ?.let(::File)
            ?.takeIf(File::exists)

        if (persistedState.pendingSharedAudioPath != null && restoredPendingFile == null) {
            transcribeStateStore.clearPendingSharedAudio()
        }

        pendingSharedAudioFile = restoredPendingFile

        _uiState.update {
            it.copy(
                transcript = persistedState.transcript,
                hasPendingSharedAudio = restoredPendingFile != null,
                shouldOpenSettingsForSharedAudio = restoredPendingFile != null && !hasApiKey,
            )
        }

        if (restoredPendingFile != null && hasApiKey) {
            val apiKey = apiKeyManager.getApiKey()
            if (!apiKey.isNullOrBlank()) {
                val operationId = PipelineLogger.newOperationId("restored-share")
                val totalStartTimeNs = PipelineLogger.now()
                PipelineLogger.log(
                    operationId,
                    "share.resume_from_saved_state",
                    "file=${restoredPendingFile.name} size_bytes=${restoredPendingFile.length()}",
                )
                transcribePreparedSharedAudio(
                    file = restoredPendingFile,
                    apiKey = apiKey,
                    operationId = operationId,
                    totalStartTimeNs = totalStartTimeNs,
                )
            }
        }
    }

    private suspend fun transcribePreparedSharedAudio(
        file: File,
        apiKey: String,
        operationId: String,
        totalStartTimeNs: Long,
    ) {
        if (!file.exists() || file.length() == 0L) {
            clearPendingSharedAudio(deleteFile = false)
            PipelineLogger.logDuration(
                operationId,
                "share.transcribe.skipped_missing_file",
                totalStartTimeNs,
            )
            _uiState.update {
                it.copy(
                    error = "Failed to prepare shared media",
                    isTranscribing = false,
                    activityMessage = null,
                    hasPendingSharedAudio = false,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isTranscribing = true,
                activityMessage = TRANSCRIBING_MESSAGE,
                transcript = "",
                error = null,
                autoCopyToClipboard = false,
                hasPendingSharedAudio = false,
                shouldOpenSettingsForSharedAudio = false,
            )
        }

        val model = apiKeyManager.getModel()
        val transcribeStartTimeNs = PipelineLogger.now()
        PipelineLogger.log(
            operationId,
            "share.transcribe.start",
            "file=${file.name} size_bytes=${file.length()} model=$model",
        )
        groqRepository.transcribe(apiKey, file, model = model, operationId = operationId)
            .onSuccess { text ->
                val normalizedTranscript = normalizeTranscript(text)
                transcribeStateStore.saveTranscript(normalizedTranscript)
                PipelineLogger.logDuration(
                    operationId,
                    "share.transcribe.success",
                    transcribeStartTimeNs,
                    "transcript_chars=${normalizedTranscript.length}",
                )
                PipelineLogger.logDuration(
                    operationId,
                    "share.finished",
                    totalStartTimeNs,
                    "result=success",
                )
                _uiState.update {
                    it.copy(
                        transcript = normalizedTranscript,
                        isTranscribing = false,
                        activityMessage = null,
                    )
                }
            }
            .onFailure { e ->
                PipelineLogger.logDuration(
                    operationId,
                    "share.transcribe.failed",
                    transcribeStartTimeNs,
                    "error=${e.message ?: e::class.java.simpleName}",
                )
                PipelineLogger.logDuration(
                    operationId,
                    "share.finished",
                    totalStartTimeNs,
                    "result=failed",
                )
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Transcription failed",
                        isTranscribing = false,
                        activityMessage = null,
                    )
                }
            }

        clearPendingSharedAudio()
    }

    private suspend fun replacePendingSharedAudio(file: File) {
        val previousFile = pendingSharedAudioFile
        pendingSharedAudioFile = file
        transcribeStateStore.savePendingSharedAudioPath(file.absolutePath)

        if (previousFile?.absolutePath != file.absolutePath) {
            previousFile?.delete()
        }
    }

    private suspend fun clearPendingSharedAudio(deleteFile: Boolean = true) {
        val file = pendingSharedAudioFile
        pendingSharedAudioFile = null
        transcribeStateStore.clearPendingSharedAudio()
        if (deleteFile) {
            file?.delete()
        }
    }
}
