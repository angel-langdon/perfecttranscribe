package com.perfecttranscribe.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFormatterTest {

    @Test
    fun addsPeriodWhenTranscriptEndsWithLetter() {
        assertEquals("hello world.", normalizeTranscript(" hello world "))
    }

    @Test
    fun addsPeriodWhenTranscriptEndsWithSpanishLetter() {
        assertEquals("canción.", normalizeTranscript(" canción "))
    }

    @Test
    fun keepsExistingTerminalPunctuation() {
        assertEquals("hola?", normalizeTranscript(" hola? "))
    }
}
