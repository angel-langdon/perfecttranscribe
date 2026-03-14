package com.perfecttranscribe.audio

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : Recorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(): File {
        val file = File.createTempFile("recording_", ".m4a", context.cacheDir)
        outputFile = file

        recorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        return file
    }

    override fun stop(): File? {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            outputFile?.delete()
            outputFile = null
        }
        recorder?.release()
        recorder = null
        return outputFile
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
