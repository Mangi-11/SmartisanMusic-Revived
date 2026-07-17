package com.smartisan.music.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.SeekBar
import kotlin.math.abs

class ThumbOnlySeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SeekBar(context, attrs, defStyleAttr) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchOnThumb(event)) {
                    return false
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun isTouchOnThumb(event: MotionEvent): Boolean {
        val maxValue = max
        val availableWidth = width - paddingLeft - paddingRight
        if (maxValue <= 0 || availableWidth <= 0) {
            return false
        }

        val thumbWidth = thumb?.intrinsicWidth?.takeIf { it > 0 } ?: height
        val thumbCenterX = paddingLeft + availableWidth * (progress.toFloat() / maxValue.toFloat())
        val touchSlop = resources.displayMetrics.density * ThumbTouchPaddingDp
        return abs(event.x - thumbCenterX) <= thumbWidth / 2f + touchSlop
    }

    private companion object {
        const val ThumbTouchPaddingDp = 4f
    }
}
