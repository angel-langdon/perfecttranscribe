package com.perfecttranscribe.audio

import android.Manifest
import androidx.annotation.RequiresPermission
import java.io.File

interface Recorder {
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(): File

    fun stop(): File?
}
