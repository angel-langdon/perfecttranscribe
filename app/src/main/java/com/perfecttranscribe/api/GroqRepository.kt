package com.perfecttranscribe.api

import com.perfecttranscribe.debug.PipelineLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqRepository @Inject constructor(
    private val api: GroqApi,
) : TranscriptionRepository {

    companion object {
        private val errorJson = Json { ignoreUnknownKeys = true }

        private val MIME_TYPES = mapOf(
            "m4a" to "audio/mp4",
            "mp3" to "audio/mpeg",
            "mp4" to "audio/mp4",
            "mpeg" to "audio/mpeg",
            "mpga" to "audio/mpeg",
            "oga" to "audio/ogg",
            "ogg" to "audio/ogg",
            "opus" to "audio/ogg",
            "wav" to "audio/wav",
            "webm" to "audio/webm",
            "flac" to "audio/flac",
        )
    }

    override suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        language: String?,
        model: String,
        prompt: String?,
        operationId: String?,
    ): Result<String> = try {
        val logId = operationId ?: PipelineLogger.newOperationId("transcribe")
        val requestStartTimeNs = PipelineLogger.now()
        val extension = audioFile.extension.lowercase()
        val mimeType = MIME_TYPES[extension] ?: "audio/mp4"
        val fileName = audioFile.name

        PipelineLogger.log(
            logId,
            "transcribe.request.start",
            "file=$fileName mime=$mimeType size_bytes=${audioFile.length()} model=$model",
        )

        val filePart = MultipartBody.Part.createFormData(
            "file",
            fileName,
            audioFile.asRequestBody(mimeType.toMediaType()),
        )
        val modelPart = model
            .toRequestBody("text/plain".toMediaType())
        val formatPart = "json"
            .toRequestBody("text/plain".toMediaType())
        val langPart = language?.toRequestBody("text/plain".toMediaType())
        val promptPart = prompt?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaType())

        val response = api.transcribe(
            authorization = "Bearer $apiKey",
            file = filePart,
            model = modelPart,
            responseFormat = formatPart,
            language = langPart,
            prompt = promptPart,
        )
        PipelineLogger.logDuration(
            logId,
            "transcribe.request.success",
            requestStartTimeNs,
            "response_chars=${response.text.length}",
        )
        Result.success(response.text)
    } catch (exception: HttpException) {
        operationId?.let { logId ->
            PipelineLogger.log(logId, "transcribe.request.http_error", "code=${exception.code()}")
        }
        Result.failure(
            IllegalStateException(
                parseApiErrorMessage(exception)
                    ?: exception.message()
                    ?: "Transcription failed with HTTP ${exception.code()}",
            ),
        )
    } catch (exception: Exception) {
        operationId?.let { logId ->
            PipelineLogger.log(
                logId,
                "transcribe.request.failed",
                "error=${exception.message ?: exception::class.java.simpleName}",
            )
        }
        Result.failure(exception)
    }

    private fun parseApiErrorMessage(exception: HttpException): String? =
        exception.response()
            ?.errorBody()
            ?.string()
            ?.let { body ->
                runCatching {
                    errorJson.decodeFromString(GroqApiErrorEnvelope.serializer(), body)
                        .error
                        ?.message
                }.getOrNull()
            }
}

@Serializable
private data class GroqApiErrorEnvelope(
    val error: GroqApiError? = null,
)

@Serializable
private data class GroqApiError(
    val message: String? = null,
)
