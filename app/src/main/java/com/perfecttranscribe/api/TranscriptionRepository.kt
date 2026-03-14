package com.perfecttranscribe.api

import java.io.File

interface TranscriptionRepository {
    suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        language: String? = null,
        model: String = "whisper-large-v3",
        prompt: String? = null,
        operationId: String? = null,
    ): Result<String>
}
