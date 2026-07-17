package com.smartisan.music.ui.playback

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun PressedDrawableButton(
    @DrawableRes normalRes: Int,
    @DrawableRes pressedRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AndroidDrawableImage(
            drawableRes = if (pressed) pressedRes else normalRes,
            modifier = Modifier.matchParentSize(),
            contentDescription = contentDescription,
        )
    }
}

@Composable
internal fun AndroidDrawableImage(
    @DrawableRes drawableRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                this.contentDescription = contentDescription
            }
        },
        modifier = modifier,
        update = { imageView ->
            imageView.setImageResource(drawableRes)
            imageView.contentDescription = contentDescription
        },
    )
}
