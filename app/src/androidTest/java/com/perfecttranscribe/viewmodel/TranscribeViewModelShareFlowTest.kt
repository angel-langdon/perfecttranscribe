package com.perfecttranscribe.viewmodel

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.perfecttranscribe.api.TranscriptionRepository
import com.perfecttranscribe.audio.Recorder
import com.perfecttranscribe.di.ApiKeyStore
import com.perfecttranscribe.di.PersistedTranscribeState
import com.perfecttranscribe.di.TranscribeStateStore
import com.perfecttranscribe.share.SharedAudioCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TranscribeViewModelShareFlowTest {

    @Test
    fun transcribeSharedAudioWithoutApiKeyQueuesShareAndRequestsSettings() = runViewModelTest {
        val cachedAudio = createTempAudioFile(extension = ".opus")
        val repository = FakeTranscriptionRepository()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(),
            groqRepository = repository,
            apiKeyManager = FakeApiKeyStore(apiKey = null),
            sharedAudioCache = FakeSharedAudioCache(cachedFile = cachedAudio),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.transcribeAudioUri(Uri.parse("content://example/audio.opus"))

        assertEquals("Preparing audio…", viewModel.uiState.value.activityMessage)
        advanceUntilIdle()

        assertEquals("Please set your Groq API key in Settings", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.hasPendingSharedAudio)
        assertTrue(viewModel.uiState.value.shouldOpenSettingsForSharedAudio)
        assertEquals(null, viewModel.uiState.value.activityMessage)
        assertEquals(0, repository.callCount)
        clearViewModel(viewModel)
        cachedAudio.delete()
    }

    @Test
    fun savingApiKeyAfterQueuedSharedAudioStartsTranscription() = runViewModelTest {
        val cachedAudio = createTempAudioFile(extension = ".opus")
        val repository = FakeTranscriptionRepository(result = Result.success(" queued share "))
        val sharedAudioCache = FakeSharedAudioCache(cachedFile = cachedAudio)
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(),
            groqRepository = repository,
            apiKeyManager = FakeApiKeyStore(apiKey = null),
            sharedAudioCache = sharedAudioCache,
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.transcribeAudioUri(Uri.parse("content://example/audio.opus"))
        advanceUntilIdle()
        viewModel.saveApiKey("gsk_test")
        advanceUntilIdle()

        assertEquals(1, sharedAudioCache.copiedUris.size)
        assertEquals(1, repository.callCount)
        assertEquals("queued share.", viewModel.uiState.value.transcript)
        assertFalse(viewModel.uiState.value.hasPendingSharedAudio)
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertFalse(cachedAudio.exists())
        clearViewModel(viewModel)
    }

    @Test
    fun sharedAudioClearsStaleAutoCopyStateBeforeQueueing() = runViewModelTest {
        val previousRecording = createTempAudioFile()
        val cachedAudio = createTempAudioFile(extension = ".opus")
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = previousRecording),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" old transcript ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = FakeSharedAudioCache(cachedFile = cachedAudio),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.startRecording(autoCopy = true)
        viewModel.stopRecording()
        advanceUntilIdle()
        viewModel.clearApiKey()

        viewModel.transcribeAudioUri(Uri.parse("content://example/audio.opus"))
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.transcript)
        assertFalse(viewModel.uiState.value.autoCopyToClipboard)
        assertTrue(viewModel.uiState.value.hasPendingSharedAudio)
        clearViewModel(viewModel)
        cachedAudio.delete()
    }

    @Test
    fun restoresPersistedTranscriptAndPendingSharedAudioState() = runViewModelTest {
        val pendingAudio = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(),
            groqRepository = FakeTranscriptionRepository(),
            apiKeyManager = FakeApiKeyStore(apiKey = null),
            sharedAudioCache = FakeSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(
                initialState = PersistedTranscribeState(
                    transcript = "saved transcript.",
                    pendingSharedAudioPath = pendingAudio.absolutePath,
                ),
            ),
        )

        advanceUntilIdle()

        assertEquals("saved transcript.", viewModel.uiState.value.transcript)
        assertTrue(viewModel.uiState.value.hasPendingSharedAudio)
        assertTrue(viewModel.uiState.value.shouldOpenSettingsForSharedAudio)
        clearViewModel(viewModel)
        pendingAudio.delete()
    }

    @Test
    fun resumesPersistedPendingSharedAudioWhenApiKeyAlreadyExists() = runViewModelTest {
        val pendingAudio = createTempAudioFile()
        val repository = FakeTranscriptionRepository(result = Result.success(" restored share "))
        val stateStore = FakeTranscribeStateStore(
            initialState = PersistedTranscribeState(
                pendingSharedAudioPath = pendingAudio.absolutePath,
            ),
        )
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(),
            groqRepository = repository,
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = FakeSharedAudioCache(),
            transcribeStateStore = stateStore,
        )

        advanceUntilIdle()

        assertEquals(1, repository.callCount)
        assertEquals("restored share.", viewModel.uiState.value.transcript)
        assertFalse(viewModel.uiState.value.hasPendingSharedAudio)
        assertFalse(pendingAudio.exists())
        clearViewModel(viewModel)
    }

    private fun createTempAudioFile(extension: String = ".m4a"): File =
        File.createTempFile("transcribe-test", extension).apply {
            writeText("audio")
            deleteOnExit()
        }

    private fun clearViewModel(viewModel: TranscribeViewModel) {
        viewModel.javaClass.superclass
            .getMethod("clear\$lifecycle_viewmodel_release")
            .invoke(viewModel)
    }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                block()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeRecorder(
    private val startFile: File = File.createTempFile("start", ".m4a").apply { deleteOnExit() },
    private val stopFile: File? = null,
) : Recorder {
    override fun start(): File = startFile

    override fun stop(): File? = stopFile
}

private class FakeTranscriptionRepository(
    private val result: Result<String> = Result.success(""),
) : TranscriptionRepository {
    var callCount = 0

    override suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        language: String?,
        model: String,
        operationId: String?,
    ): Result<String> {
        callCount += 1
        return result
    }
}

private class FakeApiKeyStore(
    private var apiKey: String?,
    private var model: String = "whisper-large-v3",
) : ApiKeyStore {
    override fun getApiKey(): String? = apiKey

    override fun saveApiKey(key: String) {
        apiKey = key
    }

    override fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

    override fun clearApiKey() {
        apiKey = null
    }

    override fun getModel(): String = model

    override fun saveModel(model: String) {
        this.model = model
    }
}

private class FakeSharedAudioCache(
    private val cachedFile: File? = null,
) : SharedAudioCache {
    val copiedUris = mutableListOf<Uri>()

    override suspend fun copyToCache(uri: Uri, operationId: String?): File? {
        copiedUris += uri
        return cachedFile
    }
}

private class FakeTranscribeStateStore(
    initialState: PersistedTranscribeState = PersistedTranscribeState(),
) : TranscribeStateStore {
    private val stateFlow = MutableStateFlow(initialState)

    override val state: Flow<PersistedTranscribeState> = stateFlow

    override suspend fun saveTranscript(transcript: String) {
        stateFlow.value = stateFlow.value.copy(transcript = transcript)
    }

    override suspend fun clearTranscript() {
        stateFlow.value = stateFlow.value.copy(transcript = "")
    }

    override suspend fun savePendingSharedAudioPath(path: String) {
        stateFlow.value = stateFlow.value.copy(pendingSharedAudioPath = path)
    }

    override suspend fun clearPendingSharedAudio() {
        stateFlow.value = stateFlow.value.copy(pendingSharedAudioPath = null)
    }
}
