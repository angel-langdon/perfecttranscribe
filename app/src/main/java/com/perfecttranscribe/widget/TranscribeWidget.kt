package com.perfecttranscribe.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.appwidget.updateAll
import com.perfecttranscribe.service.TranscriptionService

class TranscribeWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val isRecording = prefs[IsRecordingKey] ?: false

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(8.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(
                    actionRunCallback<ToggleRecordingAction>(
                        actionParametersOf(RecordingParam to !isRecording)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRecording) "\u23F9" else "\u23FA",
                    style = TextStyle(fontSize = 28.sp),
                )
                Text(
                    text = if (isRecording) "Stop" else "Transcribe",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }

    companion object {
        val IsRecordingKey = booleanPreferencesKey("is_recording")
        val RecordingParam = ActionParameters.Key<Boolean>("recording")
    }
}

class ToggleRecordingAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val shouldRecord = parameters[TranscribeWidget.RecordingParam] ?: false

        if (shouldRecord) {
            TranscriptionService.startService(context)

            // Launch main activity for actual recording
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("start_recording", true)
                }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        } else {
            TranscriptionService.stopService(context)
        }

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[TranscribeWidget.IsRecordingKey] = shouldRecord
            }
        }
        TranscribeWidget().updateAll(context)
    }
}
