package com.smartisan.music.ui.components

import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import com.smartisan.music.R
import kotlin.math.roundToInt

/** Resource-backed five-star control with local drag preview and one committed rating. */
@Composable
internal fun SmartisanRatingBar(
    score: Int,
    onRating: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val tracks =
        remember(context, configuration) {
            listOf(R.drawable.score_empty, R.drawable.score_full).map { resource ->
                (requireNotNull(AppCompatResources.getDrawable(context, resource)).mutate()
                        as BitmapDrawable)
                    .apply {
                        setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
                    }
            }
        }
    val density = LocalDensity.current
    val latestRating = rememberUpdatedState(onRating)
    val latestScore = rememberUpdatedState(score)
    // Keep the state object stable for the lifetime of the pointer-input coroutine.
    var preview by remember { mutableIntStateOf(score) }
    var tracking by remember { mutableStateOf(false) }
    LaunchedEffect(score) {
        if (!tracking) preview = score
    }
    val description = stringResource(R.string.sort_by_song_score)
    Canvas(
        modifier
            .width(with(density) { (tracks[0].intrinsicWidth * 5).toDp() })
            .height(dimensionResource(R.dimen.queue_rating_bar_height))
            .clipToBounds()
            .progressSemantics(preview.toFloat(), 0f..5f, steps = 4)
            .semantics {
                contentDescription = description
                setProgress {
                    latestRating.value(it.roundToInt().coerceIn(0, 5))
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = latestScore.value
                    tracking = true
                    try {
                        val dragStart =
                            awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                preview = smartisanRatingAt(change.position.x, size.width.toFloat())
                                change.consume()
                            }
                        if (dragStart == null) {
                            // A tap is committed on up; vertical list scrolling cancels without
                            // changing rating.
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) {
                                preview = smartisanRatingAt(up.position.x, size.width.toFloat())
                                up.consume()
                                if (preview != start) latestRating.value(preview)
                            } else {
                                preview = latestScore.value
                            }
                        } else {
                            val released =
                                horizontalDrag(dragStart.id) { change ->
                                    preview =
                                        smartisanRatingAt(change.position.x, size.width.toFloat())
                                    change.consume()
                                }
                            if (released) {
                                currentEvent.changes
                                    .firstOrNull { !it.pressed }
                                    ?.let { up ->
                                        preview =
                                            smartisanRatingAt(up.position.x, size.width.toFloat())
                                        up.consume()
                                    }
                            }
                            // RatingBar dispatches once from onStopTrackingTouch, including drag
                            // cancellation.
                            if (preview != start) latestRating.value(preview)
                        }
                    } finally {
                        tracking = false
                    }
                }
            }
    ) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            tracks.forEach { it.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt()) }
            tracks[0].draw(native)
            val save = native.save()
            // Native ProgressBar tiles source-density bitmaps instead of stretching each star.
            native.clipRect(0f, 0f, size.width * preview / 5f, size.height)
            tracks[1].draw(native)
            native.restoreToCount(save)
        }
    }
}

internal fun smartisanRatingAt(x: Float, width: Float): Int {
    if (width <= 0f) return 0
    val rounded = x.roundToInt().toFloat()
    return when {
        rounded < 0 -> 0
        rounded > width -> 5
        else -> ((rounded / width) * 5f + .6f).roundToInt().coerceIn(0, 5)
    }
}
