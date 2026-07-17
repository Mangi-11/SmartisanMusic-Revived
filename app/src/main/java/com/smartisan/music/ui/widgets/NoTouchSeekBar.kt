package com.smartisan.music.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RatingBar

class NoTouchSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RatingBar(context, attrs, defStyleAttr) {

    override fun onTouchEvent(event: MotionEvent?): Boolean = false
}
