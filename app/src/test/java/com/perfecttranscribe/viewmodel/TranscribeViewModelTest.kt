package com.perfecttranscribe.viewmodel

import com.perfecttranscribe.api.TranscriptionRepository
import com.perfecttranscribe.audio.Recorder
import com.perfecttranscribe.di.ApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TranscribeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startRecordingWithoutApiKeyShowsErrorAndDoesNotRecord() = runTest(testDispatcher) {
        val recorder = FakeRecorder()
        val viewModel = TranscribeViewModel(
            audioRecorder = recorder,
            groqRepository = FakeTranscriptionRepository(),
            apiKeyManager = FakeApiKeyStore(apiKey = null),
        )

        val started = viewModel.startRecording()

        assertFalse(started)
        assertFalse(recorder.startCalled)
        assertEquals("Please set your Groq API key in Settings", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isRecording)
    }

    @Test
    fun startRecordingWithApiKeyStartsTimer() = runTest(testDispatcher) {
        val recorder = FakeRecorder(startFile = createTempAudioFile())
        val viewModel = TranscribeViewModel(
            audioRecorder = recorder,
            groqRepository = FakeTranscriptionRepository(),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
        )

        val started = viewModel.startRecording()
        advanceTimeBy(1_000)

        assertTrue(started)
        assertTrue(recorder.startCalled)
        assertTrue(viewModel.uiState.value.isRecording)
        assertEquals(1, viewModel.uiState.value.recordingSeconds)

        viewModel.stopRecording()
        advanceUntilIdle()
    }

    @Test
    fun startRecordingWithAutoCopyStoresFlag() = runTest(testDispatcher) {
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(startFile = createTempAudioFile()),
            groqRepository = FakeTranscriptionRepository(),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
        )

        val started = viewModel.startRecording(autoCopy = true)

        assertTrue(started)
        assertTrue(viewModel.uiState.value.autoCopyToClipboard)

        viewModel.stopRecording()
        advanceUntilIdle()
    }

    @Test
    fun stopRecordingTranscribesAndDeletesFile() = runTest(testDispatcher) {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" hello world ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("hello world.", viewModel.uiState.value.transcript)
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertFalse(audioFile.exists())
    }

    @Test
    fun stopRecordingAddsPeriodWhenTranscriptEndsWithSpanishLetter() = runTest(testDispatcher) {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" canción ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("canción.", viewModel.uiState.value.transcript)
        assertFalse(audioFile.exists())
    }

    @Test
    fun stopRecordingKeepsExistingTerminalPunctuation() = runTest(testDispatcher) {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(result = Result.success(" hola? ")),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("hola?", viewModel.uiState.value.transcript)
        assertFalse(audioFile.exists())
    }

    @Test
    fun stopRecordingShowsTranscriptionError() = runTest(testDispatcher) {
        val audioFile = createTempAudioFile()
        val viewModel = TranscribeViewModel(
            audioRecorder = FakeRecorder(stopFile = audioFile),
            groqRepository = FakeTranscriptionRepository(
                result = Result.failure(IllegalStateException("Groq failed")),
            ),
            apiKeyManager = FakeApiKeyStore(apiKey = "gsk_test"),
        )

        viewModel.stopRecording()
        advanceUntilIdle()

        assertEquals("Groq failed", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertFalse(audioFile.exists())
    }

    private fun createTempAudioFile(): File =
        File.createTempFile("transcribe-test", ".m4a").apply {
            writeText("audio")
            deleteOnExit()
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
    override suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        language: String?,
    ): Result<String> = result
}

private class FakeApiKeyStore(
    private var apiKey: String?,
) : ApiKeyStore {
    override fun getApiKey(): String? = apiKey

    override fun saveApiKey(key: String) {
        apiKey = key
    }

    override fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

    override fun clearApiKey() {
        apiKey = null
    }
}
