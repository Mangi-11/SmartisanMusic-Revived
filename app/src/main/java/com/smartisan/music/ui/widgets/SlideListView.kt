package com.smartisan.music.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView

class SlideListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ListView(context, attrs, defStyleAttr) {

    fun setSlideEnable(enabled: Boolean) {
        isEnabled = enabled || isEnabled
    }
}
