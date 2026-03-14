package com.perfecttranscribe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleTileIntent(intent)
        setContent {
            PerfectTranscribeTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTileIntent(intent)
    }

    private fun handleTileIntent(intent: Intent?) {
        if (intent?.getStringExtra(TranscribeTileService.EXTRA_TILE_ACTION) == TranscribeTileService.ACTION_TOGGLE) {
            intent.removeExtra(TranscribeTileService.EXTRA_TILE_ACTION)
            _tileToggleEvents.tryEmit(Unit)
        }
    }
}
