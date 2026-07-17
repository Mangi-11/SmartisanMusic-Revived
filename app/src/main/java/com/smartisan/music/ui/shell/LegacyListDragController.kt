package com.smartisan.music.ui.shell

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import com.smartisan.music.R
import kotlin.math.abs

internal interface LegacyListDragAdapter<T> {
    fun reorderableItemAt(position: Int): T?
    fun firstReorderableAdapterPosition(): Int
    fun lastReorderableAdapterPosition(): Int
    fun movePreviewRow(fromPosition: Int, toPosition: Int)
}

internal class LegacyListDragController<T>(
    context: Context,
    private val hostView: ViewGroup,
    private val listView: ListView,
    private val adapter: LegacyListDragAdapter<T>,
    private val onMoveCommitted: (source: T, target: T, sourcePosition: Int, targetPosition: Int) -> Unit,
) {
    private val dragInterpolator = TimeInterpolator { fraction ->
        if (fraction < LegacyDragInterpolatorPivot) {
            2f * fraction * fraction
        } else {
            val inverse = fraction - 1f
            1f - 2f * inverse * inverse
        }
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var dragSource: T? = null
    private var dragSourceAdapterPosition = ListView.INVALID_POSITION
    private var dragTargetAdapterPosition = ListView.INVALID_POSITION
    private var dragStartRawY = 0f
    private var dragTouchOffsetY = 0
    private var dragFloatPaddingTop = 0
    private var dragRowHeight = 0
    private var dragFloatView: ImageView? = null
    private var dragging = false
    private var finishingDrag = false

    fun handleListTouch(event: MotionEvent): Boolean {
        if (finishingDrag) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val touchedItem = findTouchedDragHandleItem(event) ?: return false
                dragSource = touchedItem.item
                dragSourceAdapterPosition = touchedItem.adapterPosition
                dragTargetAdapterPosition = touchedItem.adapterPosition
                dragStartRawY = event.rawY
                dragging = false
                beginDragVisual(touchedItem.child, event)
                listView.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragSource == null) {
                    return false
                }
                updateDragFloatPosition(event, animate = false)
                if (abs(event.rawY - dragStartRawY) > touchSlop) {
                    dragging = true
                    updateDragTarget(event)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragSource == null) {
                    return false
                }
                updateDragFloatPosition(event, animate = false)
                if (dragging) {
                    updateDragTarget(event)
                }
                finishDrag(commitMove = dragging)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragSource == null) {
                    return false
                }
                finishDrag(commitMove = false)
                return true
            }
        }
        return false
    }

    private fun findTouchedDragHandleItem(event: MotionEvent): LegacyDragTouch<T>? {
        val position = listView.pointToPosition(event.x.toInt(), event.y.toInt())
        if (position == ListView.INVALID_POSITION) {
            return null
        }
        val item = adapter.reorderableItemAt(position) ?: return null
        val child = listView.getChildAt(position - listView.firstVisiblePosition) ?: return null
        val dragHandle = child.findViewById<View>(R.id.iv_right) ?: return null
        val hitRect = Rect()
        dragHandle.getHitRect(hitRect)
        hitRect.offset(child.left, child.top)
        hitRect.inset(-touchSlop, -touchSlop)
        return if (hitRect.contains(event.x.toInt(), event.y.toInt())) {
            LegacyDragTouch(item, position, child)
        } else {
            null
        }
    }

    private fun findDropTargetAdapterPosition(event: MotionEvent): Int {
        val y = event.y.toInt()
        val adapterPosition = listView.pointToPosition(listView.width / 2, y)
        if (adapterPosition != ListView.INVALID_POSITION) {
            adapter.reorderableItemAt(adapterPosition)?.let { return adapterPosition }
        }
        return when {
            y < firstReorderableChildTop() -> adapter.firstReorderableAdapterPosition()
            y > lastReorderableChildBottom() -> adapter.lastReorderableAdapterPosition()
            else -> nearestVisibleReorderableAdapterPosition(y)
        }
    }

    private fun beginDragVisual(child: View, event: MotionEvent) {
        clearDragFloatView()
        child.isPressed = false
        val dragBitmap = child.createDragBitmap()
        val listOffset = listViewOffsetInHost()
        dragFloatPaddingTop = dragBitmap.paddingTop
        dragRowHeight = child.height
        dragTouchOffsetY = event.y.toInt() - child.top + dragFloatPaddingTop
        dragFloatView = ImageView(hostView.context).apply {
            setImageBitmap(dragBitmap.bitmap)
            alpha = LegacyDragFloatAlpha
            layoutParams = ViewGroup.LayoutParams(child.width, dragBitmap.bitmap.height)
            x = (listOffset.first + child.left).toFloat()
            y = (listOffset.second + child.top - dragFloatPaddingTop).toFloat()
        }
        hostView.addView(dragFloatView)
        child.visibility = View.INVISIBLE
        updateDragFloatPosition(event, animate = false)
    }

    private fun updateDragFloatPosition(event: MotionEvent, animate: Boolean) {
        val floatView = dragFloatView ?: return
        val listOffset = listViewOffsetInHost()
        val minY = listOffset.second - dragFloatPaddingTop
        val maxY = listOffset.second + listView.height - floatView.height
        val targetY = (listOffset.second + event.y.toInt() - dragTouchOffsetY).coerceIn(minY, maxY)
        if (animate) {
            floatView.animate()
                .y(targetY.toFloat())
                .setDuration(LegacyDragSettleDurationMillis)
                .setInterpolator(dragInterpolator)
                .start()
        } else {
            floatView.y = targetY.toFloat()
        }
    }

    private fun updateDragTarget(event: MotionEvent) {
        val targetPosition = findDropTargetAdapterPosition(event)
        if (
            targetPosition == ListView.INVALID_POSITION ||
            targetPosition == dragTargetAdapterPosition ||
            adapter.reorderableItemAt(targetPosition) == null
        ) {
            return
        }
        dragTargetAdapterPosition = targetPosition
        animateVisibleRowsForDrag()
    }

    private fun animateVisibleRowsForDrag() {
        val sourcePosition = dragSourceAdapterPosition
        val targetPosition = dragTargetAdapterPosition
        if (sourcePosition == ListView.INVALID_POSITION || targetPosition == ListView.INVALID_POSITION) {
            return
        }
        val rowHeight = dragRowHeight.takeIf { it > 0 }
            ?: listView.getChildAt(sourcePosition - listView.firstVisiblePosition)?.height
            ?: hostView.resources.getDimensionPixelSize(R.dimen.listview_item_height)
        val firstVisible = listView.firstVisiblePosition
        repeat(listView.childCount) { childIndex ->
            val child = listView.getChildAt(childIndex)
            val adapterPosition = firstVisible + childIndex
            val targetTranslation = when {
                adapterPosition == sourcePosition -> 0f
                targetPosition > sourcePosition && adapterPosition in (sourcePosition + 1)..targetPosition -> -rowHeight.toFloat()
                targetPosition < sourcePosition && adapterPosition in targetPosition until sourcePosition -> rowHeight.toFloat()
                else -> 0f
            }
            child.visibility = if (adapterPosition == sourcePosition) View.INVISIBLE else View.VISIBLE
            child.animate()
                .translationY(targetTranslation)
                .setDuration(LegacyDragShuffleDurationMillis)
                .setInterpolator(dragInterpolator)
                .start()
        }
    }

    private fun finishDrag(commitMove: Boolean) {
        val source = dragSource
        val targetPosition = dragTargetAdapterPosition
        val target = adapter.reorderableItemAt(targetPosition)
        val shouldMove = commitMove &&
            source != null &&
            target != null &&
            dragSourceAdapterPosition != ListView.INVALID_POSITION &&
            targetPosition != ListView.INVALID_POSITION &&
            dragSourceAdapterPosition != targetPosition
        val settleY = adapterPositionTopInHost(
            if (shouldMove) targetPosition else dragSourceAdapterPosition,
        ) ?: dragFloatView?.y?.toInt()
        finishingDrag = true
        val floatView = dragFloatView
        if (floatView == null) {
            completeDrag(shouldMove, source, target)
            return
        }
        val animator = floatView.animate()
            .setDuration(LegacyDragSettleDurationMillis)
            .setInterpolator(dragInterpolator)
            .withEndAction {
                completeDrag(shouldMove, source, target)
            }
        if (settleY != null) {
            animator.y(settleY.toFloat())
        }
        animator.start()
    }

    private fun completeDrag(
        shouldMove: Boolean,
        source: T?,
        target: T?,
    ) {
        clearDragFloatView()
        resetVisibleRows()
        val sourcePosition = dragSourceAdapterPosition
        val targetPosition = dragTargetAdapterPosition
        resetDrag()
        if (
            shouldMove &&
            source != null &&
            target != null &&
            sourcePosition != ListView.INVALID_POSITION &&
            targetPosition != ListView.INVALID_POSITION
        ) {
            adapter.movePreviewRow(sourcePosition, targetPosition)
            onMoveCommitted(source, target, sourcePosition, targetPosition)
        }
    }

    private fun resetVisibleRows() {
        repeat(listView.childCount) { index ->
            listView.getChildAt(index).apply {
                animate().cancel()
                translationY = 0f
                visibility = View.VISIBLE
            }
        }
    }

    private fun clearDragFloatView() {
        dragFloatView?.let { floatView ->
            floatView.animate().cancel()
            floatView.setImageDrawable(null)
            hostView.removeView(floatView)
        }
        dragFloatView = null
    }

    private fun adapterPositionTopInHost(adapterPosition: Int): Int? {
        if (adapterPosition == ListView.INVALID_POSITION) {
            return null
        }
        val child = listView.getChildAt(adapterPosition - listView.firstVisiblePosition) ?: return null
        return listViewOffsetInHost().second + child.top - dragFloatPaddingTop
    }

    private fun firstReorderableChildTop(): Int {
        val firstPosition = adapter.firstReorderableAdapterPosition()
        val child = listView.getChildAt(firstPosition - listView.firstVisiblePosition)
        return child?.top ?: 0
    }

    private fun lastReorderableChildBottom(): Int {
        val lastPosition = adapter.lastReorderableAdapterPosition()
        val child = listView.getChildAt(lastPosition - listView.firstVisiblePosition)
        return child?.bottom ?: listView.height
    }

    private fun nearestVisibleReorderableAdapterPosition(y: Int): Int {
        var nearestPosition = ListView.INVALID_POSITION
        var nearestDistance = Int.MAX_VALUE
        val firstVisible = listView.firstVisiblePosition
        repeat(listView.childCount) { childIndex ->
            val adapterPosition = firstVisible + childIndex
            if (adapter.reorderableItemAt(adapterPosition) != null) {
                val child = listView.getChildAt(childIndex)
                val distance = abs(y - (child.top + child.height / 2))
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearestPosition = adapterPosition
                }
            }
        }
        return nearestPosition
    }

    private fun listViewOffsetInHost(): Pair<Int, Int> {
        val hostLocation = IntArray(2)
        val listLocation = IntArray(2)
        hostView.getLocationOnScreen(hostLocation)
        listView.getLocationOnScreen(listLocation)
        return (listLocation[0] - hostLocation[0]) to (listLocation[1] - hostLocation[1])
    }

    private fun resetDrag() {
        dragSource = null
        dragSourceAdapterPosition = ListView.INVALID_POSITION
        dragTargetAdapterPosition = ListView.INVALID_POSITION
        dragging = false
        dragTouchOffsetY = 0
        dragFloatPaddingTop = 0
        dragRowHeight = 0
        finishingDrag = false
        listView.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun View.createDragBitmap(): LegacyDragBitmap {
        val shadowTop = resources.getDrawable(R.drawable.shadow_top, null)
        val shadowBottom = resources.getDrawable(R.drawable.shadow_bottom, null)
        val topHeight = shadowTop.intrinsicHeight.coerceAtLeast(0)
        val bottomHeight = shadowBottom.intrinsicHeight.coerceAtLeast(0)
        val bitmap = Bitmap.createBitmap(width, topHeight + height + bottomHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        shadowTop.setBounds(0, 0, width, topHeight)
        shadowTop.draw(canvas)
        canvas.save()
        canvas.translate(0f, topHeight.toFloat())
        draw(canvas)
        canvas.restore()
        shadowBottom.setBounds(0, topHeight + height, width, topHeight + height + bottomHeight)
        shadowBottom.draw(canvas)
        return LegacyDragBitmap(
            bitmap = bitmap,
            paddingTop = topHeight,
        )
    }
}

private data class LegacyDragTouch<T>(
    val item: T,
    val adapterPosition: Int,
    val child: View,
)

private data class LegacyDragBitmap(
    val bitmap: Bitmap,
    val paddingTop: Int,
)

private const val LegacyDragShuffleDurationMillis = 150L
private const val LegacyDragSettleDurationMillis = 150L
private const val LegacyDragFloatAlpha = 0.66f
private const val LegacyDragInterpolatorPivot = 0.5f
