package com.perfecttranscribe.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMediaImportPlannerTest {

    @Test
    fun keepsPreferredM4aAudioAsCopyPath() {
        val plan = SharedMediaImportPlanner.create(
            mimeType = "audio/mp4",
            displayName = "voice-note.m4a",
            pathSegment = null,
        )

        assertFalse(plan.shouldTranscodeToM4a)
        assertFalse(plan.removeVideo)
    }

    @Test
    fun transcodesWhatsAppOpusToM4a() {
        val plan = SharedMediaImportPlanner.create(
            mimeType = "audio/ogg",
            displayName = "ptt-20260314.opus",
            pathSegment = null,
        )

        assertTrue(plan.shouldTranscodeToM4a)
        assertFalse(plan.removeVideo)
    }

    @Test
    fun transcodesVideoAndRemovesVideoTrack() {
        val plan = SharedMediaImportPlanner.create(
            mimeType = "video/mp4",
            displayName = "clip.mp4",
            pathSegment = null,
        )

        assertTrue(plan.shouldTranscodeToM4a)
        assertTrue(plan.removeVideo)
    }
}
