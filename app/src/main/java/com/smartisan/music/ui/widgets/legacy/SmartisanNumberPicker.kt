package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import kotlin.math.abs
import kotlin.math.roundToInt

class SmartisanNumberPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    fun interface OnValueChangeListener {
        fun onValueChange(
            picker: SmartisanNumberPicker,
            oldVal: Int,
            newVal: Int,
        )
    }

    interface OnScrollListener {
        fun onScrollStateChange(
            picker: SmartisanNumberPicker,
            scrollState: Int,
        )

        companion object {
            const val SCROLL_STATE_IDLE = 0
            const val SCROLL_STATE_TOUCH_SCROLL = 1
            const val SCROLL_STATE_FLING = 2
        }
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NormalTextColor
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val viewConfiguration = ViewConfiguration.get(context)
    private val flingScroller = Scroller(context, null, true)
    private val adjustScroller = Scroller(context, DecelerateInterpolator(2.5f))
    private var minValue = 0
    private var maxValue = 0
    private var currentValue = 0
    private var displayedValues: Array<String>? = null
    private var wrapSelectorWheel = false
    private var valueChangeListener: OnValueChangeListener? = null
    private var scrollListener: OnScrollListener? = null
    private var downY = 0f
    private var lastY = 0f
    private var selectorOffsetY = 0f
    private var lastScrollerY = 0
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null
    private var scrollState = OnScrollListener.SCROLL_STATE_IDLE
    private var normalTextColor = NormalTextColor
    private var selectedTextColor = SelectedTextColor
    private var normalTextSizePx = context.dp(16f)
    private var selectedTextSizePx = context.dp(18f)
    private val touchSlopPx = viewConfiguration.scaledTouchSlop
    private val minFlingVelocityPx = viewConfiguration.scaledMinimumFlingVelocity
    private val maxFlingVelocityPx = viewConfiguration.scaledMaximumFlingVelocity / 8

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setMinValue(value: Int) {
        minValue = value
        if (maxValue < minValue) {
            maxValue = minValue
        }
        setValue(currentValue.coerceIn(minValue, maxValue))
    }

    fun setMaxValue(value: Int) {
        maxValue = value.coerceAtLeast(minValue)
        setValue(currentValue.coerceIn(minValue, maxValue))
    }

    fun setDisplayedValues(values: Array<String>?) {
        displayedValues = values
        invalidate()
    }

    fun setValue(value: Int) {
        abortAnimations()
        selectorOffsetY = 0f
        setValueInternal(value, notify = false)
    }

    fun getValue(): Int = currentValue

    fun setWrapSelectorWheel(wrap: Boolean) {
        wrapSelectorWheel = wrap
    }

    fun setOnValueChangedListener(listener: OnValueChangeListener?) {
        valueChangeListener = listener
    }

    fun setOnScrollListener(listener: OnScrollListener?) {
        scrollListener = listener
    }

    fun c(
        normalColor: Int,
        selectedColor: Int,
    ) {
        normalTextColor = normalColor
        selectedTextColor = selectedColor
        invalidate()
    }

    fun d(
        normalSizePx: Int,
        selectedSizePx: Int,
    ) {
        normalTextSizePx = normalSizePx.toFloat()
        selectedTextSizePx = selectedSizePx.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val rowHeight = rowHeight()
        for (offset in VisibleOffsets) {
            val itemValue = displayValueFor(currentValue + offset)
            if (!isValueInRange(itemValue)) {
                continue
            }
            val y = centerY + offset * rowHeight + selectorOffsetY
            val selectedFraction = (1f - abs(y - centerY) / rowHeight).coerceIn(0f, 1f)
            textPaint.color = blendColor(normalTextColor, selectedTextColor, selectedFraction)
            textPaint.textSize = normalTextSizePx + (selectedTextSizePx - normalTextSizePx) * selectedFraction
            val previousFakeBoldText = textPaint.isFakeBoldText
            textPaint.isFakeBoldText = selectedFraction > 0.92f
            val label = displayedLabel(itemValue)
            canvas.drawText(label, centerX, textBaseline(y), textPaint)
            textPaint.isFakeBoldText = previousFakeBoldText
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        val tracker = velocityTracker ?: VelocityTracker.obtain().also {
            velocityTracker = it
        }
        tracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                abortAnimations()
                downY = event.y
                lastY = event.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = event.y - lastY
                lastY = event.y
                if (!dragging && abs(event.y - downY) > touchSlopPx) {
                    dragging = true
                    setScrollState(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                }
                if (dragging) {
                    scrollWheelBy(delta)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragging) {
                    tracker.computeCurrentVelocity(1_000, maxFlingVelocityPx.toFloat())
                    val yVelocity = tracker.yVelocity.toInt()
                    if (abs(yVelocity) > minFlingVelocityPx) {
                        startFling(yVelocity)
                        setScrollState(OnScrollListener.SCROLL_STATE_FLING)
                    } else {
                        startSnapAnimation()
                        setScrollState(OnScrollListener.SCROLL_STATE_IDLE)
                    }
                } else {
                    performClick()
                    val deltaRows = ((event.y - height / 2f) / rowHeight()).roundToInt()
                    when {
                        deltaRows > 0 -> startStepAnimation(1)
                        deltaRows < 0 -> startStepAnimation(-1)
                    }
                    setScrollState(OnScrollListener.SCROLL_STATE_IDLE)
                }
                recycleVelocityTracker()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                startSnapAnimation()
                setScrollState(OnScrollListener.SCROLL_STATE_IDLE)
                recycleVelocityTracker()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        val scroller = when {
            !flingScroller.isFinished -> flingScroller
            !adjustScroller.isFinished -> adjustScroller
            else -> return
        }
        if (!scroller.computeScrollOffset()) {
            return
        }
        val currentY = scroller.currY
        val deltaY = currentY - lastScrollerY
        lastScrollerY = currentY
        val consumed = scrollWheelBy(deltaY.toFloat())
        if (!consumed) {
            scroller.forceFinished(true)
        }
        if (scroller.isFinished) {
            if (scroller == flingScroller) {
                startSnapAnimation()
                setScrollState(OnScrollListener.SCROLL_STATE_IDLE)
            } else {
                selectorOffsetY = 0f
                invalidate()
            }
        } else {
            postInvalidateOnAnimation()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun startFling(yVelocity: Int) {
        adjustScroller.forceFinished(true)
        flingScroller.forceFinished(true)
        lastScrollerY = 0
        flingScroller.fling(
            0,
            0,
            0,
            yVelocity,
            0,
            0,
            -FlingDistanceLimit,
            FlingDistanceLimit,
        )
        postInvalidateOnAnimation()
    }

    private fun startStepAnimation(deltaValue: Int) {
        if (!canMoveBy(deltaValue)) {
            startSnapAnimation()
            return
        }
        adjustScroller.forceFinished(true)
        flingScroller.forceFinished(true)
        lastScrollerY = 0
        val targetScroll = (-deltaValue * rowHeight()).roundToInt()
        flingScroller.startScroll(0, 0, 0, targetScroll, StepAnimationDurationMs)
        postInvalidateOnAnimation()
    }

    private fun startSnapAnimation() {
        flingScroller.forceFinished(true)
        adjustScroller.forceFinished(true)
        if (abs(selectorOffsetY) < 0.5f) {
            selectorOffsetY = 0f
            invalidate()
            return
        }
        lastScrollerY = 0
        adjustScroller.startScroll(0, 0, 0, (-selectorOffsetY).roundToInt(), SnapAnimationDurationMs)
        postInvalidateOnAnimation()
    }

    private fun scrollWheelBy(deltaY: Float): Boolean {
        if (deltaY == 0f) {
            return true
        }
        var nextOffset = selectorOffsetY + deltaY
        if (!wrapSelectorWheel) {
            if (currentValue <= minValue && nextOffset > 0f) {
                nextOffset = 0f
            }
            if (currentValue >= maxValue && nextOffset < 0f) {
                nextOffset = 0f
            }
        }
        val moved = nextOffset != selectorOffsetY
        selectorOffsetY = nextOffset
        val threshold = rowHeight() / 2f
        while (selectorOffsetY >= threshold) {
            if (!changeValueBy(-1)) {
                selectorOffsetY = 0f
                break
            }
            selectorOffsetY -= rowHeight()
        }
        while (selectorOffsetY <= -threshold) {
            if (!changeValueBy(1)) {
                selectorOffsetY = 0f
                break
            }
            selectorOffsetY += rowHeight()
        }
        invalidate()
        return moved
    }

    private fun changeValueBy(delta: Int): Boolean {
        return setValueInternal(currentValue + delta, notify = true)
    }

    private fun setValueInternal(
        requestedValue: Int,
        notify: Boolean,
    ): Boolean {
        val nextValue = normalizeValue(requestedValue)
        if (nextValue == currentValue) {
            return false
        }
        val oldValue = currentValue
        currentValue = nextValue
        invalidate()
        if (notify) {
            valueChangeListener?.onValueChange(this, oldValue, nextValue)
        }
        return true
    }

    private fun normalizeValue(value: Int): Int {
        if (minValue == maxValue) {
            return minValue
        }
        if (!wrapSelectorWheel) {
            return value.coerceIn(minValue, maxValue)
        }
        val count = maxValue - minValue + 1
        return ((value - minValue) % count + count) % count + minValue
    }

    private fun displayValueFor(value: Int): Int {
        return if (wrapSelectorWheel) {
            normalizeValue(value)
        } else {
            value
        }
    }

    private fun isValueInRange(value: Int): Boolean {
        return if (wrapSelectorWheel) {
            minValue <= maxValue
        } else {
            value in minValue..maxValue
        }
    }

    private fun displayedLabel(value: Int): String {
        val values = displayedValues
        if (values != null) {
            val index = value - minValue
            if (index in values.indices) {
                return values[index]
            }
        }
        return value.toString()
    }

    private fun canMoveBy(deltaValue: Int): Boolean {
        return wrapSelectorWheel || (currentValue + deltaValue) in minValue..maxValue
    }

    private fun abortAnimations() {
        flingScroller.forceFinished(true)
        adjustScroller.forceFinished(true)
        lastScrollerY = 0
    }

    private fun setScrollState(state: Int) {
        if (scrollState == state) {
            return
        }
        scrollState = state
        scrollListener?.onScrollStateChange(this, state)
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun textBaseline(centerY: Float): Float {
        val metrics = textPaint.fontMetrics
        return centerY - (metrics.ascent + metrics.descent) / 2f
    }

    private fun rowHeight(): Float {
        return if (height > 0) {
            height / VisibleRowCount
        } else {
            context.dp(42f)
        }
    }

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

    private fun blendColor(
        from: Int,
        to: Int,
        fraction: Float,
    ): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val fromA = Color.alpha(from)
        val fromR = Color.red(from)
        val fromG = Color.green(from)
        val fromB = Color.blue(from)
        return Color.argb(
            fromA + ((Color.alpha(to) - fromA) * clamped).roundToInt(),
            fromR + ((Color.red(to) - fromR) * clamped).roundToInt(),
            fromG + ((Color.green(to) - fromG) * clamped).roundToInt(),
            fromB + ((Color.blue(to) - fromB) * clamped).roundToInt(),
        )
    }

    private companion object {
        val VisibleOffsets = -2..2
        const val VisibleRowCount = 5f
        const val StepAnimationDurationMs = 300
        const val SnapAnimationDurationMs = 800
        const val FlingDistanceLimit = 0x3fffffff
        val NormalTextColor = Color.rgb(158, 158, 162)
        val SelectedTextColor = Color.rgb(80, 121, 217)
    }
}
