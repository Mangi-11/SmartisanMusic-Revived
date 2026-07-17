package com.smartisan.music.ui.shell.loved

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.smartisan.music.R
import com.smartisan.music.ui.loved.LovedSongsSortMode
import com.smartisan.music.ui.widgets.RoundedRectLinearLayout

private const val SortPopupHorizontalOffsetDp = -158

internal fun showLovedSongsSortPopup(
    anchor: View,
    sortMode: LovedSongsSortMode,
    onSortModeChanged: (LovedSongsSortMode) -> Unit,
) {
    val context = anchor.context
    val resources = context.resources
    val shadowWidth = resources.getDimensionPixelSize(R.dimen.popup_bg_left_right_shadow_width)
    val shadowHeight = resources.getDimensionPixelSize(R.dimen.popup_bg_top_bottom_shadow_height)
    val contentWidth = resources.getDimensionPixelSize(R.dimen.popup_list_menu_default_width)
    val content = FrameLayout(context).apply {
        setBackgroundResource(R.drawable.popup_menu_bg_shadow)
        clipToPadding = false
    }
    val panel = RoundedRectLinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.pop_up_menu_bg)
        setRadius(10f * resources.displayMetrics.density)
    }
    content.addView(
        panel,
        FrameLayout.LayoutParams(contentWidth, FrameLayout.LayoutParams.WRAP_CONTENT),
    )
    panel.addView(sortPopupTitle(anchor))
    panel.addView(sortPopupTitleDivider(anchor))

    val popup = PopupWindow(
        content,
        contentWidth + shadowWidth * 2,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true,
    ).apply {
        isOutsideTouchable = true
        isClippingEnabled = false
        animationStyle = R.style.PopupWindowDropDownDownAnim
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
    LovedSongsSortMode.entries.forEachIndexed { index, mode ->
        panel.addView(
            sortPopupRow(
                parent = panel,
                mode = mode,
                selected = mode == sortMode,
                onClick = {
                    onSortModeChanged(mode)
                    popup.dismiss()
                },
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.popup_list_menu_item_height),
            ),
        )
        if (index != LovedSongsSortMode.entries.lastIndex) {
            panel.addView(sortPopupListDivider(anchor))
        }
    }

    popup.showAtOriginalAnchor(anchor)
}

private fun sortPopupTitle(anchor: View): TextView {
    return TextView(anchor.context).apply {
        setText(R.string.sort_title)
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(Color.argb(0x4c, 0, 0, 0))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(resources.getDimensionPixelSize(R.dimen.popup_list_title_left_margin), 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.popup_list_menu_title_height),
        )
    }
}

private fun sortPopupTitleDivider(anchor: View): View {
    return View(anchor.context).apply {
        setBackgroundColor(anchor.context.getColor(R.color.list_divider_color))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.list_divider_height),
        )
    }
}

private fun sortPopupListDivider(anchor: View): View {
    return View(anchor.context).apply {
        setBackgroundResource(R.drawable.revone_smartisan_list_popup_menu_separator)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.getDrawable(R.drawable.revone_smartisan_list_popup_menu_separator, null)
                .intrinsicHeight
                .coerceAtLeast(1),
        )
    }
}

private fun sortPopupRow(
    parent: ViewGroup,
    mode: LovedSongsSortMode,
    selected: Boolean,
    onClick: () -> Unit,
): View {
    val context = parent.context
    return LayoutInflater.from(context)
        .inflate(R.layout.popup_menu_standard_list_item, parent, false)
        .apply {
            isClickable = true
            setBackgroundResource(R.drawable.revone_menu_list_selector)
            setOnClickListener {
                onClick()
            }
            findViewById<ImageView>(R.id.menu_icon)?.setImageResource(
                when (mode) {
                    LovedSongsSortMode.Time -> R.drawable.icon_sort_by_time
                    LovedSongsSortMode.SongName -> R.drawable.icon_sort_by_name
                },
            )
            findViewById<ImageView>(R.id.menu_selected)?.visibility = if (selected) View.VISIBLE else View.GONE
            findViewById<TextView>(R.id.menu_title)?.text = context.getString(
                when (mode) {
                    LovedSongsSortMode.Time -> R.string.sort_by_saved_time
                    LovedSongsSortMode.SongName -> R.string.sort_by_name
                },
            )
        }
}

private fun View.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private fun PopupWindow.showAtOriginalAnchor(anchor: View) {
    val resources = anchor.resources
    val contentWidth = resources.getDimensionPixelSize(R.dimen.popup_list_menu_default_width)
    val shadowWidth = resources.getDimensionPixelSize(R.dimen.popup_bg_left_right_shadow_width)
    val minDistance = resources.getDimensionPixelSize(R.dimen.menu_panel_bg_min_distance)
    val screenWidth = anchor.rootView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    val anchorLocation = IntArray(2)
    anchor.getLocationInWindow(anchorLocation)
    var popupX = anchorLocation[0] + anchor.dp(SortPopupHorizontalOffsetDp)
    val rightOverflow = shadowWidth + popupX + contentWidth + minDistance - screenWidth
    if (rightOverflow > 0) {
        popupX -= rightOverflow
    }
    @Suppress("DEPRECATION")
    showAtLocation(
        anchor,
        Gravity.TOP or Gravity.LEFT,
        popupX,
        anchorLocation[1],
    )
}
