package com.smartisan.music.ui.shell

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.AbsListView
import android.widget.ListView
import com.smartisan.music.R
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class LegacySlideSelectionStartArea {
    Checkbox,
    FullItem,
}

internal enum class LegacySlideSelectionActivation {
    TouchSlop,
    HorizontalBeforeVertical,
}

internal data class LegacySlideSelectionChange(
    val key: String,
    val selected: Boolean,
)

internal class LegacySlideSelectionModel {
    private var anchorPosition = ListView.INVALID_POSITION
    private var targetSelected = false
    private var expandedFromAnchor = false
    private val activeKeys = linkedSetOf<String>()
    private val baselineSelected = mutableMapOf<String, Boolean>()
    private val currentSelected = mutableMapOf<String, Boolean>()

    val active: Boolean
        get() = anchorPosition != ListView.INVALID_POSITION

    fun begin(
        position: Int,
        key: String,
        selected: Boolean,
    ) {
        reset()
        anchorPosition = position
        targetSelected = !selected
        baselineSelected[key] = selected
        currentSelected[key] = selected
    }

    fun changesThrough(
        position: Int,
        keyAtPosition: (Int) -> String?,
        isSelected: (String) -> Boolean,
    ): List<LegacySlideSelectionChange> {
        if (!active || position == ListView.INVALID_POSITION) {
            return emptyList()
        }
        if (position != anchorPosition) {
            expandedFromAnchor = true
        }
        val range = if (position == anchorPosition && expandedFromAnchor) {
            emptyList()
        } else if (position >= anchorPosition) {
            anchorPosition..position
        } else {
            anchorPosition downTo position
        }
        val nextKeys = linkedSetOf<String>()
        val changes = mutableListOf<LegacySlideSelectionChange>()
        range.forEach { nextPosition ->
            val key = keyAtPosition(nextPosition) ?: return@forEach
            nextKeys += key
            baselineSelected.getOrPut(key) { isSelected(key) }
        }
        activeKeys.filter { key -> key !in nextKeys }.asReversed().forEach { key ->
            val baseline = baselineSelected.getValue(key)
            if (currentSelectionOf(key) != baseline) {
                changes += LegacySlideSelectionChange(key, baseline)
            }
            currentSelected[key] = baseline
        }
        nextKeys.filter { key -> key !in activeKeys }.forEach { key ->
            if (currentSelectionOf(key) != targetSelected) {
                changes += LegacySlideSelectionChange(key, targetSelected)
            }
            currentSelected[key] = targetSelected
        }
        activeKeys.clear()
        activeKeys += nextKeys
        return changes
    }

    fun reset() {
        anchorPosition = ListView.INVALID_POSITION
        targetSelected = false
        expandedFromAnchor = false
        activeKeys.clear()
        baselineSelected.clear()
        currentSelected.clear()
    }

    private fun currentSelectionOf(key: String): Boolean {
        return currentSelected[key] ?: baselineSelected.getValue(key)
    }
}

internal class LegacySlideSelectionController(
    private val listView: AbsListView,
    startArea: LegacySlideSelectionStartArea,
    activation: LegacySlideSelectionActivation,
) {
    private val touchSlop = ViewConfiguration.get(listView.context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val model = LegacySlideSelectionModel()
    private val hitRect = Rect()
    private val checkboxLocation = IntArray(2)
    private val listLocation = IntArray(2)
    private var enabled = false
    private var selectedKeys: Set<String> = emptySet()
    private var keyAtPosition: (Int) -> String? = { null }
    private var onSelectionChange: (String, Boolean) -> Unit = { _, _ -> }
    private var startArea = startArea
    private var activation = activation
    private var downPosition = ListView.INVALID_POSITION
    private var downX = 0f
    private var downY = 0f
    private var activeGesture = false
    private var ignoreGesture = false
    private var lastMotionX = 0f
    private var lastMotionY = 0f
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (!activeGesture || !enabled) {
                return
            }
            val distance = autoScrollDistance(lastMotionY)
            if (distance == 0) {
                return
            }
            listView.scrollListBy(distance)
            applySelectionAt(lastMotionX, lastMotionY)
            handler.postDelayed(this, LegacySlideSelectionAutoScrollFrameMillis.toLong())
        }
    }

    fun update(
        enabled: Boolean,
        selectedKeys: Set<String>,
        keyAtPosition: (Int) -> String?,
        onSelectionChange: (String, Boolean) -> Unit,
        startArea: LegacySlideSelectionStartArea = this.startArea,
        activation: LegacySlideSelectionActivation = this.activation,
    ) {
        this.enabled = enabled
        this.selectedKeys = selectedKeys
        this.keyAtPosition = keyAtPosition
        this.onSelectionChange = onSelectionChange
        this.startArea = startArea
        this.activation = activation
        if (!enabled) {
            resetGesture()
        }
    }

    fun handleTouch(event: MotionEvent): Boolean {
        if (!enabled) {
            return false
        }
        lastMotionX = event.x
        lastMotionY = event.y
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onActionDown(event)
            MotionEvent.ACTION_MOVE -> onActionMove(event)
            MotionEvent.ACTION_UP -> onActionUp(event)
            MotionEvent.ACTION_CANCEL -> onActionCancel()
            else -> activeGesture
        }
    }

    private fun onActionDown(event: MotionEvent): Boolean {
        resetGesture()
        val position = listView.pointToPosition(event.x.toInt(), event.y.toInt())
        val key = keyAtPosition(position) ?: return false
        if (!isStartAreaHit(position, event)) {
            ignoreGesture = true
            return false
        }
        downPosition = position
        downX = event.x
        downY = event.y
        model.begin(
            position = position,
            key = key,
            selected = key in selectedKeys,
        )
        return false
    }

    private fun onActionMove(event: MotionEvent): Boolean {
        if (ignoreGesture || !model.active) {
            return false
        }
        if (!activeGesture && !shouldActivate(event)) {
            return false
        }
        if (!activeGesture) {
            activeGesture = true
            cancelListPress(event)
            clearPressedState()
            listView.parent?.requestDisallowInterceptTouchEvent(true)
        }
        applySelectionAt(event.x, event.y)
        scheduleAutoScrollIfNeeded(event.y)
        return true
    }

    private fun onActionUp(event: MotionEvent): Boolean {
        if (model.active && !activeGesture && downPosition != ListView.INVALID_POSITION) {
            cancelListPress(event)
            clearPressedState()
            model.changesThrough(
                position = downPosition,
                keyAtPosition = keyAtPosition,
                isSelected = { key -> key in selectedKeys },
            ).forEach { change ->
                onSelectionChange(change.key, change.selected)
            }
            resetGesture()
            return true
        }
        return onActionCancel()
    }

    private fun onActionCancel(): Boolean {
        val consumed = activeGesture
        resetGesture()
        return consumed
    }

    private fun shouldActivate(event: MotionEvent): Boolean {
        val dx = abs(event.x - downX)
        val dy = abs(event.y - downY)
        return when (activation) {
            LegacySlideSelectionActivation.TouchSlop -> {
                dx > touchSlop || dy > touchSlop ||
                    listView.pointToPosition(event.x.toInt(), event.y.toInt()) != downPosition
            }
            LegacySlideSelectionActivation.HorizontalBeforeVertical -> {
                val startChild = childAtAdapterPosition(downPosition)
                val horizontalThreshold = ((startChild?.width ?: 0) / 2f).coerceAtLeast(touchSlop.toFloat())
                val verticalIgnoreThreshold = ((startChild?.height ?: 0) / 2f).coerceAtLeast(touchSlop.toFloat())
                if (dy >= verticalIgnoreThreshold && dx < horizontalThreshold) {
                    ignoreGesture = true
                    model.reset()
                    false
                } else {
                    dx >= horizontalThreshold
                }
            }
        }
    }

    private fun applySelectionAt(x: Float, y: Float) {
        val position = selectionPositionAt(x, y)
        if (position == ListView.INVALID_POSITION) {
            return
        }
        model.changesThrough(
            position = position,
            keyAtPosition = keyAtPosition,
            isSelected = { key -> key in selectedKeys },
        ).forEach { change ->
            onSelectionChange(change.key, change.selected)
        }
    }

    private fun selectionPositionAt(x: Float, y: Float): Int {
        if (listView.width <= 0 || listView.height <= 0) {
            return ListView.INVALID_POSITION
        }
        val clampedX = x.coerceIn(0f, (listView.width - 1).toFloat()).toInt()
        val clampedY = y.coerceIn(0f, (listView.height - 1).toFloat()).toInt()
        val directPosition = listView.pointToPosition(clampedX, clampedY)
        if (directPosition != ListView.INVALID_POSITION && keyAtPosition(directPosition) != null) {
            return directPosition
        }
        return when {
            y < 0f -> firstVisibleSelectablePosition()
            y >= listView.height -> lastVisibleSelectablePosition()
            else -> ListView.INVALID_POSITION
        }
    }

    private fun isStartAreaHit(
        position: Int,
        event: MotionEvent,
    ): Boolean {
        if (startArea == LegacySlideSelectionStartArea.FullItem) {
            return true
        }
        val child = childAtAdapterPosition(position) ?: return false
        val checkbox = child.findViewById<View>(R.id.cb_del) ?: return false
        if (checkbox.visibility != View.VISIBLE || checkbox.alpha <= 0f) {
            return false
        }
        checkbox.getLocationOnScreen(checkboxLocation)
        listView.getLocationOnScreen(listLocation)
        hitRect.set(
            checkboxLocation[0] - listLocation[0],
            checkboxLocation[1] - listLocation[1],
            checkboxLocation[0] - listLocation[0] + checkbox.width,
            checkboxLocation[1] - listLocation[1] + checkbox.height,
        )
        hitRect.inset(-touchSlop, -touchSlop)
        return hitRect.contains(event.x.toInt(), event.y.toInt())
    }

    private fun scheduleAutoScrollIfNeeded(y: Float) {
        handler.removeCallbacks(autoScrollRunnable)
        if (autoScrollDistance(y) != 0) {
            handler.postDelayed(autoScrollRunnable, LegacySlideSelectionAutoScrollFrameMillis.toLong())
        }
    }

    private fun autoScrollDistance(y: Float): Int {
        if (!activeGesture || listView.height <= 0) {
            return 0
        }
        val edgeSize = (listView.height * LegacySlideSelectionAutoScrollEdgeRatio)
            .roundToInt()
            .coerceAtLeast(touchSlop * 2)
        val topOverflow = edgeSize - y
        val bottomOverflow = y - (listView.height - edgeSize)
        val rawDistance = when {
            topOverflow > 0f && listView.canScrollVertically(-1) -> -topOverflow
            bottomOverflow > 0f && listView.canScrollVertically(1) -> bottomOverflow
            else -> 0f
        }
        if (rawDistance == 0f || abs(y - downY) < touchSlop * 2) {
            return 0
        }
        val ratio = (abs(rawDistance) / edgeSize).coerceIn(0f, 1f)
        val distance = (LegacySlideSelectionAutoScrollMaxStepPx * ratio)
            .roundToInt()
            .coerceAtLeast(LegacySlideSelectionAutoScrollMinStepPx)
        return if (rawDistance < 0f) -distance else distance
    }

    private fun childAtAdapterPosition(position: Int): View? {
        if (position == ListView.INVALID_POSITION) {
            return null
        }
        return listView.getChildAt(position - listView.firstVisiblePosition)
    }

    private fun cancelListPress(event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(event).apply {
            action = MotionEvent.ACTION_CANCEL
        }
        listView.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun firstVisibleSelectablePosition(): Int {
        val firstVisible = listView.firstVisiblePosition
        for (childIndex in 0 until listView.childCount) {
            val position = firstVisible + childIndex
            if (keyAtPosition(position) != null) {
                return position
            }
        }
        return ListView.INVALID_POSITION
    }

    private fun lastVisibleSelectablePosition(): Int {
        val firstVisible = listView.firstVisiblePosition
        for (childIndex in listView.childCount - 1 downTo 0) {
            val position = firstVisible + childIndex
            if (keyAtPosition(position) != null) {
                return position
            }
        }
        return ListView.INVALID_POSITION
    }

    private fun clearPressedState() {
        listView.isPressed = false
        listView.clearFocus()
        for (index in 0 until listView.childCount) {
            listView.getChildAt(index)?.clearPressedState()
        }
    }

    private fun View.clearPressedState() {
        isPressed = false
        if (this is android.view.ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index)?.clearPressedState()
            }
        }
    }

    private fun resetGesture() {
        handler.removeCallbacks(autoScrollRunnable)
        model.reset()
        downPosition = ListView.INVALID_POSITION
        downX = 0f
        downY = 0f
        activeGesture = false
        ignoreGesture = false
        listView.parent?.requestDisallowInterceptTouchEvent(false)
    }
}

internal fun AbsListView.legacySlideSelectionController(
    startArea: LegacySlideSelectionStartArea = LegacySlideSelectionStartArea.Checkbox,
    activation: LegacySlideSelectionActivation = LegacySlideSelectionActivation.TouchSlop,
): LegacySlideSelectionController {
    val existing = getTag(R.id.legacy_slide_selection_controller) as? LegacySlideSelectionController
    if (existing != null) {
        return existing
    }
    return LegacySlideSelectionController(
        listView = this,
        startArea = startArea,
        activation = activation,
    ).also { controller ->
        setTag(R.id.legacy_slide_selection_controller, controller)
    }
}

internal fun <T> Set<T>.withSelection(
    value: T,
    selected: Boolean,
): Set<T> = if (selected) this + value else this - value

private const val LegacySlideSelectionAutoScrollEdgeRatio = 0.18f
private const val LegacySlideSelectionAutoScrollFrameMillis = 16
private const val LegacySlideSelectionAutoScrollMinStepPx = 6
private const val LegacySlideSelectionAutoScrollMaxStepPx = 36
