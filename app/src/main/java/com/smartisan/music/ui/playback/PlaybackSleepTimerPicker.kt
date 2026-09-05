package com.smartisan.music.ui.playback

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.smartisan.music.R
import com.smartisan.music.ui.components.smartisanClick
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The calibrated five-row wheel, rendered on Compose's canvas with its existing fling physics. */
@Composable
internal fun PlaybackSleepTimerPicker(
    labels: List<String>,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state = remember(context, labels.size) { SleepTimerWheelState(context, labels.size) }
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onValueChanged)
    val normalColor = resources.getColor(R.color.menu_text_color, context.theme)
    val selectedColor = resources.getColor(R.color.btn_text_color_blue, context.theme)
    val normalSize = resources.getDimensionPixelSize(R.dimen.time_picker_text_size).toFloat()
    val selectedSize =
        resources.getDimensionPixelSize(R.dimen.time_picker_text_size_hight).toFloat()
    val description = resources.getString(R.string.setting_stop_time)
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val click = smartisanClick {}
    fun changed(index: Int) {
        callback.value(index + 1)
        PlaybackHaptics.vibrateEffect(context)
    }
    fun step(delta: Int) {
        state.stop()
        state.motion = scope.launch { state.step(delta, ::changed) }
    }
    DisposableEffect(state) { onDispose { state.stop() } }
    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { state.rowHeight = it.height / 5f }
            .semantics {
                contentDescription = description
                stateDescription = labels[state.index]
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        state.index.toFloat(),
                        0f..labels.lastIndex.toFloat(),
                        labels.size - 2,
                    )
                setProgress { value ->
                    state.stop()
                    state.select(value.roundToInt(), ::changed)
                    true
                }
                scrollBy { _, y ->
                    step(if (y > 0) 1 else -1)
                    true
                }
            }
            .focusable()
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    state.stop()
                    down.consume()
                    val tracker = VelocityTracker.obtain()
                    fun sample(time: Long, y: Float, action: Int) {
                        val event = MotionEvent.obtain(down.uptimeMillis, time, action, 0f, y, 0)
                        tracker.addMovement(event)
                        event.recycle()
                    }
                    sample(down.uptimeMillis, down.position.y, MotionEvent.ACTION_DOWN)
                    var lastY = down.position.y
                    var dragging = false
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            change.historical.forEach {
                                sample(it.uptimeMillis, it.position.y, MotionEvent.ACTION_MOVE)
                            }
                            sample(
                                change.uptimeMillis,
                                change.position.y,
                                if (change.pressed) MotionEvent.ACTION_MOVE
                                else MotionEvent.ACTION_UP,
                            )
                            val delta = change.position.y - lastY
                            lastY = change.position.y
                            if (!change.pressed) {
                                released = true
                                change.consume()
                                if (dragging) {
                                    tracker.computeCurrentVelocity(1_000, state.maximumVelocity)
                                    val velocity = tracker.yVelocity.toInt()
                                    state.motion = scope.launch {
                                        state.flingOrSnap(velocity, ::changed)
                                    }
                                } else {
                                    click()
                                    val rows =
                                        ((lastY - size.height / 2f) / state.rowHeight).roundToInt()
                                    when {
                                        rows > 0 -> step(1)
                                        rows < 0 -> step(-1)
                                    }
                                }
                                break
                            }
                            if (abs(lastY - down.position.y) > state.touchSlop) dragging = true
                            if (dragging) state.scrollBy(delta, ::changed)
                            change.consume()
                        }
                    } finally {
                        tracker.recycle()
                        if (!released) state.motion = scope.launch { state.snap(::changed) }
                    }
                }
            }
    ) {
        val centerY = size.height / 2f
        for (offset in -2..2) {
            val index = state.index + offset
            if (index !in labels.indices) continue
            val y = centerY + offset * state.rowHeight + state.offset
            val fraction = (1f - abs(y - centerY) / state.rowHeight).coerceIn(0f, 1f)
            paint.color = blendWheelColor(normalColor, selectedColor, fraction)
            paint.textSize = normalSize + (selectedSize - normalSize) * fraction
            paint.isFakeBoldText = fraction > 0.92f
            val metrics = paint.fontMetrics
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    labels[index],
                    size.width / 2f,
                    y - (metrics.ascent + metrics.descent) / 2f,
                    paint,
                )
            }
        }
    }
}

private class SleepTimerWheelState(context: Context, private val count: Int) {
    private val configuration = ViewConfiguration.get(context)
    private val flingScroller = Scroller(context, null, true)
    private val adjustScroller = Scroller(context, DecelerateInterpolator(2.5f))
    val touchSlop = configuration.scaledTouchSlop
    val maximumVelocity = (configuration.scaledMaximumFlingVelocity / 8).toFloat()
    private val minimumVelocity = configuration.scaledMinimumFlingVelocity
    var index by mutableIntStateOf(0)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    var rowHeight by mutableFloatStateOf(42f * context.resources.displayMetrics.density)
    var motion: Job? = null

    fun stop() {
        motion?.cancel()
        motion = null
        flingScroller.forceFinished(true)
        adjustScroller.forceFinished(true)
    }

    fun select(requested: Int, changed: (Int) -> Unit) {
        offset = 0f
        val next = requested.coerceIn(0, count - 1)
        if (next != index) {
            index = next
            changed(next)
        }
    }

    fun scrollBy(delta: Float, changed: (Int) -> Unit): Boolean {
        if (delta == 0f) return true
        var next = offset + delta
        if ((index == 0 && next > 0f) || (index == count - 1 && next < 0f)) next = 0f
        val moved = next != offset
        offset = next
        while (offset >= rowHeight / 2f) {
            if (index == 0) {
                offset = 0f
                break
            }
            index--
            changed(index)
            offset -= rowHeight
        }
        while (offset <= -rowHeight / 2f) {
            if (index == count - 1) {
                offset = 0f
                break
            }
            index++
            changed(index)
            offset += rowHeight
        }
        return moved
    }

    suspend fun flingOrSnap(velocity: Int, changed: (Int) -> Unit) {
        if (abs(velocity) > minimumVelocity) {
            flingScroller.fling(0, 0, 0, velocity, 0, 0, -0x3fffffff, 0x3fffffff)
            runScroller(flingScroller, changed)
        }
        snap(changed)
    }

    suspend fun step(delta: Int, changed: (Int) -> Unit) {
        if (index + delta !in 0 until count) {
            snap(changed)
            return
        }
        flingScroller.startScroll(0, 0, 0, (-delta * rowHeight).roundToInt(), 300)
        runScroller(flingScroller, changed)
        snap(changed)
    }

    suspend fun snap(changed: (Int) -> Unit) {
        if (abs(offset) >= 0.5f) {
            adjustScroller.startScroll(0, 0, 0, (-offset).roundToInt(), 800)
            runScroller(adjustScroller, changed)
        }
        offset = 0f
    }

    private suspend fun runScroller(scroller: Scroller, changed: (Int) -> Unit) {
        var previousY = 0
        while (!scroller.isFinished) {
            withFrameNanos {}
            if (!scroller.computeScrollOffset()) break
            val nextY = scroller.currY
            if (!scrollBy((nextY - previousY).toFloat(), changed)) scroller.forceFinished(true)
            previousY = nextY
        }
    }
}

private fun blendWheelColor(from: Int, to: Int, fraction: Float): Int =
    Color.argb(
        Color.alpha(from) + ((Color.alpha(to) - Color.alpha(from)) * fraction).roundToInt(),
        Color.red(from) + ((Color.red(to) - Color.red(from)) * fraction).roundToInt(),
        Color.green(from) + ((Color.green(to) - Color.green(from)) * fraction).roundToInt(),
        Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * fraction).roundToInt(),
    )
