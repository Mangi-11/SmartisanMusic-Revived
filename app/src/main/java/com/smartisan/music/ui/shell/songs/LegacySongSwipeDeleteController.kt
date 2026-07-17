package com.smartisan.music.ui.shell.songs

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.ListView
import com.smartisan.music.ui.shell.LegacySwipeDeleteRow
import kotlin.math.abs
import kotlin.math.max

internal fun ListView.legacySongSwipeDeleteController(): LegacySongSwipeDeleteController {
    val existing = getTag(com.smartisan.music.R.id.legacy_song_swipe_delete_controller)
        as? LegacySongSwipeDeleteController
    if (existing != null) {
        return existing
    }
    return LegacySongSwipeDeleteController(this).also { controller ->
        setTag(com.smartisan.music.R.id.legacy_song_swipe_delete_controller, controller)
    }
}

internal class LegacySongSwipeDeleteController(
    private val listView: ListView,
) {
    private val touchSlop = ViewConfiguration.get(listView.context).scaledTouchSlop
    private var enabled = false
    private var keyAtPosition: (Int) -> String? = { null }
    private var onDeleteClick: (String, () -> Unit) -> Unit = { _, _ -> }
    private var onSwipeActiveChange: (Boolean) -> Unit = {}
    private var swipeActive = false

    private var downX = 0f
    private var downY = 0f
    private var activePosition = AdapterView.INVALID_POSITION
    private var activeKey: String? = null
    private var activeRow: LegacySwipeDeleteRow? = null
    private var dragging = false
    private var dragDistance = 0f
    private var openRow: LegacySwipeDeleteRow? = null
    private var openKey: String? = null
    private var deleteTapPending = false

    fun update(
        enabled: Boolean,
        keyAtPosition: (Int) -> String?,
        onDeleteClick: (String, () -> Unit) -> Unit,
        onSwipeActiveChange: (Boolean) -> Unit = {},
    ) {
        this.enabled = enabled
        this.keyAtPosition = keyAtPosition
        this.onDeleteClick = onDeleteClick
        this.onSwipeActiveChange = onSwipeActiveChange
        if (!enabled) {
            closeOpenRow(animated = false)
            clearActive()
            setSwipeActive(false)
        }
    }

    fun isSwipeActive(): Boolean = swipeActive

    fun handleTouch(event: MotionEvent): Boolean {
        if (!enabled) {
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
            else -> dragging || deleteTapPending
        }
    }

    private fun handleDown(event: MotionEvent): Boolean {
        downX = event.x
        downY = event.y
        dragDistance = 0f
        dragging = false
        deleteTapPending = false
        val touched = rowAt(event.x, event.y)
        val opened = openRow
        if (opened != null) {
            if (touched?.row === opened && opened.legacyDeleteView.containsListPoint(event.x, event.y)) {
                deleteTapPending = true
                return true
            }
            closeOpenRow(animated = true)
            clearActive()
            return true
        }

        if (touched == null) {
            clearActive()
            return false
        }
        activePosition = touched.position
        activeKey = keyAtPosition(touched.position)
        activeRow = touched.row
        return false
    }

    private fun handleMove(event: MotionEvent): Boolean {
        val row = activeRow ?: return false
        val dx = event.x - downX
        val dy = event.y - downY
        if (!dragging) {
            if (dx <= touchSlop || abs(dx) <= abs(dy)) {
                if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
                    clearActive()
                }
                return false
            }
            dragging = true
            setSwipeActive(true)
            cancelListPress(event)
            clearPressedState()
            listView.parent?.requestDisallowInterceptTouchEvent(true)
            row.legacyRemoveItemView.animate().cancel()
            row.legacySlideLayout.animate().cancel()
            row.legacySlideLayout.alpha = 1f
        }

        dragDistance = max(0f, dx)
        row.legacyRemoveItemView.x = resistedDragDistance(row, dragDistance)
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        if (deleteTapPending) {
            val key = openKey
            val row = openRow
            val insideDelete = row?.legacyDeleteView?.containsListPoint(event.x, event.y) == true
            if (key != null && insideDelete) {
                requestDeleteForOpenRow()
            }
            deleteTapPending = false
            return true
        }

        val row = activeRow ?: return false
        if (!dragging) {
            clearActive()
            return false
        }
        val openWidth = row.openWidth()
        if (dragDistance <= openWidth / LegacySwipeDeleteOpenThresholdDivisor) {
            animateClosed(row)
            if (openRow === row) {
                openRow = null
                openKey = null
            }
            setSwipeActive(false)
        } else {
            openRow = row
            openKey = activeKey
            setSwipeActive(true)
            row.legacySlideLayout.alpha = 1f
            row.legacySlideLayout.isClickable = true
            row.legacyDeleteView.isClickable = true
            row.legacyDeleteView.setOnClickListener {
                requestDeleteForOpenRow()
            }
            row.legacyDeleteView.tag = activePosition
            val duration = if (dragDistance > openWidth) {
                LegacySwipeDeleteSnapBackMillis
            } else {
                LegacySwipeDeleteSnapOpenMillis
            }
            row.legacyRemoveItemView.animate()
                .x(openWidth)
                .setDuration(duration)
                .start()
        }
        clearActive(keepOpen = true)
        return true
    }

    private fun handleCancel(): Boolean {
        val consumed = dragging || deleteTapPending
        activeRow?.let(::animateClosed)
        clearActive()
        deleteTapPending = false
        setSwipeActive(false)
        return consumed
    }

    fun closeOpenRow(animated: Boolean) {
        val row = openRow ?: return
        if (animated) {
            animateClosed(row)
        } else {
            row.resetLegacySwipeDelete()
        }
        openRow = null
        openKey = null
        setSwipeActive(false)
    }

    private fun animateClosed(row: LegacySwipeDeleteRow) {
        row.legacyRemoveItemView.animate().cancel()
        row.legacySlideLayout.animate().cancel()
        row.legacyRemoveItemView.animate()
            .x(row.legacyLeftMargin.toFloat())
            .setDuration(LegacySwipeDeleteCloseMillis)
            .start()
        row.legacySlideLayout.animate()
            .setStartDelay(LegacySwipeDeleteCloseMillis)
            .setDuration(0L)
            .alpha(0f)
            .start()
        row.legacySlideLayout.isClickable = false
        row.legacySlideLayout.tag = null
        row.legacyDeleteView.isClickable = false
        row.legacyDeleteView.setOnClickListener(null)
        row.legacyDeleteView.tag = null
    }

    private fun requestDeleteForOpenRow() {
        val key = openKey ?: return
        onDeleteClick(key) {
            closeOpenRow(animated = true)
        }
    }

    private fun setSwipeActive(active: Boolean) {
        if (swipeActive == active) {
            return
        }
        swipeActive = active
        onSwipeActiveChange(active)
    }

    private fun rowAt(x: Float, y: Float): TouchedSwipeDeleteRow? {
        val position = listView.pointToPosition(x.toInt(), y.toInt())
        if (position == AdapterView.INVALID_POSITION || keyAtPosition(position) == null) {
            return null
        }
        val child = listView.getChildAt(position - listView.firstVisiblePosition)
            as? LegacySwipeDeleteRow
            ?: return null
        return TouchedSwipeDeleteRow(position, child)
    }

    private fun resistedDragDistance(row: LegacySwipeDeleteRow, distance: Float): Float {
        val openWidth = row.openWidth()
        return when {
            distance < openWidth -> distance
            distance <= openWidth * LegacySwipeDeleteResistanceLimitMultiplier ->
                openWidth + ((distance - openWidth) / LegacySwipeDeleteResistanceDivisor)
            else -> openWidth + (openWidth * LegacySwipeDeleteResistanceLimitMultiplier / LegacySwipeDeleteResistanceDivisor)
        }
    }

    private fun LegacySwipeDeleteRow.openWidth(): Float {
        val width = legacySlideLayout.width.takeIf { it > 0 }
            ?: legacySlideLayout.measuredWidth.takeIf { it > 0 }
            ?: legacyDeleteView.measuredWidth.takeIf { it > 0 }
            ?: (legacyDeleteView as? ImageView)?.drawable?.intrinsicWidth
            ?: 1
        return width.toFloat()
    }

    private fun View.containsListPoint(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        val listLocation = IntArray(2)
        getLocationOnScreen(location)
        listView.getLocationOnScreen(listLocation)
        val rawX = listLocation[0] + x
        val rawY = listLocation[1] + y
        return rawX >= location[0] &&
            rawX <= location[0] + width &&
            rawY >= location[1] &&
            rawY <= location[1] + height
    }

    private fun clearActive(keepOpen: Boolean = false) {
        activePosition = AdapterView.INVALID_POSITION
        activeKey = null
        activeRow = null
        dragging = false
        dragDistance = 0f
        if (!keepOpen) {
            openRow = null
            openKey = null
        }
    }

    private fun cancelListPress(event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(event).apply {
            action = MotionEvent.ACTION_CANCEL
        }
        listView.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun clearPressedState() {
        listView.isPressed = false
        for (index in 0 until listView.childCount) {
            listView.getChildAt(index)?.isPressed = false
        }
    }

    private data class TouchedSwipeDeleteRow(
        val position: Int,
        val row: LegacySwipeDeleteRow,
    )
}

private const val LegacySwipeDeleteCloseMillis = 200L
private const val LegacySwipeDeleteSnapBackMillis = 200L
private const val LegacySwipeDeleteSnapOpenMillis = 100L
private const val LegacySwipeDeleteOpenThresholdDivisor = 4f
private const val LegacySwipeDeleteResistanceDivisor = 7f
private const val LegacySwipeDeleteResistanceLimitMultiplier = 8f
