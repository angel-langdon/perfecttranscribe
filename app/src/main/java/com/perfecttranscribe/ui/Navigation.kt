package com.perfecttranscribe.ui

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.perfecttranscribe.ui.screens.SettingsScreen
import com.perfecttranscribe.ui.screens.TranscribeScreen
import com.perfecttranscribe.viewmodel.TranscribeViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: TranscribeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = "transcribe") {
        composable("transcribe") {
            TranscribeScreen(
                uiState = uiState,
                onStartRecording = @RequiresPermission(Manifest.permission.RECORD_AUDIO) { autoCopy ->
                    viewModel.startRecording(autoCopy)
                },
                onStopRecording = viewModel::stopRecording,
                onClearTranscript = viewModel::clearTranscript,
                onDismissError = viewModel::dismissError,
                onNavigateToSettings = { navController.navigate("settings") },
            )
        }

        composable("settings") {
            SettingsScreen(
                currentApiKey = viewModel.getApiKey(),
                hasApiKey = uiState.hasApiKey,
                onSaveApiKey = viewModel::saveApiKey,
                onClearApiKey = viewModel::clearApiKey,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
