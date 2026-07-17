package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView

class HeadersListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.listViewStyle,
) : ListView(context, attrs, defStyleAttr) {

    fun setAreHeadersSticky(sticky: Boolean) = Unit

    fun getAreHeadersSticky(): Boolean = false
}
