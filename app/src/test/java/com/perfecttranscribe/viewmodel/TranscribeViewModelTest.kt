package com.perfecttranscribe.viewmodel
import com.perfecttranscribe.api.TranscriptionRepository
import com.perfecttranscribe.audio.Recorder
import com.perfecttranscribe.di.ApiKeyStore
import com.perfecttranscribe.di.PersistedTranscribeState
import com.perfecttranscribe.di.TranscribeStateStore
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TranscribeViewModelTest {

    @Test
    fun startRecordingWithoutApiKeyShowsErrorAndDoesNotRecord() = runViewModelTest {
        val recorder = FakeRecorder()
        val viewModel = TranscribeViewModel(
            audioRecorder = recorder,
            groqRepository = FakeTranscriptionRepository(),
            apiKeyManager = FakeApiKeyStore(apiKey = null),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        val started = viewModel.startRecording()

        assertFalse(started)
        assertFalse(recorder.startCalled)
        assertEquals("Please set your Groq API key in Settings", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRecording)
        clearViewModel(viewModel)
    }

    @Test
    fun startRecordingWithApiKeyBeginsRecordingSession() = runViewModelTest {
        val recorder = FakeRecorder(startFile = createTempAudioFile())
        val viewModel = TranscribeViewModel(
            audioRecorder = recorder,
            groqRepository = FakeTranscriptionRepository(),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        val started = viewModel.startRecording()

        assertTrue(started)
        assertTrue(recorder.startCalled)
        assertTrue(viewModel.uiState.value.isRecording)
        assertEquals(0, viewModel.uiState.value.recordingSeconds)

        viewModel.stopRecording()
        advanceUntilIdle()
        clearViewModel(viewModel)
    }

    @Test
    fun startRecordingWithAutoCopyStoresFlag() = runViewModelTest {
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(startFile = createTempAudioFile()),
            groqRepository = FakeTranscriptionRepository(),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        val started = viewModel.startRecording(autoCopy = true)

        assertTrue(started)
        assertTrue(viewModel.uiState.value.autoCopyToClipboard)
        assertEquals(null, viewModel.uiState.value.activityMessage)

        viewModel.stopRecording()
        advanceUntilIdle()
        clearViewModel(viewModel)
    }

    @Test
    fun stopRecordingShowsTranscribingMessageWhileUploadRuns() = runViewModelTest {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" hello world ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.stopRecording()

        assertEquals("Transcribing…", viewModel.uiState.value.activityMessage)

        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.activityMessage)
        clearViewModel(viewModel)
    }

    @Test
    fun stopRecordingTranscribesAndDeletesFile() = runViewModelTest {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" hello world ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("hello world.", viewModel.uiState.value.transcript)
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertFalse(audioFile.exists())
        clearViewModel(viewModel)
    }

    @Test
    fun stopRecordingAddsPeriodWhenTranscriptEndsWithSpanishLetter() = runViewModelTest {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" canción ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("canción.", viewModel.uiState.value.transcript)
        assertFalse(audioFile.exists())
        clearViewModel(viewModel)
    }

    @Test
    fun stopRecordingKeepsExistingTerminalPunctuation() = runViewModelTest {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" hola? ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("hola?", viewModel.uiState.value.transcript)
        assertFalse(audioFile.exists())
        clearViewModel(viewModel)
    }

    @Test
    fun stopRecordingShowsTranscriptionError() = runViewModelTest {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(
                result = Result.failure(IllegalStateException("Groq failed")),
            ),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
            sharedAudioCache = NoOpSharedAudioCache(),
            transcribeStateStore = FakeTranscribeStateStore(),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("Groq failed", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertFalse(audioFile.exists())
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
    var startCalled = false

    override fun start(): File {
        startCalled = true
        return startFile
    }

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
        prompt: String?,
        operationId: String?,
    ): Result<String> {
        callCount += 1
        return result
    }
}

private class FakeApiKeyStore(
    private var apiKey: String?,
    private var model: String = "whisper-large-v3",
    private var vocabularyHints: String = "",
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

    override fun getVocabularyHints(): String = vocabularyHints

    override fun saveVocabularyHints(hints: String) {
        vocabularyHints = hints
    }
}

private class NoOpSharedAudioCache : com.perfecttranscribe.share.SharedAudioCache {
    override suspend fun copyToCache(uri: android.net.Uri, operationId: String?): File? = null
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
