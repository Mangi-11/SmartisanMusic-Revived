package com.smartisan.music.ui.shell.songs

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.smartisan.music.R
import com.smartisan.music.ui.shell.LegacySwipeDeleteRow

internal class LegacySongSwipeDeleteItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr), LegacySwipeDeleteRow {
    private val shadowWidth = resources.getDimensionPixelOffset(R.dimen.lv_item_shadow_width)
    private val deleteButtonPaddingLeft = resources.getInteger(R.integer.delete_button_padding_left)
    private var contentView: View? = null

    override val legacySlideLayout: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        alpha = 0f
        isClickable = false
    }

    override val legacyDeleteView: ImageButton = ImageButton(context).apply {
        setBackgroundResource(0)
        setImageResource(R.drawable.remove_playlist_selector)
        scaleType = ImageView.ScaleType.CENTER
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        isClickable = false
        isFocusable = false
        contentDescription = context.getString(R.string.delete)
        layoutParams = LinearLayout.LayoutParams(
            resources.getDrawable(R.drawable.compose_quicktext_delete, context.theme).intrinsicWidth +
                (deleteButtonPaddingLeft * 2),
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    override val legacyRemoveItemView: View
        get() = contentView ?: this

    override val legacyLeftMargin: Int
        get() = -shadowWidth

    init {
        layoutParams = AbsListView.LayoutParams(
            AbsListView.LayoutParams.MATCH_PARENT,
            AbsListView.LayoutParams.WRAP_CONTENT,
        )
        setBackgroundResource(R.drawable.account_background)
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        clipChildren = false
        setChildrenDrawingOrderEnabled(true)
        legacySlideLayout.addView(legacyDeleteView)
        addView(legacySlideLayout)
    }

    fun setContentView(view: View) {
        if (contentView === view) {
            return
        }
        contentView?.let(::removeView)
        contentView = view
        addView(view, 0)
        resetLegacySwipeDelete()
    }

    fun contentView(): View? = contentView

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val checkbox = findViewById<View>(R.id.cb_del) ?: return super.onInterceptTouchEvent(event)
        if (checkbox.visibility == View.VISIBLE && checkbox.alpha > 0f) {
            return true
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun resetLegacySwipeDelete() {
        legacySlideLayout.animate().cancel()
        legacyRemoveItemView.animate().cancel()
        legacySlideLayout.alpha = 0f
        legacySlideLayout.isClickable = false
        legacySlideLayout.tag = null
        legacyDeleteView.isClickable = false
        legacyDeleteView.setOnClickListener(null)
        legacyDeleteView.tag = null
        legacyRemoveItemView.x = legacyLeftMargin.toFloat()
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        if (childCount < 2 || contentView == null) {
            return super.getChildDrawingOrder(childCount, drawingPosition)
        }
        val slideIndex = indexOfChild(legacySlideLayout)
        val contentIndex = indexOfChild(contentView)
        if (slideIndex < 0 || contentIndex < 0) {
            return super.getChildDrawingOrder(childCount, drawingPosition)
        }
        return if (drawingPosition == 0) slideIndex else contentIndex
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val content = contentView
        if (content == null) {
            measureChild(legacySlideLayout, widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(width, legacySlideLayout.measuredHeight)
            return
        }

        val contentWidthSpec = MeasureSpec.makeMeasureSpec(width + shadowWidth, MeasureSpec.EXACTLY)
        measureChild(content, contentWidthSpec, heightMeasureSpec)
        val height = content.measuredHeight
        measureChild(
            legacySlideLayout,
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST),
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val content = contentView ?: return
        val height = measuredHeight
        content.layout(0, 0, measuredWidth + shadowWidth, height)
        val slideWidth = legacySlideLayout.measuredWidth
        legacySlideLayout.layout(0, 0, slideWidth, height)
    }
}
