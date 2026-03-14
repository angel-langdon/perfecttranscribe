package com.perfecttranscribe.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqRepository @Inject constructor(
    private val api: GroqApi,
) : TranscriptionRepository {
    override suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        language: String?,
    ): Result<String> = runCatching {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "audio.m4a",
            audioFile.asRequestBody("audio/mp4".toMediaType()),
        )
        val modelPart = "whisper-large-v3-turbo"
            .toRequestBody("text/plain".toMediaType())
        val formatPart = "json"
            .toRequestBody("text/plain".toMediaType())
        val langPart = language?.toRequestBody("text/plain".toMediaType())

        val response = api.transcribe(
            authorization = "Bearer $apiKey",
            file = filePart,
            model = modelPart,
            responseFormat = formatPart,
            language = langPart,
        )
        response.text
    }
}
