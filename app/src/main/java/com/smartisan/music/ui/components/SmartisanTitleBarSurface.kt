package com.smartisan.music.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartisan.music.R

enum class SmartisanTitleBarSurfaceStyle {
    Main,
    Playback,
}

@Composable
fun SmartisanTitleBarSurface(
    style: SmartisanTitleBarSurfaceStyle,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier) {
        SmartisanDrawableBackground(
            drawableRes =
                when (style) {
                    SmartisanTitleBarSurfaceStyle.Main -> R.drawable.titlebar_bg
                    SmartisanTitleBarSurfaceStyle.Playback -> R.drawable.titlebar_playing_bg
                },
            modifier = Modifier.matchParentSize(),
        )
        content()
    }
}

@Composable
fun SmartisanDrawableBackground(
    @DrawableRes drawableRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.smartisanPainterBackground(painter = rememberSmartisanDrawablePainter(drawableRes))
    )
}
