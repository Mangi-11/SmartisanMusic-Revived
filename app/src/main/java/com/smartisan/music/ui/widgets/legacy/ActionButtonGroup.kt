package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.smartisan.music.R

private const val DEFAULT_BUTTON_COUNT = 4

private val SmartisanAttributeNamespaces = arrayOf(
    "http://schemas.android.com/apk/res/com.smartisan.music",
    "http://schemas.android.com/apk/res/smartisanos",
)

class ActionButtonGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val buttons: Array<ShadowButton>
    private val container: LinearLayout
    private val shadowView: ImageView
    private val leftActionButton: ImageButton
    private val rightActionButton: ImageButton?
    private var externalShadowView: ImageView? = null
    private var shadowDrawableRes: Int = R.drawable.smartisan_secondary_bar_shadow
    private var shadowVisible: Boolean = false

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.smartisan_button_group_layout, this, true)
        container = findViewById(R.id.ll_group_button_container)
        shadowView = findViewById(R.id.smartisan_iv_btn_group_shadow)
        shadowView.visibility = View.GONE
        leftActionButton = createActionButton().also { actionButton ->
            container.addView(actionButton)
        }
        container.setPadding(
            container.paddingLeft,
            container.paddingTop,
            resources.getDimensionPixelSize(R.dimen.button_group_left_right_padding),
            container.paddingBottom,
        )
        val buttonCount = attrs.resolveInt("button_count", DEFAULT_BUTTON_COUNT)
        buttons = Array(buttonCount.coerceAtLeast(1)) { index ->
            ShadowButton(context).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
                maxLines = 1
                setAllCaps(false)
                setTextColor(context.getColorStateList(R.color.filter_button_text_color))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setShadowColors(
                    context.getColorStateList(R.color.filter_button_text_shadow_colors),
                    0.1f,
                    0f,
                    -2f,
                )
                setBackgroundResource(buttonBackground(index, buttonCount))
                layoutParams = LayoutParams(
                    0,
                    resources.getDimensionPixelSize(R.dimen.secondary_bar_height),
                    1f,
                )
            }
        }
        buttons.forEach(container::addView)
        rightActionButton = null
    }

    fun getButton(index: Int): ShadowButton = buttons[index]

    fun getButtonCount(): Int = buttons.size

    fun getLeftActionButton(): ImageButton = leftActionButton

    fun getRightActionButton(): ImageButton? = rightActionButton

    fun setActionButtonGroupBackground(resId: Int) {
        container.setBackgroundResource(resId)
    }

    fun setActionButtonGroupBackground(drawable: Drawable?) {
        container.background = drawable
    }

    fun setActionButtonGroupBackgroundColor(color: Int) {
        container.setBackgroundColor(color)
    }

    fun setActionButtonGroupSidePadding(left: Int, right: Int) {
        container.setPadding(left, container.paddingTop, right, container.paddingBottom)
    }

    fun setActionButtonGroupShadowVisibility(visible: Boolean) {
        shadowVisible = visible
        shadowView.visibility = View.GONE
        updateExternalShadow()
    }

    fun setButtonActivated(index: Int) {
        buttons.forEachIndexed { buttonIndex, button ->
            button.isActivated = buttonIndex == index
        }
    }

    fun setButtonDrawable(index: Int, resId: Int) {
        buttons.getOrNull(index)?.setCompoundDrawablesWithIntrinsicBounds(resId, 0, 0, 0)
    }

    fun setButtonText(index: Int, resId: Int) {
        setButtonText(index, context.getString(resId))
    }

    fun setButtonText(index: Int, text: CharSequence?) {
        buttons.getOrNull(index)?.text = text ?: ""
    }

    fun setShadowDrawable(resId: Int) {
        if (resId == 0) {
            shadowDrawableRes = R.drawable.smartisan_secondary_bar_shadow
            setActionButtonGroupShadowVisibility(false)
        } else {
            shadowDrawableRes = resId
            externalShadowView?.setBackgroundResource(resId)
            if (shadowVisible) {
                updateExternalShadow()
            }
        }
    }

    private fun updateExternalShadow() {
        val parentView = parent as? ViewGroup
        if (parentView == null) {
            if (shadowVisible) {
                post { updateExternalShadow() }
            }
            return
        }

        if (!shadowVisible) {
            externalShadowView?.let(parentView::removeView)
            externalShadowView = null
            return
        }

        val shadow = externalShadowView ?: ImageView(context).also { imageView ->
            imageView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            imageView.setBackgroundResource(shadowDrawableRes)
            parentView.addView(imageView, createExternalShadowLayoutParams(parentView))
            externalShadowView = imageView
        }
        shadow.setBackgroundResource(shadowDrawableRes)
        shadow.layoutParams = createExternalShadowLayoutParams(parentView)
        shadow.bringToFront()
    }

    private fun createExternalShadowLayoutParams(parentView: ViewGroup): ViewGroup.LayoutParams {
        return when (parentView) {
            is RelativeLayout -> RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                if (id != View.NO_ID) {
                    addRule(RelativeLayout.BELOW, id)
                } else {
                    topMargin = bottom
                }
            }
            is FrameLayout -> FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = bottom
            }
            else -> LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun createActionButton(): ImageButton {
        return ImageButton(context).apply {
            background = context.getDrawable(R.drawable.selector_small_btn_standard)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = LayoutParams(
                resources.getDimensionPixelSize(R.dimen.smartisan_button_fixed_width),
                resources.getDimensionPixelSize(R.dimen.samrtisan_button_fixed_height),
            ).apply {
                rightMargin = resources.getDimensionPixelSize(R.dimen.action_button_left_margin)
            }
        }
    }

    private fun buttonBackground(index: Int, count: Int): Int {
        return when {
            count == 1 -> R.drawable.selector_small_btn_filter_left
            index == 0 -> R.drawable.selector_small_btn_filter_left
            index == count - 1 -> R.drawable.selector_small_btn_filter_right
            else -> R.drawable.selector_small_btn_filter_middle
        }
    }

}

private fun AttributeSet?.resolveInt(name: String, defaultValue: Int): Int {
    if (this == null) {
        return defaultValue
    }
    SmartisanAttributeNamespaces.forEach { namespace ->
        val value = getAttributeIntValue(namespace, name, Int.MIN_VALUE)
        if (value != Int.MIN_VALUE) {
            return value
        }
    }
    return defaultValue
}
