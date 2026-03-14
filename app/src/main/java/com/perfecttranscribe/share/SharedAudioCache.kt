package com.perfecttranscribe.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.perfecttranscribe.debug.PipelineLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface SharedAudioCache {
    suspend fun copyToCache(uri: Uri, operationId: String? = null): File?
}

internal data class SharedMediaImportPlan(
    val shouldTranscodeToM4a: Boolean,
    val removeVideo: Boolean,
    val targetExtension: String = "m4a",
)

internal object SharedAudioFormatNormalizer {
    private val mimeTypeAliases = mapOf(
        "audio/mp4" to "m4a",
        "audio/x-m4a" to "m4a",
        "audio/ogg" to "ogg",
        "application/ogg" to "ogg",
        "application/x-ogg" to "ogg",
        "audio/opus" to "ogg",
        "video/mp4" to "mp4",
    )

    private val extensionAliases = mapOf(
        "oga" to "ogg",
        "ogx" to "ogg",
        "opus" to "ogg",
    )

    fun normalize(extensionOrName: String?, mimeType: String?): String? {
        val normalizedMimeType = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (normalizedMimeType != null) {
            mimeTypeAliases[normalizedMimeType]?.let { return it }
        }

        val extension = extensionOrName
            ?.substringAfterLast('.', extensionOrName)
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.length in 1..5 }
            ?: return null

        return extensionAliases[extension] ?: extension
    }
}

internal object SharedMediaImportPlanner {
    private val preferredAudioExtensions = setOf("m4a", "mp4")

    fun create(
        mimeType: String?,
        displayName: String?,
        pathSegment: String?,
    ): SharedMediaImportPlan {
        val normalizedMimeType = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        val extension = SharedAudioFormatNormalizer.normalize(displayName, normalizedMimeType)
            ?: SharedAudioFormatNormalizer.normalize(pathSegment, normalizedMimeType)
            ?: SharedAudioFormatNormalizer.normalize(normalizedMimeType, normalizedMimeType)
        val isVideo = normalizedMimeType?.startsWith("video/") == true
        val shouldTranscodeToM4a = isVideo || extension !in preferredAudioExtensions

        return SharedMediaImportPlan(
            shouldTranscodeToM4a = shouldTranscodeToM4a,
            removeVideo = isVideo,
        )
    }
}

@Singleton
class AndroidSharedAudioCache @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : SharedAudioCache {

    override suspend fun copyToCache(uri: Uri, operationId: String?): File? {
        val logId = operationId ?: PipelineLogger.newOperationId("shared-media")
        val prepareStartTimeNs = PipelineLogger.now()

        return withContext(Dispatchers.IO) {
            val mimeType = appContext.contentResolver
                .getType(uri)
                ?.substringBefore(';')
                ?.trim()
            val displayName = getDisplayName(uri)
            val plan = SharedMediaImportPlanner.create(
                mimeType = mimeType,
                displayName = displayName,
                pathSegment = uri.lastPathSegment,
            )
            val mode = if (plan.shouldTranscodeToM4a) "transcode_to_m4a" else "copy_as_is"

            PipelineLogger.log(
                logId,
                "shared_media.prepare.start",
                "uri=$uri mime=${mimeType ?: "unknown"} name=${displayName ?: "unknown"} mode=$mode",
            )

            val outputFile = if (plan.shouldTranscodeToM4a) {
                transcodeToPreferredFormat(uri, plan, logId)
            } else {
                copyPreferredAudioToCache(uri, plan.targetExtension, logId)
            }

            if (outputFile == null) {
                PipelineLogger.logDuration(
                    logId,
                    "shared_media.prepare.failed",
                    prepareStartTimeNs,
                )
            } else {
                PipelineLogger.logDuration(
                    logId,
                    "shared_media.prepare.success",
                    prepareStartTimeNs,
                    "output=${outputFile.name} size_bytes=${outputFile.length()}",
                )
            }

            outputFile
        }
    }

    private fun copyPreferredAudioToCache(
        uri: Uri,
        extension: String,
        operationId: String,
    ): File? {
        val copyStartTimeNs = PipelineLogger.now()

        return runCatching {
            val tempFile = File.createTempFile("shared_audio_", ".$extension", mediaStorageDir())
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                }
            }
            PipelineLogger.logDuration(
                operationId,
                "shared_media.copy.success",
                copyStartTimeNs,
                "output=${tempFile.name} size_bytes=${tempFile.length()}",
            )
            tempFile
        }.getOrElse {
            PipelineLogger.log(
                operationId,
                "shared_media.copy.failed",
                "error=${it.message ?: it::class.java.simpleName}",
            )
            null
        }
    }

    private suspend fun transcodeToPreferredFormat(
        uri: Uri,
        plan: SharedMediaImportPlan,
        operationId: String,
    ): File? {
        val outputFile = File.createTempFile(
            "shared_audio_",
            ".${plan.targetExtension}",
            mediaStorageDir(),
        )
        val transcodeStartTimeNs = PipelineLogger.now()

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                        PipelineLogger.logDuration(
                            operationId,
                            "shared_media.transcode.success",
                            transcodeStartTimeNs,
                            "output=${outputFile.name} size_bytes=${outputFile.length()} remove_video=${plan.removeVideo}",
                        )
                        if (continuation.isActive) continuation.resume(outputFile)
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        PipelineLogger.log(
                            operationId,
                            "shared_media.transcode.failed",
                            "error=${exportException.message ?: exportException::class.java.simpleName}",
                        )
                        outputFile.delete()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                val transformer = Transformer.Builder(appContext)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(listener)
                    .build()

                continuation.invokeOnCancellation {
                    outputFile.delete()
                    runCatching { transformer.cancel() }
                }

                runCatching {
                    val mediaItem = MediaItem.fromUri(uri)
                    if (plan.removeVideo) {
                        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                            .setRemoveVideo(true)
                            .build()
                        transformer.start(editedMediaItem, outputFile.absolutePath)
                    } else {
                        transformer.start(mediaItem, outputFile.absolutePath)
                    }
                }.onFailure {
                    PipelineLogger.log(
                        operationId,
                        "shared_media.transcode.start_failed",
                        "error=${it.message ?: it::class.java.simpleName}",
                    )
                    outputFile.delete()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun getDisplayName(uri: Uri): String? =
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex == -1) null else cursor.getString(nameIndex)
        }

    private fun mediaStorageDir(): File =
        File(appContext.noBackupFilesDir, "shared_media").apply {
            mkdirs()
        }
}
