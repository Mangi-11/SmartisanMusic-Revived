package com.smartisan.music.ui.playback

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import com.smartisan.music.ui.components.SmartisanTitleBarSurface
import com.smartisan.music.ui.components.SmartisanTitleBarSurfaceStyle
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun PlaybackTopBar(
    title: String,
    artist: String,
    topInset: Dp,
    onCollapse: () -> Unit,
) {
    SmartisanTitleBarSurface(
        style = SmartisanTitleBarSurfaceStyle.Playback,
        modifier = Modifier.fillMaxWidth().height(topInset + 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(top = topInset, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressedDrawableButton(
                normalRes = R.drawable.btn_playing_back,
                pressedRes = R.drawable.btn_playing_back_down,
                contentDescription = stringResource(R.string.collapse_player),
                modifier = Modifier.width(40.dp).height(30.dp),
                onClick = onCollapse,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = PlaybackTitleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = PlaybackArtistStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Box(modifier = Modifier.width(40.dp).height(30.dp))
        }
    }
}

@Composable
internal fun PlaybackTimeSeekBar(
    durationMs: Long,
    currentPositionMs: Long,
    @DrawableRes thumbRes: Int,
    modifier: Modifier = Modifier,
    onSeek: (Long) -> Unit,
) {
    val duration = durationMs.coerceAtLeast(0L)
    val currentFraction =
        if (duration > 0L) {
                currentPositionMs.toFloat() / duration.toFloat()
            } else {
                0f
            }
            .coerceIn(0f, 1f)
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current
    val shownFraction = if (dragFraction.isNaN()) currentFraction else dragFraction.coerceIn(0f, 1f)
    val shownPosition =
        if (duration > 0L) {
            (shownFraction * duration.toFloat()).roundToLong()
        } else {
            0L
        }
    val trackProgress = (shownFraction * PlaybackSeekBarProgressMax.toFloat()).roundToInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(PlaybackSeekBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlaybackTime(shownPosition),
                style = PlaybackTimeStyle.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = Modifier.width(PlaybackSeekBarHorizontalPadding),
            )
            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .progressSemantics(shownFraction)
                        .onSizeChanged { trackWidthPx = it.width }
                        .pointerInput(duration) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (trackWidthPx > 0) {
                                        dragFraction = fractionFromPosition(offset.x, trackWidthPx)
                                        if (duration > 0L) {
                                            onSeek(
                                                (dragFraction * duration.toFloat()).roundToLong()
                                            )
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (trackWidthPx > 0) {
                                        dragFraction =
                                            fractionFromPosition(change.position.x, trackWidthPx)
                                        if (duration > 0L) {
                                            onSeek(
                                                (dragFraction * duration.toFloat()).roundToLong()
                                            )
                                        }
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    val finalFraction =
                                        dragFraction.takeUnless { it.isNaN() } ?: currentFraction
                                    if (duration > 0L) {
                                        onSeek((finalFraction * duration.toFloat()).roundToLong())
                                    }
                                    dragFraction = Float.NaN
                                },
                                onDragCancel = {
                                    dragFraction = Float.NaN
                                },
                            )
                        }
            ) {
                Image(
                    painter =
                        rememberSmartisanDrawablePainter(
                            R.drawable.seekbar_progress,
                            progressLevel = trackProgress,
                        ),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(PlaybackSeekTrackDrawableHeight)
                            .align(Alignment.Center),
                )
                Image(
                    painter = painterResource(thumbRes),
                    contentDescription = null,
                    modifier =
                        Modifier.width(PlaybackSeekThumbWidth)
                            .height(PlaybackSeekThumbHeight)
                            .align(Alignment.CenterStart)
                            .offset {
                                val thumbWidthPx =
                                    with(density) { PlaybackSeekThumbWidth.roundToPx() }
                                IntOffset(
                                    x =
                                        ((trackWidthPx * shownFraction) - (thumbWidthPx / 2f))
                                            .roundToInt(),
                                    y = 0,
                                )
                            },
                )
            }
            Text(
                text = "-${formatPlaybackTime((duration - shownPosition).coerceAtLeast(0L))}",
                style = PlaybackTimeStyle.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = Modifier.width(PlaybackSeekBarHorizontalPadding),
            )
        }
        AndroidDrawableImage(
            drawableRes = R.drawable.playing_progress_bar_line,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(PlaybackSeekBarDividerHeight),
        )
    }
}

private const val PlaybackSeekBarProgressMax = 10_000

@Composable
internal fun PlaybackControlButtons(
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    controlWidth: Dp,
    entranceTimeMillis: Float,
    onRepeatClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
) {
    val buttonMetrics = playbackControlButtonMetrics(controlWidth)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = PlaybackControlButtonsTopPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val repeatIconRes = playbackRepeatButtonRes(repeatMode)
        PressedDrawableButton(
            normalRes = repeatIconRes,
            pressedRes = repeatIconRes,
            contentDescription = stringResource(repeatContentDescriptionRes(repeatMode)),
            modifier =
                Modifier.width(buttonMetrics.outerWidth)
                    .height(buttonMetrics.height)
                    .then(
                        Modifier.playbackControlEntrance(
                            timeMillis = entranceTimeMillis,
                            delayMillis = PlaybackOuterButtonAlphaDelayMillis,
                            durationMillis = PlaybackOuterButtonAlphaDurationMillis,
                            offsetY = PlaybackControlEntranceOffset,
                            animateY = false,
                        )
                    ),
            onClick = onRepeatClick,
        )
        PressedDrawableButton(
            normalRes = R.drawable.btn_playing_prev,
            pressedRes = R.drawable.btn_playing_prev_down,
            contentDescription = stringResource(R.string.previous_song),
            modifier =
                Modifier.width(buttonMetrics.sideWidth)
                    .height(buttonMetrics.height)
                    .then(
                        Modifier.playbackControlEntrance(
                            timeMillis = entranceTimeMillis,
                            delayMillis = PlaybackSideButtonEntranceDelayMillis,
                            durationMillis = PlaybackControlEntranceDurationMillis,
                            offsetY = PlaybackControlEntranceOffset,
                        )
                    ),
            onClick = onPreviousClick,
        )
        PressedDrawableButton(
            normalRes =
                if (isPlaying) {
                    R.drawable.btn_playing_pause
                } else {
                    R.drawable.btn_playing_play
                },
            pressedRes =
                if (isPlaying) {
                    R.drawable.btn_playing_pause_down
                } else {
                    R.drawable.btn_playing_play_down
                },
            contentDescription =
                if (isPlaying) {
                    stringResource(R.string.pause)
                } else {
                    stringResource(R.string.play)
                },
            modifier =
                Modifier.width(buttonMetrics.playWidth)
                    .height(buttonMetrics.height)
                    .then(
                        Modifier.playbackControlEntrance(
                            timeMillis = entranceTimeMillis,
                            delayMillis = PlaybackPlayButtonEntranceDelayMillis,
                            durationMillis = PlaybackControlEntranceDurationMillis,
                            offsetY = PlaybackControlEntranceOffset,
                        )
                    ),
            onClick = onPlayPauseClick,
        )
        PressedDrawableButton(
            normalRes = R.drawable.btn_playing_next,
            pressedRes = R.drawable.btn_playing_next_down,
            contentDescription = stringResource(R.string.next_song),
            modifier =
                Modifier.width(buttonMetrics.sideWidth)
                    .height(buttonMetrics.height)
                    .then(
                        Modifier.playbackControlEntrance(
                            timeMillis = entranceTimeMillis,
                            delayMillis = PlaybackSideButtonEntranceDelayMillis,
                            durationMillis = PlaybackControlEntranceDurationMillis,
                            offsetY = PlaybackControlEntranceOffset,
                        )
                    ),
            onClick = onNextClick,
        )
        PressedDrawableButton(
            normalRes =
                if (shuffleEnabled) {
                    R.drawable.btn_playing_shuffle_on
                } else {
                    R.drawable.btn_playing_shuffle_off
                },
            pressedRes =
                if (shuffleEnabled) {
                    R.drawable.btn_playing_shuffle_on
                } else {
                    R.drawable.btn_playing_shuffle_off
                },
            contentDescription = stringResource(R.string.shuffle),
            modifier =
                Modifier.width(buttonMetrics.outerWidth)
                    .height(buttonMetrics.height)
                    .then(
                        Modifier.playbackControlEntrance(
                            timeMillis = entranceTimeMillis,
                            delayMillis = PlaybackOuterButtonAlphaDelayMillis,
                            durationMillis = PlaybackOuterButtonAlphaDurationMillis,
                            offsetY = PlaybackControlEntranceOffset,
                            animateY = false,
                        )
                    ),
            onClick = onShuffleClick,
        )
    }
}

private data class PlaybackControlButtonMetrics(
    val outerWidth: Dp,
    val sideWidth: Dp,
    val playWidth: Dp,
    val height: Dp,
)

internal val PlaybackBottomControlsMinimumWidth =
    (OriginalTurntableBaseWidthDp * PlaybackMinimumTouchTargetSize.value /
            PlaybackControlOuterButtonBaseWidthDp)
        .dp

private fun playbackControlButtonMetrics(controlWidth: Dp): PlaybackControlButtonMetrics {
    val width = controlWidth.value
    return when {
        width >= 432f ->
            PlaybackControlButtonMetrics(
                outerWidth = 77.dp,
                sideWidth = 84.dp,
                playWidth = 102.3.dp,
                height = 104.5.dp,
            )
        width >= 411f ->
            PlaybackControlButtonMetrics(
                outerWidth = 77.dp,
                sideWidth = 80.1.dp,
                playWidth = 98.dp,
                height = 99.5.dp,
            )
        else -> {
            val scale = width / OriginalTurntableBaseWidthDp
            PlaybackControlButtonMetrics(
                outerWidth = PlaybackControlOuterButtonBaseWidthDp.dp * scale,
                sideWidth = PlaybackControlSideButtonBaseWidthDp.dp * scale,
                playWidth = PlaybackControlPlayButtonBaseWidthDp.dp * scale,
                height = PlaybackControlButtonBaseHeightDp.dp * scale,
            )
        }
    }
}

private const val PlaybackControlOuterButtonBaseWidthDp = 67.3f
private const val PlaybackControlSideButtonBaseWidthDp = 70f
private const val PlaybackControlPlayButtonBaseWidthDp = 85.3f
private const val PlaybackControlButtonBaseHeightDp = 87f

@Composable
private fun Modifier.playbackControlEntrance(
    timeMillis: Float,
    delayMillis: Int,
    durationMillis: Int,
    offsetY: Dp,
    animateY: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val progress =
        playbackEntranceProgress(
            timeMillis = timeMillis,
            delayMillis = delayMillis,
            durationMillis = durationMillis,
        )
    val offsetYPx =
        with(density) {
            offsetY.roundToPx().toFloat()
        }
    return graphicsLayer {
        if (animateY) {
            translationY = (1f - progress) * offsetYPx
        }
        alpha = if (animateY) 1f else progress
    }
}

@Composable
internal fun PlaybackVolumeBar(
    value: Float,
    width: Dp,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { PlaybackVolumeHorizontalPadding.roundToPx() }
    val thumbOffsetPx = with(density) { PlaybackVolumeThumbOffset.roundToPx() }
    val thumbTouchPaddingPx = with(density) { 4.dp.toPx() }
    val progress = (value.coerceIn(0f, 1f) * 100f).roundToInt()
    val latestProgress = rememberUpdatedState(progress)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    var pressed by remember { mutableStateOf(false) }
    val track =
        rememberSmartisanDrawablePainter(
            R.drawable.volume_seekbar_progress,
            pressed = pressed,
            progressLevel = progress * 100,
        )
    val thumb =
        rememberSmartisanDrawablePainter(R.drawable.playing_control_volume, pressed = pressed)
    val description = stringResource(R.string.volume)
    Canvas(
        modifier
            .width(width)
            .height(PlaybackVolumeBarHeight)
            .progressSemantics(progress / 100f, steps = 99)
            .semantics {
                contentDescription = description
                setProgress { requested ->
                    latestOnValueChange.value(
                        (requested.coerceIn(0f, 1f) * 100f).roundToInt() / 100f
                    )
                    true
                }
            }
            .focusable()
            .pointerInput(horizontalPaddingPx, thumbOffsetPx, thumbTouchPaddingPx, thumb) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val available = size.width - horizontalPaddingPx * 2
                    val thumbWidth = thumb.intrinsicSize.width
                    if (
                        available <= 0 ||
                            !playbackVolumeThumbHit(
                                down.position.x,
                                horizontalPaddingPx.toFloat(),
                                available.toFloat(),
                                latestProgress.value / 100f,
                                thumbWidth,
                                thumbTouchPaddingPx,
                            )
                    )
                        return@awaitEachGesture
                    // AbsSeekBar preserves a small grab offset within its explicit 5dp thumb
                    // offset.
                    val grabOffset =
                        (latestProgress.value / 100f -
                                (down.position.x - horizontalPaddingPx) / available)
                            .takeIf {
                                abs(it * available) <= thumbOffsetPx
                            } ?: 0f
                    fun update(x: Float) {
                        latestOnValueChange.value(
                            playbackVolumeTouchProgress(
                                x,
                                horizontalPaddingPx.toFloat(),
                                available.toFloat(),
                                grabOffset,
                            ) / 100f
                        )
                    }
                    down.consume()
                    pressed = true
                    try {
                        update(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            update(change.position.x)
                            change.consume()
                        } while (change.pressed)
                    } finally {
                        pressed = false
                    }
                }
            }
    ) {
        val available = (size.width - horizontalPaddingPx * 2).coerceAtLeast(0f)
        val thumbSize = thumb.intrinsicSize
        // ProgressBar's unstyled maxHeight starts at 48px and grows to drawable.minimumHeight.
        val trackHeight = minOf(size.height, maxOf(48f, track.intrinsicSize.height))
        val trackTop: Int
        val thumbTop: Int
        if (thumbSize.height > trackHeight) {
            thumbTop = ((size.height - thumbSize.height) / 2).toInt()
            trackTop = thumbTop + ((thumbSize.height - trackHeight) / 2).toInt()
        } else {
            trackTop = ((size.height - trackHeight) / 2).toInt()
            thumbTop = trackTop + ((trackHeight - thumbSize.height) / 2).toInt()
        }
        translate(horizontalPaddingPx.toFloat(), trackTop.toFloat()) {
            with(track) { draw(Size(available, trackHeight)) }
        }
        val thumbLeft =
            playbackVolumeThumbLeft(
                horizontalPaddingPx.toFloat(),
                available,
                thumbSize.width,
                thumbOffsetPx.toFloat(),
                progress / 100f,
            )
        translate(thumbLeft, thumbTop.toFloat()) { with(thumb) { draw(thumbSize) } }
    }
}

internal fun playbackVolumeThumbLeft(
    padding: Float,
    availableWidth: Float,
    thumbWidth: Float,
    thumbOffset: Float,
    fraction: Float,
): Float =
    padding - thumbOffset +
        (fraction * (availableWidth - thumbWidth + 2 * thumbOffset) + 0.5f).toInt()

internal fun playbackVolumeThumbHit(
    x: Float,
    padding: Float,
    availableWidth: Float,
    fraction: Float,
    thumbWidth: Float,
    touchPadding: Float,
): Boolean = abs(x - (padding + availableWidth * fraction)) <= thumbWidth / 2 + touchPadding

internal fun playbackVolumeTouchProgress(
    x: Float,
    padding: Float,
    availableWidth: Float,
    grabOffset: Float,
): Int {
    val roundedX = x.roundToInt().toFloat()
    val fraction =
        when {
            roundedX < padding -> 0f
            roundedX > padding + availableWidth -> 1f
            else -> (roundedX - padding) / availableWidth + grabOffset
        }
    return (fraction * 100f).roundToInt().coerceIn(0, 100)
}
