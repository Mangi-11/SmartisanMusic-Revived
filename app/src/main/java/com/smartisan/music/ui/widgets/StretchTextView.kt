package com.smartisan.music.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.TextView
import com.smartisan.music.R
import kotlin.math.min

class StretchTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextView(context, attrs, defStyleAttr) {
    private val playStateDrawable: Drawable? = context.getDrawable(R.drawable.playing_blueplay2_selector)
    private val pauseStateDrawable: Drawable? = context.getDrawable(R.drawable.playing_bluepause_selector)
    private val drawableGap = (10f * resources.displayMetrics.density).toInt()
    private var currentPlayDrawable: Drawable? = null
    private var showingPlayImage = false

    override fun isFocused(): Boolean = true

    fun b(@Suppress("UNUSED_PARAMETER") ignored: Boolean) = Unit

    fun c(isPlaying: Boolean) {
        currentPlayDrawable = if (isPlaying) playStateDrawable else pauseStateDrawable
        setShowingPlayImage(true)
    }

    fun setShowingPlayImage(showing: Boolean) {
        showingPlayImage = showing
        val drawable = currentPlayDrawable
        setPadding(
            paddingLeft,
            paddingTop,
            if (showing && drawable != null) drawable.intrinsicWidth + drawableGap else 0,
            paddingBottom,
        )
        invalidate()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        currentPlayDrawable?.state = drawableState
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = currentPlayDrawable ?: return
        if (!showingPlayImage || text.isNullOrEmpty()) {
            return
        }

        drawable.state = drawableState
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        val textWidth = paint.measureText(text.toString())
        val availableRight = width - paddingRight
        val preferredLeft = (compoundPaddingLeft + textWidth + drawableGap).toInt()
        val left = min(preferredLeft, (availableRight - drawableWidth).coerceAtLeast(0))
        val top = ((height - drawableHeight) / 2).coerceAtLeast(0)
        drawable.setBounds(left, top, left + drawableWidth, top + drawableHeight)
        drawable.draw(canvas)
    }
}
