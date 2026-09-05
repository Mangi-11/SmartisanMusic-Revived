package com.smartisan.music.ui.components

import android.content.res.TypedArray
import android.util.TypedValue
import android.view.ViewConfiguration
import androidx.annotation.StyleableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.smartisan.music.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Draws the theme's vertical scrollbar without reserving space or intercepting list gestures. */
@Composable
internal fun Modifier.smartisanVerticalScrollbar(state: ScrollableState): Modifier {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val style =
        remember(context, configuration) {
            val viewConfiguration = ViewConfiguration.get(context)
            context
                .obtainStyledAttributes(
                    null,
                    R.styleable.SmartisanScrollbar,
                    android.R.attr.listViewStyle,
                    0,
                )
                .let { attributes ->
                    try {
                        ScrollbarStyle(
                            thumb =
                                attributes.scrollbarDrawable(
                                    R.styleable.SmartisanScrollbar_android_scrollbarThumbVertical
                                ),
                            track =
                                attributes.scrollbarDrawable(
                                    R.styleable.SmartisanScrollbar_android_scrollbarTrackVertical
                                ),
                            thickness =
                                attributes.getDimensionPixelSize(
                                    R.styleable.SmartisanScrollbar_android_scrollbarSize,
                                    viewConfiguration.scaledScrollBarSize,
                                ),
                            fadeDelay =
                                attributes
                                    .getInt(
                                        R.styleable
                                            .SmartisanScrollbar_android_scrollbarDefaultDelayBeforeFade,
                                        ViewConfiguration.getScrollDefaultDelay(),
                                    )
                                    .coerceAtLeast(0),
                            fadeDuration =
                                attributes
                                    .getInt(
                                        R.styleable
                                            .SmartisanScrollbar_android_scrollbarFadeDuration,
                                        ViewConfiguration.getScrollBarFadeDuration(),
                                    )
                                    .coerceAtLeast(0),
                            fade =
                                attributes.getBoolean(
                                    R.styleable.SmartisanScrollbar_android_fadeScrollbars,
                                    true,
                                ),
                            alwaysDrawTrack =
                                attributes.getBoolean(
                                    R.styleable
                                        .SmartisanScrollbar_android_scrollbarAlwaysDrawVerticalTrack,
                                    false,
                                ),
                        )
                    } finally {
                        attributes.recycle()
                    }
                }
        }
    val thumb = scrollbarPainter(style.thumb)
    val track = scrollbarPainter(style.track)
    val alpha = remember(state, style) { Animatable(if (style.fade) 0f else 1f) }
    val right = LocalLayoutDirection.current != LayoutDirection.Rtl
    LaunchedEffect(state, style) {
        if (!style.fade) return@LaunchedEffect
        var previousOffset = state.scrollIndicatorState?.scrollOffset
        snapshotFlow { state.isScrollInProgress to state.scrollIndicatorState?.scrollOffset }
            .collectLatest { (scrolling, offset) ->
                val moved = offset != previousOffset
                previousOffset = offset
                if (scrolling || moved || alpha.value > 0f) {
                    alpha.snapTo(1f)
                    delay(style.fadeDelay.toLong())
                    alpha.animateTo(0f, tween(style.fadeDuration, easing = LinearEasing))
                }
            }
    }
    return drawWithContent {
        drawContent()
        val opacity = alpha.value
        if (opacity <= 0f) return@drawWithContent
        // View uses the drawable's intrinsic width, then the configured scrollbar size.
        val drawableWidth = (track ?: thumb)?.intrinsicSize?.width
        val thickness =
            drawableWidth?.takeIf { it.isFinite() && it > 0f }?.roundToInt() ?: style.thickness
        if (thickness <= 0 || size.height <= 0f) return@drawWithContent
        val indicator = state.scrollIndicatorState
        val geometry = indicator?.let {
            smartisanScrollbarGeometry(
                size.height.roundToInt(),
                thickness,
                it.scrollOffset,
                it.contentSize,
                it.viewportSize,
            )
        }
        val left = if (right) size.width - thickness else 0f
        translate(left = left) {
            if (track != null && (geometry != null || style.alwaysDrawTrack)) {
                with(track) { draw(Size(thickness.toFloat(), size.height), alpha = opacity) }
            }
            if (thumb != null && geometry != null) {
                translate(top = geometry.offset.toFloat()) {
                    with(thumb) {
                        draw(Size(thickness.toFloat(), geometry.length.toFloat()), alpha = opacity)
                    }
                }
            }
        }
    }
}

internal data class SmartisanScrollbarGeometry(val offset: Int, val length: Int)

internal fun smartisanScrollbarGeometry(
    viewportLength: Int,
    thickness: Int,
    scrollOffset: Int,
    contentSize: Int,
    viewportSize: Int,
): SmartisanScrollbarGeometry? {
    if (
        viewportLength <= 0 ||
            thickness <= 0 ||
            viewportSize <= 0 ||
            contentSize <= viewportSize ||
            scrollOffset == Int.MAX_VALUE ||
            contentSize == Int.MAX_VALUE ||
            viewportSize == Int.MAX_VALUE
    )
        return null
    val proportion = viewportSize.toDouble() / contentSize
    val length =
        maxOf((viewportLength * proportion).roundToInt(), thickness * 2)
            .coerceAtMost(viewportLength)
    val range = contentSize - viewportSize
    val offset =
        ((viewportLength - length).toDouble() * scrollOffset.coerceIn(0, range) / range)
            .roundToInt()
    return SmartisanScrollbarGeometry(offset, length)
}

@Composable
private fun scrollbarPainter(source: ScrollbarDrawable): Painter? =
    when {
        source.resource != 0 -> rememberSmartisanDrawablePainter(source.resource)
        source.color != null -> remember(source.color) { ColorPainter(Color(source.color)) }
        else -> null
    }

private fun TypedArray.scrollbarDrawable(@StyleableRes index: Int): ScrollbarDrawable {
    val resource = getResourceId(index, 0)
    val value = peekValue(index)
    val color =
        value
            ?.takeIf { it.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT }
            ?.data
    return ScrollbarDrawable(resource, color)
}

private data class ScrollbarDrawable(val resource: Int, val color: Int?)

private data class ScrollbarStyle(
    val thumb: ScrollbarDrawable,
    val track: ScrollbarDrawable,
    val thickness: Int,
    val fadeDelay: Int,
    val fadeDuration: Int,
    val fade: Boolean,
    val alwaysDrawTrack: Boolean,
)
