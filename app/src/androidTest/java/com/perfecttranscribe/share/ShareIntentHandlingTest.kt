package com.perfecttranscribe.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.perfecttranscribe.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentHandlingTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sendIntentResolvesForWhatsAppOggMimeType() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            type = "application/ogg"
        }

        assertTrue(intentResolvesToMainActivity(intent))
    }

    @Test
    fun sendMultipleIntentResolvesForAudioMimeType() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            type = "audio/ogg"
        }

        assertTrue(intentResolvesToMainActivity(intent))
    }

    @Test
    fun sendIntentResolvesForVideoMimeType() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            type = "video/mp4"
        }

        assertTrue(intentResolvesToMainActivity(intent))
    }

    @Test
    fun extractsExtraStreamUri() {
        val uri = Uri.parse("content://example/audio.ogg")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        assertEquals(uri, SharedAudioIntentResolver.extractAudioUri(intent))
    }

    @Test
    fun extractsClipDataUriWhenExtraStreamIsMissing() {
        val uri = Uri.parse("content://example/voice-note")
        val intent = Intent(Intent.ACTION_SEND).apply {
            clipData = ClipData.newRawUri("audio", uri)
        }

        assertEquals(uri, SharedAudioIntentResolver.extractAudioUri(intent))
    }

    @Test
    fun extractsFirstUriFromSendMultiple() {
        val first = Uri.parse("content://example/first.ogg")
        val second = Uri.parse("content://example/second.ogg")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }

        assertEquals(first, SharedAudioIntentResolver.extractAudioUri(intent))
    }

    @Test
    fun fallsBackToDataUriWhenNeeded() {
        val uri = Uri.parse("content://example/audio-from-data.ogg")
        val intent = Intent(Intent.ACTION_SEND).apply {
            data = uri
        }

        assertEquals(uri, SharedAudioIntentResolver.extractAudioUri(intent))
    }

    private fun intentResolvesToMainActivity(intent: Intent): Boolean =
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { resolveInfo ->
                resolveInfo.activityInfo.packageName == context.packageName &&
                    resolveInfo.activityInfo.name == MainActivity::class.java.name
            }
}
