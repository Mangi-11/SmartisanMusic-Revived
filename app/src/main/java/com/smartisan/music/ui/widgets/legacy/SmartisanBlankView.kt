package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.smartisan.music.R

private val SmartisanBlankAttributeNamespaces = arrayOf(
    "http://schemas.android.com/apk/res-auto",
    "http://schemas.android.com/apk/res/com.smartisan.music",
    "http://schemas.android.com/apk/res/smartisanos",
)

class SmartisanBlankView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER

        val blankAttributes = context.resolveBlankAttributes(attrs)
        val drawableRes = blankAttributes.emptyDrawable
        if (drawableRes != 0) {
            addView(
                ImageView(context).apply {
                    setImageResource(drawableRes)
                },
                LayoutParams(context.dp(120f), context.dp(120f)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = context.dp(40f)
                },
            )
        }

        blankAttributes.primaryHint?.let { primary ->
            addView(
                TextView(context).apply {
                    text = primary
                    gravity = Gravity.CENTER
                    setTextColor(Color.argb(0x26, 0x00, 0x00, 0x00))
                    setTypeface(typeface, Typeface.BOLD)
                    setSingleLine(true)
                    textSize = 20f
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    leftMargin = context.dp(60f)
                    topMargin = context.dp(18f)
                    rightMargin = context.dp(60f)
                },
            )
        }
        blankAttributes.secondaryHint?.let { secondary ->
            addView(
                TextView(context).apply {
                    text = secondary
                    gravity = Gravity.CENTER
                    setTextColor(Color.argb(0x26, 0x00, 0x00, 0x00))
                    textSize = 13.5f
                },
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    leftMargin = context.dp(60f)
                    topMargin = context.dp(5f)
                    rightMargin = context.dp(60f)
                    bottomMargin = context.dp(40f)
                },
            )
        }
    }
}

private data class SmartisanBlankAttributes(
    val emptyDrawable: Int,
    val primaryHint: CharSequence?,
    val secondaryHint: CharSequence?,
)

private fun Context.resolveBlankAttributes(attrs: AttributeSet?): SmartisanBlankAttributes {
    val typedArray = obtainStyledAttributes(attrs, R.styleable.SmartisanBlankView)
    return try {
        SmartisanBlankAttributes(
            emptyDrawable = typedArray.getResourceId(R.styleable.SmartisanBlankView_emptyDrawable, 0)
                .takeIf { it != 0 }
                ?: attrs.resolveResource("emptyDrawable"),
            primaryHint = typedArray.getText(R.styleable.SmartisanBlankView_primaryHint)
                ?: attrs.resolveText(this, "primaryHint"),
            secondaryHint = typedArray.getText(R.styleable.SmartisanBlankView_secondaryHint)
                ?: attrs.resolveText(this, "secondaryHint"),
        )
    } finally {
        typedArray.recycle()
    }
}

private fun AttributeSet?.resolveText(context: Context, name: String): CharSequence? {
    val resId = resolveResource(name)
    return if (resId != 0) {
        context.getText(resId)
    } else {
        resolveValue(name)
    }
}

private fun AttributeSet?.resolveResource(name: String): Int {
    if (this == null) {
        return 0
    }
    SmartisanBlankAttributeNamespaces.forEach { namespace ->
        val value = getAttributeResourceValue(namespace, name, 0)
        if (value != 0) {
            return value
        }
    }
    return 0
}

private fun AttributeSet?.resolveValue(name: String): String? {
    if (this == null) {
        return null
    }
    SmartisanBlankAttributeNamespaces.forEach { namespace ->
        getAttributeValue(namespace, name)?.let { value ->
            return value
        }
    }
    return null
}

private fun Context.dp(value: Float): Int {
    return (value * resources.displayMetrics.density + 0.5f).toInt()
}
