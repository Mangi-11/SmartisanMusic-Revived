package com.smartisan.music.ui.playback

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

internal object PlaybackHaptics {
    fun vibrateEffect(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
