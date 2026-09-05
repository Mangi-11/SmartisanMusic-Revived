package com.smartisan.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class SmartisanDragRowBounds(val index: Int, val top: Int, val height: Int)

internal data class SmartisanListDrag(
    val source: Int,
    val target: Int,
    val downY: Float,
    val touchOffset: Int,
    val floatingY: Float,
    val rowHeight: Int,
    val layer: GraphicsLayer,
    val moved: Boolean = false,
)

/** Owns only a transient gesture and its recorded drawing; callers own item order and commits. */
@Stable
internal class SmartisanListDragState internal constructor(private val scope: CoroutineScope) {
    var drag by mutableStateOf<SmartisanListDrag?>(null)
        private set

    var settling by mutableStateOf(false)
        private set

    private val layers = mutableMapOf<Int, GraphicsLayer>()
    private var settleJob: Job? = null
    private var generation = 0
    private var disposed = false

    internal fun attach(index: Int, layer: GraphicsLayer) {
        layers[index] = layer
    }

    internal fun detach(index: Int, layer: GraphicsLayer) {
        if (layers[index] === layer) layers.remove(index)
    }

    fun start(index: Int, rowTop: Int, rowHeight: Int, pointerY: Float, shadowTop: Int): Boolean {
        if (disposed || settling) return false
        val layer = layers[index] ?: return false
        generation++
        drag =
            SmartisanListDrag(
                index,
                index,
                pointerY,
                pointerY.toInt() - rowTop + shadowTop,
                (rowTop - shadowTop).toFloat(),
                rowHeight,
                layer,
            )
        return true
    }

    fun move(
        pointerY: Float,
        viewportHeight: Int,
        shadowTop: Int,
        shadowBottom: Int,
        touchSlop: Float,
        targetAt: (Float) -> Int?,
    ) {
        val current = drag ?: return
        if (settling) return
        val moved = current.moved || abs(pointerY - current.downY) > touchSlop
        val floatingY =
            smartisanDragFloatingY(
                pointerY,
                current.touchOffset,
                viewportHeight,
                current.rowHeight,
                shadowTop,
                shadowBottom,
            )
        drag =
            current.copy(
                target = if (moved) targetAt(pointerY) ?: current.target else current.target,
                floatingY = floatingY,
                moved = moved,
            )
    }

    /**
     * Release commits once after settling; cancellation returns to the source without committing.
     */
    fun settle(
        released: Boolean,
        shadowTop: Int,
        rowTop: (Int) -> Int?,
        isCurrent: () -> Boolean,
        onCommit: (source: Int, target: Int) -> Unit,
    ) {
        val finished = drag ?: return
        if (disposed || settling) return
        val commit = released && finished.moved && finished.source != finished.target
        val destination = if (commit) finished.target else finished.source
        val targetY = rowTop(destination)?.minus(shadowTop)?.toFloat() ?: finished.floatingY
        val expectedGeneration = generation
        settling = true
        settleJob = scope.launch {
            Animatable(finished.floatingY).animateTo(
                targetY,
                tween(SmartisanDragSettleMillis, easing = SmartisanDragEasing),
            ) {
                if (isCurrent() && !disposed && generation == expectedGeneration)
                    drag = finished.copy(floatingY = value)
            }
            if (isCurrent() && !disposed && generation == expectedGeneration) {
                drag = null
                settling = false
                if (commit) onCommit(finished.source, finished.target)
            }
        }
    }

    fun reset() {
        generation++
        settleJob?.cancel()
        settleJob = null
        drag = null
        settling = false
    }

    internal fun dispose() {
        disposed = true
        reset()
        layers.clear()
    }
}

@Composable
internal fun rememberSmartisanListDragState(contentKey: Any?): SmartisanListDragState {
    val scope = rememberCoroutineScope()
    val state = remember(contentKey) { SmartisanListDragState(scope) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}

@Composable
internal fun rememberSmartisanDragLayer(state: SmartisanListDragState, index: Int): GraphicsLayer {
    val layer = rememberGraphicsLayer()
    DisposableEffect(state, index, layer) {
        state.attach(index, layer)
        onDispose { state.detach(index, layer) }
    }
    return layer
}

internal fun Modifier.smartisanDragRecording(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

@Composable
internal fun Modifier.smartisanDragItem(
    state: SmartisanListDragState,
    index: Int,
    rowHeight: Int,
    contentAlpha: () -> Float = { 1f },
): Modifier {
    val shift by
        animateFloatAsState(
            smartisanDragRowTranslation(
                index,
                state.drag?.source,
                state.drag?.target,
                rowHeight.toFloat(),
            ),
            tween(SmartisanDragSettleMillis, easing = SmartisanDragEasing),
            label = "list reorder row",
        )
    return graphicsLayer {
        translationY = shift
        alpha = if (state.drag?.source == index) 0f else contentAlpha()
    }
}

@Composable
internal fun SmartisanDragOverlay(
    state: SmartisanListDragState,
    topShadow: Painter,
    bottomShadow: Painter,
    modifier: Modifier = Modifier,
) {
    val floating = state.drag ?: return
    val density = LocalDensity.current
    val shadowTop = topShadow.intrinsicSize.height.roundToInt()
    val shadowBottom = bottomShadow.intrinsicSize.height.roundToInt()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(with(density) { (floating.rowHeight + shadowTop + shadowBottom).toDp() })
            .zIndex(1f)
            .graphicsLayer {
                translationY = floating.floatingY
                alpha = .66f
            }
    ) {
        with(topShadow) { draw(Size(size.width, shadowTop.toFloat())) }
        translate(top = shadowTop.toFloat()) { drawLayer(floating.layer) }
        translate(top = (shadowTop + floating.rowHeight).toFloat()) {
            with(bottomShadow) { draw(Size(size.width, shadowBottom.toFloat())) }
        }
    }
}

internal fun smartisanDragHandleHit(
    pointer: Offset,
    listWidth: Int,
    rowTop: Int,
    rowHeight: Int,
    handleSize: Size,
    rightMargin: Int,
    touchSlop: Float,
): Boolean {
    val handleTop = rowTop + (rowHeight - handleSize.height) / 2f
    return pointer.x >= listWidth - rightMargin - handleSize.width - touchSlop &&
        pointer.x <= listWidth - rightMargin + touchSlop &&
        pointer.y >= handleTop - touchSlop &&
        pointer.y <= handleTop + handleSize.height + touchSlop
}

internal fun smartisanDragFloatingY(
    pointerY: Float,
    touchOffset: Int,
    viewportHeight: Int,
    rowHeight: Int,
    shadowTop: Int,
    shadowBottom: Int,
): Float =
    (pointerY.toInt() - touchOffset)
        .coerceIn(
            -shadowTop,
            maxOf(-shadowTop, viewportHeight - rowHeight - shadowTop - shadowBottom),
        )
        .toFloat()

internal fun smartisanDragTargetAt(
    pointerY: Float,
    visible: List<SmartisanDragRowBounds>,
    allowed: IntRange,
    viewportHeight: Int,
): Int? {
    if (allowed.isEmpty() || allowed.first < 0) return null
    val tracks = visible.filter { it.index in allowed }
    tracks
        .firstOrNull { pointerY >= it.top && pointerY < it.top + it.height }
        ?.let {
            return it.index
        }
    val firstTop = tracks.firstOrNull { it.index == allowed.first }?.top ?: 0
    val last = tracks.firstOrNull { it.index == allowed.last }
    val lastBottom = last?.let { it.top + it.height } ?: viewportHeight
    return when {
        pointerY < firstTop -> allowed.first
        pointerY > lastBottom -> allowed.last
        else -> tracks.minByOrNull { abs(pointerY - (it.top + it.height / 2)) }?.index
    }
}

internal fun smartisanDragRowTranslation(
    index: Int,
    source: Int?,
    target: Int?,
    rowHeight: Float,
): Float =
    when {
        source == null || target == null || index == source -> 0f
        target > source && index in (source + 1)..target -> -rowHeight
        target < source && index in target until source -> rowHeight
        else -> 0f
    }

private const val SmartisanDragSettleMillis = 150
private val SmartisanDragEasing = Easing {
    if (it < .5f) 2f * it * it else 1f - 2f * (it - 1f) * (it - 1f)
}
