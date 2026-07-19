package com.smartisan.music.ui.shell.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisan.music.R
import com.smartisan.music.launcher.AppIcon
import com.smartisan.music.ui.shell.dpPx
import com.smartisan.music.ui.shell.titlebar.LegacyPortSmartisanTitleBar
import com.smartisan.music.ui.widgets.legacy.ShadowDrawable

@Composable
internal fun LegacyAppIconSettingsPage(
    active: Boolean,
    selectedIcon: AppIcon,
    onClose: () -> Unit,
    onIconSelected: (AppIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        LegacyPortSmartisanTitleBar(
            modifier = Modifier.fillMaxWidth(),
            showShadow = true,
        ) { titleBar ->
            titleBar.removeAllLeftViews()
            titleBar.removeAllRightViews()
            titleBar.setShadowVisible(false)
            titleBar.setCenterText(R.string.app_icon)
            titleBar.addLeftImageView(R.drawable.standard_icon_back_selector).setOnClickListener {
                onClose()
            }
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = ::LegacyAppIconContentView,
            update = { view ->
                view.visibility = if (active) View.VISIBLE else View.INVISIBLE
                view.bind(
                    selectedIcon = selectedIcon,
                    onIconSelected = onIconSelected,
                )
            },
        )
    }
}

internal fun AppIcon.labelRes(): Int {
    return when (this) {
        AppIcon.Original -> R.string.app_icon_original
        AppIcon.Modern -> R.string.app_icon_modern
    }
}

private fun AppIcon.summaryRes(): Int {
    return when (this) {
        AppIcon.Original -> R.string.app_icon_original_summary
        AppIcon.Modern -> R.string.app_icon_modern_summary
    }
}

private fun AppIcon.previewRes(): Int {
    return when (this) {
        AppIcon.Original -> R.mipmap.ic_launcher
        AppIcon.Modern -> R.mipmap.ic_launcher_modern
    }
}

private class LegacyAppIconContentView(context: Context) : ScrollView(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private val iconRows = AppIcon.entries.map { icon ->
        icon to LegacyAppIconRow(context, icon)
    }

    init {
        setBackgroundResource(R.drawable.account_background)
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_ALWAYS
        clipChildren = false
        clipToPadding = false
        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(gapView(context))
        content.addView(sectionTitleView(context))
        content.addView(iconGroup(context))
        content.addView(refreshHintView(context))
        content.addView(gapView(context))
    }

    fun bind(
        selectedIcon: AppIcon,
        onIconSelected: (AppIcon) -> Unit,
    ) {
        iconRows.forEach { (icon, row) ->
            row.bind(
                selected = icon == selectedIcon,
                onClick = {
                    if (icon != selectedIcon) {
                        onIconSelected(icon)
                    }
                },
            )
        }
    }

    private fun iconGroup(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            iconRows.forEachIndexed { index, (_, row) ->
                row.applyBackground(
                    when (index) {
                        0 -> AppIconRowShape.Top
                        iconRows.lastIndex -> AppIconRowShape.Bottom
                        else -> AppIconRowShape.Middle
                    },
                )
                addView(row, rowLayoutParams(context))
            }
        }
    }

    private fun rowLayoutParams(context: Context): LinearLayout.LayoutParams {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.resources.getDimensionPixelSize(R.dimen.list_item_min_height),
        ).apply {
            leftMargin = margin
            rightMargin = margin
        }
    }

    private fun gapView(context: Context): View {
        return View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.list_item_vertical_gap),
            )
        }
    }

    private fun sectionTitleView(context: Context): TextView {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return TextView(context).apply {
            setText(R.string.app_icon_choose)
            setTextColor(context.getColor(R.color.setting_item_summary_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(context.dpPx(18), 0, context.dpPx(18), context.dpPx(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = margin
                rightMargin = margin
            }
        }
    }

    private fun refreshHintView(context: Context): TextView {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return TextView(context).apply {
            setText(R.string.app_icon_refresh_hint)
            setTextColor(context.getColor(R.color.setting_item_summary_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
            setPadding(context.dpPx(18), context.dpPx(10), context.dpPx(18), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = margin
                rightMargin = margin
            }
        }
    }
}

@SuppressLint("ViewConstructor") // Programmatic-only row requires an immutable icon model.
private class LegacyAppIconRow(
    context: Context,
    private val icon: AppIcon,
) : RelativeLayout(context) {
    private val previewView = ImageView(context).apply {
        id = View.generateViewId()
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(icon.previewRes())
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val titleView = TextView(context).apply {
        setSingleLine(true)
        setText(icon.labelRes())
        setTextColor(context.getColorStateList(R.color.setting_item_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.primary_text_size))
        setDuplicateParentStateEnabled(true)
    }
    private val summaryView = TextView(context).apply {
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        setText(icon.summaryRes())
        setTextColor(context.getColorStateList(R.color.setting_item_summary_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
        setDuplicateParentStateEnabled(true)
    }
    private val selectedView = ImageView(context).apply {
        id = View.generateViewId()
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(R.drawable.selector_radio_choice)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        isClickable = true
        isFocusable = true
        addView(
            previewView,
            LayoutParams(context.dpPx(40), context.dpPx(40)).apply {
                addRule(ALIGN_PARENT_LEFT)
                addRule(CENTER_VERTICAL)
                leftMargin = context.dpPx(12)
            },
        )
        addView(
            selectedView,
            LayoutParams(context.dpPx(28), context.dpPx(28)).apply {
                addRule(ALIGN_PARENT_RIGHT)
                addRule(CENTER_VERTICAL)
                rightMargin = context.dpPx(14)
            },
        )
        val textColumn = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                titleView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                summaryView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        addView(
            textColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                addRule(RIGHT_OF, previewView.id)
                addRule(LEFT_OF, selectedView.id)
                addRule(CENTER_VERTICAL)
                leftMargin = context.dpPx(12)
                rightMargin = context.dpPx(8)
            },
        )
    }

    fun bind(
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        isSelected = selected
        contentDescription = context.getString(
            R.string.app_icon_option_description,
            context.getString(icon.labelRes()),
            context.getString(icon.summaryRes()),
        )
        setOnClickListener { onClick() }
    }

    fun applyBackground(shape: AppIconRowShape) {
        val target = requireNotNull(context.getDrawable(shape.backgroundRes)).mutate()
        val shadow = requireNotNull(context.getDrawable(shape.shadowRes)).mutate()
        val shadowPadding = Rect()
        shadow.getPadding(shadowPadding)
        background = ShadowDrawable(
            shadow = shadow,
            target = target,
            insetLeftRight = shadowPadding.left,
            insetTopBottom = shadowPadding.top,
        )
    }
}

private enum class AppIconRowShape(
    val backgroundRes: Int,
    val shadowRes: Int,
) {
    Top(R.drawable.group_list_item_bg_top, R.drawable.list_content_item_top_shadow),
    Middle(R.drawable.group_list_item_bg_mid, R.drawable.list_content_item_middle_shadow),
    Bottom(R.drawable.group_list_item_bg_bottom, R.drawable.list_content_item_bottom_shadow),
}
