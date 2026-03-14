package com.perfecttranscribe.api

import java.io.File

interface TranscriptionRepository {
    suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        language: String? = null,
    ): Result<String>
}
