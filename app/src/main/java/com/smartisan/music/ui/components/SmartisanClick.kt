package com.smartisan.music.ui.components

import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Preserves View.performClick / ListView.performItemClick feedback and the system sound setting.
 */
@Composable
internal fun smartisanClick(onClick: () -> Unit): () -> Unit {
    val host = LocalView.current
    return remember(host, onClick) {
        {
            host.playSoundEffect(SoundEffectConstants.CLICK)
            onClick()
        }
    }
}
