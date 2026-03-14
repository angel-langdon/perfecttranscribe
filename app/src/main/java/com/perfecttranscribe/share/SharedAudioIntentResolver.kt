package com.perfecttranscribe.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build

object SharedAudioIntentResolver {

    fun extractAudioUri(intent: Intent?): Uri? {
        if (intent == null) return null

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getSharedStreamUri()
                    ?: intent.clipData.firstUri()
                    ?: intent.data
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getSharedStreamUris().firstOrNull()
                    ?: intent.clipData.firstUri()
                    ?: intent.data
            }
            else -> {
                intent.getSharedStreamUri()
                    ?: intent.getSharedStreamUris().firstOrNull()
                    ?: intent.clipData.firstUri()
                    ?: intent.data
            }
        }
    }

    private fun Intent.getSharedStreamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.getSharedStreamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }

    private fun ClipData?.firstUri(): Uri? =
        this
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
}
