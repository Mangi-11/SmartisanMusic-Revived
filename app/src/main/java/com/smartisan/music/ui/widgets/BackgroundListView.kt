package com.smartisan.music.ui.widgets

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.ListView

class BackgroundListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ListView(context, attrs, defStyleAttr) {
    init {
        cacheColorHint = Color.TRANSPARENT
    }
}
