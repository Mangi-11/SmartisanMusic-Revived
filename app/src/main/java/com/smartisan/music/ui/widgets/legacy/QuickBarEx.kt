package com.smartisan.music.ui.widgets.legacy

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import com.smartisan.music.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class QuickBarEx @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val shadowDrawable: Drawable = requireNotNull(context.getDrawable(R.drawable.letters_bar_background_shadow))
    private val shadowWidth = shadowDrawable.intrinsicWidth.coerceAtLeast(0)
    private val letterBarBackgroundDrawable: Drawable = requireNotNull(context.getDrawable(R.drawable.letters_bar_background))
    private val letterBarWidth = letterBarBackgroundDrawable.intrinsicWidth.coerceAtLeast(0)
    private val gridColumnCount = resources.getInteger(R.integer.smartisan_letterbar_gridview_column_num)
    private val gridColumnWidth = resources.getDimensionPixelSize(R.dimen.quickbar_ex_grid_column_width)
    private val gridSpacing = resources.getDimensionPixelSize(R.dimen.smartisan_quickbar_grid_item_space)
    private val gridWidth = gridColumnCount * gridColumnWidth + (gridColumnCount - 1) * gridSpacing
    private val minDragDistance = resources.getDimensionPixelSize(R.dimen.smartisan_quickbar_min_distance)
    private val visibleWidth = shadowWidth + letterBarWidth
    private val contentWidth = visibleWidth + gridWidth

    val collapsedVisibleWidth: Int
        get() = visibleWidth

    private val shadowView = View(context).apply {
        background = shadowDrawable
    }
    private val lettersBar = LettersStripView(context)
    private val alphabetsAdapter = AlphabetsAdapter(context)
    private val gridView = GridView(context).apply {
        setBackgroundColor(Color.parseColor("#eaeaea"))
        gravity = Gravity.CENTER
        selector = ColorDrawable(Color.TRANSPARENT)
        horizontalSpacing = gridSpacing
        verticalSpacing = gridSpacing
        stretchMode = GridView.NO_STRETCH
        columnWidth = gridColumnWidth
        numColumns = gridColumnCount
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        adapter = alphabetsAdapter
        setOnItemClickListener { _, _, position, _ ->
            val item = alphabetsAdapter.getItem(position)
            if (item == CollapseGridItem) {
                hideLetterGrid()
                return@setOnItemClickListener
            }
            listener?.onLetterChanged(item, LETTER_CHANGED_CLICK)
            hideLetterGrid()
        }
    }

    private var listener: QBListener? = null
    private var state = STATE_HIDDEN
    private var animator: ObjectAnimator? = null
    private var startX = 0f
    private var endX = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var previousMoveRawX = 0f
    private var currentMoveRawX = 0f
    private var dragStartX = 0f
    private var handlingDrag = false
    private var handlingHiddenTouch = false
    private var trackingLetters = false
    private var dragStartedFromHidden = false
    private val hostLocation = IntArray(2)

    init {
        isClickable = true
        clipChildren = false
        addView(shadowView)
        addView(lettersBar)
        addView(gridView)
        setLettersBarBg(false)
    }

    fun setLongPressEnabled(enabled: Boolean) = Unit

    fun setQBListener(nextListener: QBListener?) {
        listener = nextListener
    }

    fun setLetters(nextLetters: List<String>) {
        lettersBar.setLetters(nextLetters.ifEmpty { DefaultLetters })
    }

    fun isLetterGridShown(): Boolean = state == STATE_EXPANDED

    fun hideLetterGrid(animated: Boolean = true) {
        val wasHidden = state == STATE_HIDDEN && animator == null
        if (wasHidden) {
            return
        }
        cancelAnimator()
        handlingDrag = false
        if (animated && width > 0) {
            animateXTo(
                targetX = startX,
                onStart = {
                    state = STATE_DRAGGING
                    setLettersBarBg(true)
                },
                onEnd = {
                    finishHidden()
                },
            )
        } else {
            x = startX
            finishHidden()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredHeight = resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        val requestedWidth = resolveSize(contentWidth, widthMeasureSpec)
        val measuredWidth = max(requestedWidth, contentWidth)
        setMeasuredDimension(measuredWidth, measuredHeight)

        val exactHeight = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        shadowView.measure(
            MeasureSpec.makeMeasureSpec(shadowWidth, MeasureSpec.EXACTLY),
            exactHeight,
        )
        lettersBar.measure(
            MeasureSpec.makeMeasureSpec(letterBarWidth, MeasureSpec.EXACTLY),
            exactHeight,
        )
        gridView.measure(
            MeasureSpec.makeMeasureSpec(gridWidth, MeasureSpec.EXACTLY),
            exactHeight,
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val height = bottom - top
        shadowView.layout(0, 0, shadowWidth, height)
        lettersBar.layout(shadowWidth, 0, shadowWidth + letterBarWidth, height)
        gridView.layout(shadowWidth + letterBarWidth, 0, contentWidth, height)
        updateHostPositions()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        alphabetsAdapter.notifyDataSetChanged()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != VISIBLE) {
            hideLetterGrid(animated = false)
        }
    }

    override fun onDetachedFromWindow() {
        cancelAnimator()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (state == STATE_HIDDEN || handlingHiddenTouch) {
            return dispatchHiddenTouchEvent(event)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateHostPositions()
                downRawX = event.rawX
                downRawY = event.rawY
                resetMoveHistory(event.rawX)
                dragStartX = x
                handlingDrag = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                if (shouldStartHorizontalDrag(deltaX, deltaY)) {
                    beginDrag()
                    recordMove(event.rawX)
                    moveToRawX(event.rawX)
                    handlingDrag = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                handlingDrag = false
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!handlingDrag && event.actionMasked != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateHostPositions()
                downRawX = event.rawX
                downRawY = event.rawY
                resetMoveHistory(event.rawX)
                dragStartX = x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                beginDrag()
                recordMove(event.rawX)
                moveToRawX(event.rawX)
                handlingDrag = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                settleDrag(cancelled = false)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                settleDrag(cancelled = true)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun dispatchHiddenTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateHostPositions()
                val rawXInHost = eventRawXInHost(event)
                if (rawXInHost < startX || rawXInHost > (parent as View).width) {
                    return false
                }
                if (rawXInHost < startX + shadowWidth) {
                    return false
                }
                cancelAnimator()
                downRawX = event.rawX
                downRawY = event.rawY
                resetMoveHistory(event.rawX)
                dragStartX = x
                handlingDrag = false
                handlingHiddenTouch = true
                trackingLetters = true
                setLettersBarBg(true)
                lettersBar.startTouch(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!handlingHiddenTouch) {
                    return false
                }
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                recordMove(event.rawX)
                if (!handlingDrag && shouldStartHorizontalDrag(deltaX, deltaY)) {
                    if (trackingLetters) {
                        lettersBar.cancelTouch()
                        trackingLetters = false
                    }
                    beginDrag()
                    handlingDrag = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (handlingDrag) {
                    moveToRawX(event.rawX)
                } else if (trackingLetters) {
                    lettersBar.moveTouch(event.y)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!handlingHiddenTouch) {
                    return false
                }
                if (handlingDrag) {
                    settleDrag(cancelled = false)
                } else if (trackingLetters) {
                    lettersBar.endTouch()
                    setLettersBarBg(false)
                }
                finishHiddenTouch()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!handlingHiddenTouch) {
                    return false
                }
                if (handlingDrag) {
                    settleDrag(cancelled = true)
                } else if (trackingLetters) {
                    lettersBar.cancelTouch()
                    setLettersBarBg(false)
                }
                finishHiddenTouch()
                return true
            }
        }
        return false
    }

    private fun eventRawXInHost(event: MotionEvent): Float {
        (parent as View).getLocationOnScreen(hostLocation)
        return event.rawX - hostLocation[0]
    }

    private fun finishHiddenTouch() {
        parent?.requestDisallowInterceptTouchEvent(false)
        handlingHiddenTouch = false
        handlingDrag = false
        trackingLetters = false
    }

    private fun shouldStartHorizontalDrag(deltaX: Float, deltaY: Float): Boolean {
        if (abs(deltaX) < minDragDistance || abs(deltaX) < abs(deltaY) * 0.5f) {
            return false
        }
        return when (state) {
            STATE_HIDDEN -> deltaX < 0f
            STATE_DRAGGING -> true
            STATE_EXPANDED -> true
            else -> false
        }
    }

    private fun beginDrag() {
        cancelAnimator()
        if (state != STATE_DRAGGING) {
            dragStartedFromHidden = state == STATE_HIDDEN
        }
        state = STATE_DRAGGING
        setLettersBarBg(true)
    }

    private fun moveToRawX(rawX: Float) {
        val nextX = (dragStartX + rawX - downRawX).coerceIn(endX, startX)
        x = nextX
    }

    private fun resetMoveHistory(rawX: Float) {
        previousMoveRawX = rawX
        currentMoveRawX = rawX
    }

    private fun recordMove(rawX: Float) {
        previousMoveRawX = currentMoveRawX
        currentMoveRawX = rawX
    }

    private fun settleDrag(cancelled: Boolean) {
        if (!handlingDrag && state != STATE_DRAGGING) {
            return
        }
        handlingDrag = false
        if (cancelled) {
            hideLetterGrid()
            return
        }
        val movedLeftOrSettled = currentMoveRawX - previousMoveRawX <= 0f
        val shouldExpand = movedLeftOrSettled && x < (parent as View).width - ORIGINAL_SETTLE_THRESHOLD_PX
        if (shouldExpand) {
            showLetterGrid()
        } else {
            hideLetterGrid()
        }
    }

    private fun showLetterGrid(animated: Boolean = true) {
        val shouldNotifyShow = state == STATE_HIDDEN || dragStartedFromHidden
        cancelAnimator()
        if (shouldNotifyShow) {
            listener?.onLetterGridShow()
        }
        if (animated && width > 0) {
            animateXTo(
                targetX = endX,
                onStart = {
                    state = STATE_DRAGGING
                    setLettersBarBg(true)
                },
                onEnd = {
                    state = STATE_EXPANDED
                    x = endX
                    dragStartedFromHidden = false
                    setLettersBarBg(true)
                },
            )
        } else {
            state = STATE_EXPANDED
            x = endX
            dragStartedFromHidden = false
            setLettersBarBg(true)
        }
    }

    private fun animateXTo(
        targetX: Float,
        onStart: () -> Unit,
        onEnd: () -> Unit,
    ) {
        var cancelled = false
        val nextAnimator = ObjectAnimator.ofFloat(this, "x", x, targetX).apply {
            duration = ANIMATION_DURATION_MS
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        onStart()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (animator === animation) {
                            animator = null
                        }
                        if (!cancelled) {
                            onEnd()
                        }
                    }
                },
            )
        }
        animator = nextAnimator
        nextAnimator.start()
    }

    private fun cancelAnimator() {
        animator?.cancel()
        animator = null
    }

    private fun finishHidden() {
        state = STATE_HIDDEN
        x = startX
        dragStartedFromHidden = false
        setLettersBarBg(false)
        listener?.onLetterGridHidden()
    }

    private fun updateHostPositions() {
        val hostWidth = (parent as View).width
        if (hostWidth <= 0 || width <= 0) {
            return
        }
        startX = (hostWidth - visibleWidth).toFloat()
        endX = (hostWidth - width).toFloat()
        if (animator == null && !handlingDrag) {
            x = when (state) {
                STATE_EXPANDED -> endX
                STATE_DRAGGING -> x.coerceIn(endX, startX)
                else -> startX
            }
        }
    }

    private fun setLettersBarBg(show: Boolean) {
        lettersBar.setShowBackground(show)
        shadowView.visibility = if (show) VISIBLE else INVISIBLE
    }

    private fun requestHostDisallowIntercept(disallow: Boolean) {
        (parent as ViewGroup).requestDisallowInterceptTouchEvent(disallow)
    }

    private inner class LettersStripView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val backgroundDrawable: Drawable = requireNotNull(context.getDrawable(R.drawable.letters_bar_background))
        private val highlightIcon: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.letters_bar_highlight_icon)
        private val letterMargin = resources.getDimensionPixelSize(R.dimen.smartisan_quickbar_letterbar_margin)
        private val letterFontSize = resources.getDimension(R.dimen.letters_bar_letter_font_size)
        private val singleLetterMinHeight = resources.getDimensionPixelSize(R.dimen.letters_bar_single_letter_min_height)
        private val noChosenColor = context.getColor(R.color.no_chosen_letter_font_color)
        private val hasChosenColor = context.getColor(R.color.has_chosen_letter_font_color)
        private var letters: List<String> = DefaultLetters
        private var lastIndex = -1
        private var touchedIndex = -1
        private var showBackground = false

        init {
            isClickable = true
            setWillNotDraw(false)
        }

        fun setLetters(nextLetters: List<String>) {
            letters = nextLetters.ifEmpty { DefaultLetters }
            touchedIndex = -1
            lastIndex = -1
            invalidate()
        }

        fun setShowBackground(show: Boolean) {
            if (showBackground != show) {
                showBackground = show
                invalidate()
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (letters.isEmpty()) {
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    requestHostDisallowIntercept(true)
                    startTouch(event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    moveTouch(event.y)
                    return true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    requestHostDisallowIntercept(false)
                    endTouch()
                    return true
                }
            }
            return true
        }

        fun startTouch(y: Float) {
            val index = indexForY(y)
            touchedIndex = index
            notifyLetter(index, LETTER_CHANGED_CLICK)
            invalidate()
        }

        fun moveTouch(y: Float) {
            val index = indexForY(y)
            touchedIndex = index
            notifyLetter(index, LETTER_CHANGED_SLIDE)
            invalidate()
        }

        fun endTouch() {
            touchedIndex = -1
            lastIndex = -1
            invalidate()
        }

        fun cancelTouch() {
            endTouch()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (letters.isEmpty()) {
                return
            }
            if (showBackground || touchedIndex >= 0) {
                backgroundDrawable.setBounds(0, 0, width, height)
                backgroundDrawable.draw(canvas)
            }

            val visibleIndexes = calculateVisibleLetterIndexes()
            val availableHeight = (height - letterMargin * 2).coerceAtLeast(1)
            val singleHeight = max(availableHeight.toFloat() / visibleIndexes.size, singleLetterMinHeight.toFloat())
            val centerX = width / 2f
            paint.textSize = letterFontSize

            visibleIndexes.forEachIndexed { visibleIndex, letterIndex ->
                val centerY = letterMargin + visibleIndex * singleHeight + singleHeight / 2f
                if (letterIndex == touchedIndex) {
                    drawHighlight(canvas, centerX, centerY)
                }
                paint.color = when {
                    letterIndex == touchedIndex -> Color.WHITE
                    touchedIndex >= 0 || showBackground -> hasChosenColor
                    else -> noChosenColor
                }
                paint.isFakeBoldText = letterIndex == touchedIndex
                val fontMetrics = paint.fontMetricsInt
                val baseline = centerY - (fontMetrics.bottom - fontMetrics.top) / 2f - fontMetrics.top
                canvas.drawText(letters[letterIndex], centerX, baseline, paint)
            }
        }

        private fun drawHighlight(canvas: Canvas, centerX: Float, centerY: Float) {
            val left = centerX - highlightIcon.width / 2f
            val top = centerY - highlightIcon.height / 2f
            canvas.drawBitmap(highlightIcon, left, top, null)
        }

        private fun calculateVisibleLetterIndexes(): List<Int> {
            val letterCount = letters.size
            if (letterCount <= 1) {
                return listOf(0)
            }
            val availableHeight = (height - letterMargin * 2).coerceAtLeast(1)
            val step = if (availableHeight / letterCount < singleLetterMinHeight) {
                max(2, ceil(letterCount / ((availableHeight / singleLetterMinHeight).coerceAtLeast(1).toFloat() - 2f)).toInt()) * 2
            } else {
                1
            }
            val indexes = mutableListOf(0)
            if (step < letterCount / 2) {
                var index = step
                while (index < letterCount - 1) {
                    indexes += index
                    index += step
                }
            }
            if (step < letterCount) {
                indexes += letterCount - 1
            }
            return indexes.distinct()
        }

        private fun indexForY(y: Float): Int {
            return ((y / height.coerceAtLeast(1)) * letters.size)
                .toInt()
                .coerceIn(0, letters.lastIndex)
        }

        private fun notifyLetter(index: Int, action: Int) {
            if (index == lastIndex && action == LETTER_CHANGED_SLIDE) {
                return
            }
            lastIndex = index
            listener?.onLetterChanged(letters[index], action)
        }
    }

    private inner class AlphabetsAdapter(
        private val context: Context,
    ) : BaseAdapter() {
        private val alphabets = ('A'..'Z').map(Char::toString) + CollapseGridItem
        private val rowCount = ceil(alphabets.size / gridColumnCount.toFloat()).toInt()

        override fun getCount(): Int = alphabets.size

        override fun getItem(position: Int): String = alphabets[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int {
            return if (alphabets[position] == CollapseGridItem) 1 else 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = alphabets[position]
            val cellHeight = cellHeightFor(position)
            val view = if (item == CollapseGridItem) {
                (convertView as? ImageView ?: ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER
                    setImageResource(R.drawable.letters_bar_arrow)
                }).apply {
                    minimumHeight = cellHeight
                }
            } else {
                (convertView as? TextView ?: TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextSize(
                        TypedValue.COMPLEX_UNIT_PX,
                        resources.getDimension(R.dimen.smartisan_quickbarex_gridview_font_size),
                    )
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#80000000"))
                    includeFontPadding = true
                }).apply {
                    text = item
                    height = cellHeight
                }
            }
            view.layoutParams = AbsListView.LayoutParams(gridColumnWidth, cellHeight)
            view.setBackgroundResource(
                if ((position / gridColumnCount) % 2 == 0) {
                    R.drawable.quickbar_ex_alphabet_text_light_colorlist
                } else {
                    R.drawable.quickbar_ex_alphabet_text_dark_colorlist
                },
            )
            return view
        }

        private fun cellHeightFor(position: Int): Int {
            val availableHeight = (measuredHeight - (rowCount - 1) * gridSpacing).coerceAtLeast(0)
            val baseHeight = if (rowCount == 0) 0 else availableHeight / rowCount
            val remainder = if (rowCount == 0) 0 else availableHeight - baseHeight * rowCount
            return if (remainder > 0 && position >= count - gridColumnCount) {
                baseHeight + remainder
            } else {
                baseHeight
            }.coerceAtLeast(1)
        }
    }

    interface QBListener {
        fun onLetterChanged(letter: String, action: Int): Boolean

        fun onLetterGridHidden() = Unit

        fun onLetterGridShow() = Unit
    }

    companion object {
        const val LETTER_CHANGED_SLIDE = 0
        const val LETTER_CHANGED_CLICK = 1

        private const val ANIMATION_DURATION_MS = 200L
        private const val ORIGINAL_SETTLE_THRESHOLD_PX = 150f
        private const val STATE_HIDDEN = 0
        private const val STATE_DRAGGING = 1
        private const val STATE_EXPANDED = 2
        private const val CollapseGridItem = "\u0000"

        val DefaultLetters = (
            ('A'..'Z').map(Char::toString) + "#"
        )
    }
}
