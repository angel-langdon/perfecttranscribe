package com.perfecttranscribe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.perfecttranscribe.MainActivity
import com.perfecttranscribe.R
import com.perfecttranscribe.api.TranscriptionRepository
import com.perfecttranscribe.audio.Recorder
import com.perfecttranscribe.debug.PipelineLogger
import com.perfecttranscribe.di.ApiKeyStore
import com.perfecttranscribe.navigation.PreviousAppNavigator
import com.perfecttranscribe.transcription.normalizeTranscript
import com.perfecttranscribe.widget.TranscribeWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TranscriptionService : Service() {

    @Inject lateinit var recorder: Recorder
    @Inject lateinit var repository: TranscriptionRepository
    @Inject lateinit var apiKeyStore: ApiKeyStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID = "transcription_channel"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.perfecttranscribe.action.START"
        const val ACTION_STOP = "com.perfecttranscribe.action.STOP"
        const val EXTRA_COPY_TO_CLIPBOARD = "copy_to_clipboard"
        const val EXTRA_RETURN_TO_PACKAGE = "return_to_package"

        var isRecording: Boolean = false
            private set

        fun startService(
            context: Context,
            copyToClipboard: Boolean = false,
            returnToPackage: String? = null,
        ) {
            val intent = Intent(context, TranscriptionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_COPY_TO_CLIPBOARD, copyToClipboard)
                putExtra(EXTRA_RETURN_TO_PACKAGE, returnToPackage)
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TranscriptionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var copyToClipboard = false
    private var returnToPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                copyToClipboard = intent.getBooleanExtra(EXTRA_COPY_TO_CLIPBOARD, false)
                returnToPackage = intent.getStringExtra(EXTRA_RETURN_TO_PACKAGE)
                val hasPermission = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Toast.makeText(this, "Grant microphone permission in the app first", Toast.LENGTH_SHORT).show()
                    syncShortcutState(isRecording = false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (copyToClipboard && apiKeyStore.getApiKey().isNullOrBlank()) {
                    Toast.makeText(this, "Set your Groq API key in Settings first", Toast.LENGTH_SHORT).show()
                    syncShortcutState(isRecording = false)
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    startForeground(NOTIFICATION_ID, createNotification("Recording…"))
                    recorder.start()
                    isRecording = true
                    syncShortcutState(isRecording = true)
                } catch (e: Exception) {
                    isRecording = false
                    syncShortcutState(isRecording = false)
                    try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                isRecording = false
                syncShortcutState(isRecording = false)
                val file = recorder.stop()
                val apiKey = apiKeyStore.getApiKey()

                if (file != null && file.exists() && file.length() > 0 && !apiKey.isNullOrBlank() && copyToClipboard) {
                    val model = apiKeyStore.getModel()
                    val prompt = apiKeyStore.getVocabularyHints().takeIf { it.isNotBlank() }
                    val operationId = PipelineLogger.newOperationId("service-recording")
                    updateNotification("Transcribing…")
                    scope.launch {
                        repository.transcribe(apiKey, file, model = model, prompt = prompt, operationId = operationId)
                            .onSuccess { text ->
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("transcript", normalizeTranscript(text)),
                                )
                                Toast.makeText(this@TranscriptionService, "Transcription copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(this@TranscriptionService, "Transcription failed", Toast.LENGTH_SHORT).show()
                            }
                        file.delete()
                        finishServiceSession()
                    }
                } else {
                    if (copyToClipboard && apiKey.isNullOrBlank()) {
                        Toast.makeText(this, "Set your Groq API key in Settings first", Toast.LENGTH_SHORT).show()
                    }
                    file?.delete()
                    finishServiceSession()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        syncShortcutState(isRecording = false)
        scope.cancel()
    }

    private fun finishServiceSession() {
        syncShortcutState(isRecording = false)
        PreviousAppNavigator.relaunchPackage(this, returnToPackage)
        stopForeground(STOP_FOREGROUND_REMOVE)
        returnToPackage = null
        stopSelf()
    }

    private fun syncShortcutState(isRecording: Boolean) {
        TranscribeTileService.setRecordingState(this, isRecording)
        CoroutineScope(Dispatchers.Default).launch {
            TranscribeWidget.setRecordingState(this@TranscriptionService, isRecording)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TranscriptionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PerfectTranscribe")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }
}
