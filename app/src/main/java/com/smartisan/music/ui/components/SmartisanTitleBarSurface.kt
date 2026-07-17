package com.smartisan.music.ui.components

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
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
            drawableRes = when (style) {
                SmartisanTitleBarSurfaceStyle.Main -> R.drawable.titlebar_bg
                SmartisanTitleBarSurfaceStyle.Playback -> R.drawable.titlebar_playing_bg
            },
            modifier = Modifier.matchParentSize(),
        )
        content()
    }
}

@Composable
@SuppressLint("LocalContextGetResourceValueCall")
fun SmartisanDrawableBackground(
    @DrawableRes drawableRes: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Canvas(modifier = modifier) {
        val drawable = context.getDrawable(drawableRes) ?: return@Canvas
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { canvas ->
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
