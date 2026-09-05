package com.smartisan.music.ui.artwork

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.library.SmartisanAlbumArtwork
import com.smartisan.music.ui.library.rememberAlbumArtworkLoader
import kotlin.math.min

internal data class AlbumArtworkBrowserState(
    val album: AlbumSummary,
    val sourceBounds: Rect?,
    val onSourceVisibilityChanged: (Boolean) -> Unit = {},
)

@Composable
internal fun AlbumArtworkBrowserOverlay(
    state: AlbumArtworkBrowserState?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var retained by remember { mutableStateOf<AlbumArtworkBrowserState?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state != null) {
            retained = state
            progress.animateTo(1f, tween(420, easing = ArtworkBrowserEasing))
        } else if (retained != null) {
            progress.animateTo(0f, tween(300, easing = ArtworkBrowserEasing))
            retained = null
        }
    }
    val displayed = state ?: retained ?: return
    DisposableEffect(displayed) {
        displayed.onSourceVisibilityChanged(false)
        onDispose { displayed.onSourceVisibilityChanged(true) }
    }
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, clippingEnabled = false),
    ) {
        val loader = rememberAlbumArtworkLoader()
        BoxWithConstraints(modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val sizePx = min(widthPx, heightPx)
            val end =
                Rect(
                    (widthPx - sizePx) / 2,
                    (heightPx - sizePx) / 2,
                    (widthPx + sizePx) / 2,
                    (heightPx + sizePx) / 2,
                )
            val start =
                displayed.sourceBounds?.takeIf { it.width > 0 && it.height > 0 }
                    ?: Rect(
                        end.left + sizePx * .03f,
                        end.top + sizePx * .03f,
                        end.right - sizePx * .03f,
                        end.bottom - sizePx * .03f,
                    )
            val startScale = min(start.width, start.height) / sizePx.coerceAtLeast(1f)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(Color.Black)
                    .clickable(
                        remember { MutableInteractionSource() },
                        null,
                        onClick = onDismissRequest,
                    )
            )
            val side = with(density) { sizePx.toDp() }
            SmartisanAlbumArtwork(
                displayed.album,
                sizePx.toInt().coerceAtLeast(1),
                R.drawable.noalbumcover_220,
                Modifier.size(side)
                    .graphicsLayer {
                        val t = progress.value
                        val r = 1f - t
                        val controlX =
                            (start.center.x + end.center.x) / 2 -
                                (end.center.y - start.center.y) * .18f
                        val controlY =
                            (start.center.y + end.center.y) / 2 +
                                (end.center.x - start.center.x) * .18f
                        val x = r * r * start.center.x + 2 * r * t * controlX + t * t * end.center.x
                        val y = r * r * start.center.y + 2 * r * t * controlY + t * t * end.center.y
                        scaleX = startScale + (1 - startScale) * t
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = x - sizePx * scaleX / 2
                        translationY = y - sizePx * scaleY / 2
                    }
                    .clickable(remember { MutableInteractionSource() }, null, onClick = {})
                    .clearAndSetSemantics {},
                loader,
                ContentScale.Fit,
            )
        }
    }
}

private val ArtworkBrowserEasing = CubicBezierEasing(.25f, .1f, .25f, 1f)
