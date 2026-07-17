package com.smartisan.music.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.LinearLayout

class RoundedRectLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private var radius = 17f
    private var clipPath: Path? = null

    override fun dispatchDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        clipPath?.let(canvas::clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        clipPath = if (radius > 0f) {
            Path().apply {
                addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW)
            }
        } else {
            null
        }
    }

    fun setRadius(value: Float) {
        radius = value
        requestLayout()
        invalidate()
    }
}
