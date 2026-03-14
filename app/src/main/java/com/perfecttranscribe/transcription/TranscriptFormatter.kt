package com.perfecttranscribe.transcription

fun normalizeTranscript(text: String): String {
    val trimmed = text.trim()
    val lastChar = trimmed.lastOrNull() ?: return trimmed

    return if (lastChar.isLetter()) "$trimmed." else trimmed
}
