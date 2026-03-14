package com.perfecttranscribe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.perfecttranscribe.share.SharedAudioIntentResolver
import com.perfecttranscribe.service.TranscribeTileService
import com.perfecttranscribe.ui.AppNavigation
import com.perfecttranscribe.ui.theme.PerfectTranscribeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private val _tileToggleEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val tileToggleEvents = _tileToggleEvents.asSharedFlow()
    }

    private var sharedAudioUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            PerfectTranscribeTheme {
                AppNavigation(
                    sharedAudioUri = sharedAudioUri,
                    onSharedAudioHandled = { sharedAudioUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        setIntent(intent)

        if (intent.getStringExtra(TranscribeTileService.EXTRA_TILE_ACTION) == TranscribeTileService.ACTION_TOGGLE) {
            intent.removeExtra(TranscribeTileService.EXTRA_TILE_ACTION)
            _tileToggleEvents.tryEmit(Unit)
            return
        }

        val audioUri = SharedAudioIntentResolver.extractAudioUri(intent)
        if (audioUri != null) {
            sharedAudioUri = audioUri
            intent.removeExtra(Intent.EXTRA_STREAM)
            intent.clipData = null
            intent.action = null
            intent.data = null
            intent.type = null
        }
    }
}
