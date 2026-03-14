package com.perfecttranscribe.ui

import android.Manifest
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.perfecttranscribe.ui.screens.SettingsScreen
import com.perfecttranscribe.ui.screens.TranscribeScreen
import com.perfecttranscribe.viewmodel.TranscribeViewModel

@Composable
fun AppNavigation(
    sharedAudioUri: Uri?,
    onSharedAudioHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val viewModel: TranscribeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(sharedAudioUri) {
        if (sharedAudioUri != null) {
            viewModel.transcribeAudioUri(sharedAudioUri)
            onSharedAudioHandled()
        }
    }

    LaunchedEffect(uiState.shouldOpenSettingsForSharedAudio, currentRoute) {
        if (uiState.shouldOpenSettingsForSharedAudio) {
            if (currentRoute != "settings") {
                navController.navigate("settings")
            }
            viewModel.onSharedAudioSettingsNavigationHandled()
        }
    }

    LaunchedEffect(uiState.isTranscribing, currentRoute) {
        if (uiState.isTranscribing && currentRoute == "settings") {
            navController.popBackStack()
        }
    }

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
                currentModel = viewModel.getModel(),
                hasApiKey = uiState.hasApiKey,
                showSharedAudioNotice = uiState.hasPendingSharedAudio,
                onSaveApiKey = viewModel::saveApiKey,
                onClearApiKey = viewModel::clearApiKey,
                onSelectModel = viewModel::saveModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
