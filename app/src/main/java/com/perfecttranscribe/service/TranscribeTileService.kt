package com.perfecttranscribe.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.perfecttranscribe.MainActivity

class TranscribeTileService : TileService() {

    companion object {
        const val EXTRA_TILE_ACTION = "tile_action"
        const val ACTION_TOGGLE = "toggle"

        var isTileRecording: Boolean = false
            private set

        fun requestUpdate(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, TranscribeTileService::class.java),
            )
        }

        fun setRecordingState(context: Context, isRecording: Boolean) {
            isTileRecording = isRecording
            requestUpdate(context)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TILE_ACTION, ACTION_TOGGLE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        if (isTileRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop"
            tile.subtitle = "Recording…"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Transcribe"
            tile.subtitle = "Tap to record"
        }
        tile.updateTile()
    }
}
