package com.smartisan.music.ui.shell

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HeaderViewListAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.PluralsRes
import com.smartisan.music.R

internal const val LegacyPortListFooterThreshold = 8

internal class LegacyPortListFooterView(context: Context) : LinearLayout(context) {
    private val body = LinearLayout(context).apply {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
    }
    private val content = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = true
        setTextColor(Color.rgb(0xbc, 0xbc, 0xbc))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.footer_text_size))
        setBackgroundColor(Color.WHITE)
        setPadding(
            0,
            resources.getDimensionPixelSize(R.dimen.footer_padding),
            0,
            resources.getDimensionPixelSize(R.dimen.footer_padding),
        )
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        body.addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            body,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
            ),
        )
    }

    fun bind(text: String, visible: Boolean) {
        content.text = text
        val params = body.layoutParams as? LayoutParams
            ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        val nextHeight = if (visible) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        if (params.height != nextHeight) {
            params.height = nextHeight
            body.layoutParams = params
        }
    }
}

internal fun ListView.addLegacyPortListFooter(): LegacyPortListFooterView {
    (getTag(R.id.legacy_port_list_footer) as? LegacyPortListFooterView)?.let { footer ->
        return footer
    }
    isVerticalFadingEdgeEnabled = false
    return LegacyPortListFooterView(context).also { footer ->
        setTag(R.id.legacy_port_list_footer, footer)
        addFooterView(footer, null, false)
    }
}

internal fun ListView.bindLegacyPortListFooter(
    @PluralsRes pluralsRes: Int,
    count: Int,
    visible: Boolean = count >= LegacyPortListFooterThreshold,
) {
    (getTag(R.id.legacy_port_list_footer) as? LegacyPortListFooterView)
        ?.bind(context.resources.getQuantityString(pluralsRes, count, count), visible)
}

internal inline fun <reified T> ListView.legacyWrappedAdapter(): T? {
    return when (val currentAdapter = adapter) {
        is T -> currentAdapter
        is HeaderViewListAdapter -> currentAdapter.wrappedAdapter as? T
        else -> null
    }
}
