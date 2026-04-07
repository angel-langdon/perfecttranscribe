# PerfectTranscribe

PerfectTranscribe is a native Android app for fast speech-to-text transcription with the Groq API. It can record from the microphone, accept shared audio or video from other apps, and return clean transcript text that is easy to copy and reuse.

## What the app does

- Records audio from the device microphone and sends it to Groq for transcription.
- Accepts `SEND` and `SEND_MULTIPLE` share intents for `audio/*`, `video/*`, `application/ogg`, and `application/x-ogg`.
- Imports shared media from apps like WhatsApp and can normalize or transcode unsupported formats to `m4a` before upload.
- Removes the video track when a shared video file is used, then transcribes the extracted audio.
- Lets the user choose between these Groq models: `whisper-large-v3`, `whisper-large-v3-turbo`, and `distil-whisper-large-v3-en`.
- Supports custom vocabulary hints, passed to Groq as the transcription prompt.
- Stores the Groq API key in `EncryptedSharedPreferences`.
- Persists the last transcript and any queued shared-audio job with `DataStore`.
- Automatically resumes a queued shared-media transcription after the API key is added or after app restart.
- Normalizes returned transcript text by trimming whitespace and adding terminal punctuation when needed.
- Lets the user copy the transcript to the clipboard or clear it from the main screen.
- Exposes a Quick Settings tile that can start/stop recording and auto-copy the finished transcript.
- Includes a home-screen widget and foreground recording service path that can record and transcribe without opening the main UI.

## Main user flows

### 1. In-app recording

Open the app, grant microphone permission, tap the mic button, then tap stop to upload the recording to Groq. While recording, the UI shows an elapsed timer. While uploading, the UI shows a transcription progress state.

### 2. Share from another app

Share an audio file or video into PerfectTranscribe. The app:

- extracts the first shared item
- copies or transcodes it into app storage
- uploads it to Groq
- shows the transcript in the main screen

If the API key is missing, the imported file is cached, Settings opens automatically, and transcription resumes after the key is saved.

### 3. Quick Settings tile

The Quick Settings tile opens the app and toggles recording. When transcription finishes from the tile flow, the transcript is copied to the clipboard automatically and the app moves to the background so you can paste into the previous app.

### 4. Home-screen widget

The project includes a Glance app widget backed by a foreground `TranscriptionService`. This path is separate from the main Compose/ViewModel flow and is optimized for quick capture from the home screen:

- tap the widget to start recording immediately, without opening the app UI
- tap the widget again or use the notification action to stop recording
- the service uploads the audio to Groq and copies the finished transcript to the clipboard automatically
- if Android Usage Access is enabled for the app, PerfectTranscribe attempts to reopen the previously used app so you can paste immediately

This flow assumes microphone permission and a Groq API key were already configured in the main app once. If either is missing, the widget shows a toast and does not enter a false recording state.

## Supported shared-media handling

The import pipeline is designed to cope with common Android share targets and messaging-app exports:

- preferred pass-through formats: `m4a`, `mp4`
- normalized aliases: `oga -> ogg`, `opus -> ogg`
- known MIME handling includes `audio/mp4`, `audio/ogg`, `application/ogg`, `application/x-ogg`, `video/mp4`
- non-preferred audio is transcoded to AAC in an `m4a` container
- shared videos are transcoded to audio-only `m4a`

`SEND_MULTIPLE` is supported, but the app currently transcribes only the first shared item.

## Requirements

- Android 8.0+ (`minSdk = 26`)
- Android SDK 35 to build
- Java 17
- A Groq API key from `console.groq.com`
- Network access for API calls

## Permissions used

- `RECORD_AUDIO`
- `INTERNET`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MICROPHONE`
- `POST_NOTIFICATIONS`
- `PACKAGE_USAGE_STATS` for best-effort return to the previously used app after widget transcription

## Building

```bash
./gradlew assembleDebug
```

Install the debug build with:

```bash
./gradlew installDebug
```

The repo also includes [`redeploy.sh`](./redeploy.sh), a local helper script that builds, installs, and launches the debug APK on a connected device. If multiple devices are attached, pass a serial or choose one interactively.

Release builds are optimized, but they are only installable when signed with a release keystore. To enable that locally, copy `keystore.properties.example` to `keystore.properties`, fill in your signing values, and run:

```bash
./gradlew assembleRelease
```

## Testing

Unit tests:

```bash
./gradlew test
```

Instrumentation tests:

```bash
./gradlew connectedAndroidTest
```

The test suite covers transcript normalization, share-intent resolution, media import planning, and key ViewModel flows such as queued shared-audio recovery.

## Tech stack

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Retrofit + OkHttp
- Kotlinx Serialization
- DataStore
- EncryptedSharedPreferences
- Glance App Widgets
- Media3 Transformer

## Project status

The main supported features today are:

- microphone recording and transcription from the app UI
- sharing audio/video into the app
- Quick Settings tile capture with auto-copy
- home-screen widget capture with direct background recording, auto-copy, and best-effort return to the previous app
- encrypted API key storage
- model selection and vocabulary hints
- transcript and queued-share persistence across restarts
