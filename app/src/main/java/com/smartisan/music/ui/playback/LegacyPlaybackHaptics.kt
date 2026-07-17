package com.smartisan.music.ui.playback

import android.content.Context
import android.os.Vibrator
import com.smartisan.music.ui.widgets.legacy.VibratorSmt

internal object LegacyPlaybackHaptics {
    fun vibrateEffect(
        context: Context,
        effect: Int = DefaultEffect,
    ) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        VibratorSmt.vibrateEffect(vibrator, effect)
    }

    private const val DefaultEffect = 2
}
