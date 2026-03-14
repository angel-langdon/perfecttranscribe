package com.perfecttranscribe.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedAudioFormatNormalizerTest {

    @Test
    fun normalizesWhatsAppOpusExtensionToOgg() {
        assertEquals("ogg", SharedAudioFormatNormalizer.normalize("voice-note.opus", null))
    }

    @Test
    fun normalizesAndroidOgaExtensionToOgg() {
        assertEquals("ogg", SharedAudioFormatNormalizer.normalize("shared_audio.oga", null))
    }

    @Test
    fun prefersKnownMimeTypeAlias() {
        assertEquals("ogg", SharedAudioFormatNormalizer.normalize("voice-note.opus", "audio/ogg"))
    }

    @Test
    fun keepsSupportedExtensionWhenNoAliasIsNeeded() {
        assertEquals("m4a", SharedAudioFormatNormalizer.normalize("recording.m4a", "audio/mp4"))
    }

    @Test
    fun returnsNullForMissingCandidate() {
        assertNull(SharedAudioFormatNormalizer.normalize(null, null))
    }
}
