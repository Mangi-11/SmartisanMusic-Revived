package com.smartisan.music.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Uses the existing reversible range-selection model and the current lazy layout's hit map. */
@Composable
internal fun Modifier.smartisanSlideSelection(
    enabled: Boolean,
    itemAt: (Offset) -> Int?,
    keyAt: (Int) -> String?,
    selectedKeys: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit,
    canStart: (Offset) -> Boolean,
    scrollBy: suspend (Float) -> Unit,
    horizontalActivation: Boolean = false,
    activationThreshold: (Offset) -> Offset = { Offset.Zero },
    edgeItemAt: (top: Boolean) -> Int? = { null },
): Modifier {
    val currentItemAt by rememberUpdatedState(itemAt)
    val currentKeyAt by rememberUpdatedState(keyAt)
    val currentSelected by rememberUpdatedState(selectedKeys)
    val currentChange by rememberUpdatedState(onSelectionChange)
    val currentCanStart by rememberUpdatedState(canStart)
    val currentScroll by rememberUpdatedState(scrollBy)
    val currentThreshold by rememberUpdatedState(activationThreshold)
    val currentEdgeItem by rememberUpdatedState(edgeItemAt)
    return pointerInput(enabled, horizontalActivation) {
        if (!enabled) return@pointerInput
        coroutineScope {
            val gestureScope = this
            awaitEachGesture {
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val index = currentItemAt(down.position) ?: return@awaitEachGesture
                val key = currentKeyAt(index) ?: return@awaitEachGesture
                if (!currentCanStart(down.position)) return@awaitEachGesture
                val model =
                    SlideSelectionModel().apply { begin(index, key, key in currentSelected) }
                val threshold = currentThreshold(down.position)
                var activated = false
                var position = down.position
                fun applySelection() {
                    val point =
                        Offset(
                            position.x.coerceIn(0f, (size.width - 1).coerceAtLeast(0).toFloat()),
                            position.y.coerceIn(0f, (size.height - 1).coerceAtLeast(0).toFloat()),
                        )
                    val through =
                        when {
                            position.y < 0f -> currentEdgeItem(true) ?: currentItemAt(point)
                            position.y >= size.height ->
                                currentEdgeItem(false) ?: currentItemAt(point)
                            else -> currentItemAt(point)
                        } ?: return
                    model
                        .changesThrough(through, currentKeyAt) { it in currentSelected }
                        .forEach { currentChange(it.key, it.selected) }
                }
                val autoScroll = gestureScope.launch {
                    var previousFrame = withFrameNanos { it }
                    while (true) {
                        val frame = withFrameNanos { it }
                        val frameRatio = ((frame - previousFrame) / 16_000_000f).coerceAtMost(3f)
                        previousFrame = frame
                        if (
                            !activated ||
                                abs(position.y - down.position.y) < viewConfiguration.touchSlop * 2
                        )
                            continue
                        val edge = max(size.height * .18f, viewConfiguration.touchSlop * 2)
                        val overflow =
                            when {
                                position.y < edge -> position.y - edge
                                position.y > size.height - edge -> position.y - (size.height - edge)
                                else -> 0f
                            }
                        if (overflow != 0f) {
                            val step =
                                (36 * (abs(overflow) / edge).coerceIn(0f, 1f))
                                    .roundToInt()
                                    .coerceAtLeast(6)
                            currentScroll((if (overflow < 0) -step else step) * frameRatio)
                            applySelection()
                        }
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        position = change.position
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            if (activated) change.consume()
                            break
                        }
                        if (!activated) {
                            val dx = abs(position.x - down.position.x)
                            val dy = abs(position.y - down.position.y)
                            if (horizontalActivation) {
                                val horizontal = max(threshold.x, viewConfiguration.touchSlop)
                                val vertical = max(threshold.y, viewConfiguration.touchSlop)
                                if (dy >= vertical && dx < horizontal) break
                                activated = dx >= horizontal
                            } else
                                activated =
                                    dx > viewConfiguration.touchSlop ||
                                        dy > viewConfiguration.touchSlop ||
                                        currentItemAt(position) != index
                        }
                        if (activated) {
                            change.consume()
                            applySelection()
                        }
                    }
                } finally {
                    autoScroll.cancel()
                    model.reset()
                }
            }
        }
    }
}
