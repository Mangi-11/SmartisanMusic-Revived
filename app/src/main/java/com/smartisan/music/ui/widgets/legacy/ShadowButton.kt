package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import com.smartisan.music.R

class ShadowButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle,
) : Button(context, attrs, defStyleAttr) {

    private val shadowButtonStyle: Int
    private val backgroundShadowResId: Int

    enum class LongButtonStyle {
        GRAY,
        RED,
    }

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.ShadowButton,
            defStyleAttr,
            0,
        )
        shadowButtonStyle = typedArray.getInt(R.styleable.ShadowButton_shadowButtonStyle, 0)
        backgroundShadowResId = typedArray.getResourceId(R.styleable.ShadowButton_backgroundShadow, 0)
        typedArray.recycle()

        setAllCaps(false)
        includeFontPadding = true
        minWidth = 0
        minimumWidth = 0
        if (shadowButtonStyle == SHRINK_LONG_BUTTON) {
            val shrinkMinHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                48f,
                resources.displayMetrics,
            ).toInt()
            minHeight = shrinkMinHeight
            minimumHeight = shrinkMinHeight
        } else {
            minHeight = 0
            minimumHeight = 0
        }
        gravity = Gravity.CENTER
        stateListAnimator = null
        elevation = 0f
        backgroundTintList = null
        foreground = null
        applyShadowBackgroundIfNeeded()
    }

    private fun applyShadowBackgroundIfNeeded() {
        val content = background ?: return
        if (shadowButtonStyle != SHRINK_LONG_BUTTON) {
            return
        }
        val shadowResId = if (backgroundShadowResId != 0) {
            backgroundShadowResId
        } else {
            R.drawable.shadow_button_shrink_shadow_selector
        }
        val shadow = context.getDrawable(shadowResId) ?: return
        val shadowPadding = Rect()
        shadow.getPadding(shadowPadding)
        super.setBackground(
            ShadowDrawable(
                shadow = shadow,
                target = content,
                insetLeftRight = shadowPadding.left,
                insetTopBottom = shadowPadding.top,
            ),
        )
    }

    fun setShadowColors(
        colors: ColorStateList?,
        radius: Float,
        dx: Float,
        dy: Float,
    ) {
        val color = colors?.getColorForState(drawableState, currentTextColor) ?: currentTextColor
        setShadowLayer(radius, dx, dy, color)
    }

    fun setMaxTitleSize(sizePx: Float) {
        if (sizePx > 0f) {
            textSize = sizePx
        }
    }

    fun setShadowEnable(enabled: Boolean) = Unit

    fun setShadowEnabled(enabled: Boolean) = Unit

    fun setShadowShouldProjects(shouldProject: Boolean) = Unit

    private companion object {
        const val SHRINK_LONG_BUTTON = 3
    }
}
