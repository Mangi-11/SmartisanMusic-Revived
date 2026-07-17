package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.smartisan.music.R

class MenuDialogTitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val leftImageView: ImageView
    private val rightImageView: ImageView
    private val titleBarContainer: ViewGroup
    private var leftClickListener: View.OnClickListener? = null
    private var rightClickListener: View.OnClickListener? = null
    private var requestAccessibilityFocusWhenAttached = true

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.menu_dialog_title_bar, this, true)
        titleView = findViewById(R.id.title)
        leftImageView = findViewById(R.id.btn_cancel_left)
        rightImageView = findViewById(R.id.btn_cancel_right)
        titleBarContainer = findViewById(R.id.menu_dialog_title_bar_container)

        leftImageView.setOnClickListener { view ->
            leftClickListener?.onClick(view)
        }
        rightImageView.setOnClickListener { view ->
            rightClickListener?.onClick(view)
        }
        elevation = 0.1f
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        event.className = javaClass.name
        event.packageName = context.packageName
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? ViewGroup)?.clipChildren = false
        if (requestAccessibilityFocusWhenAttached) {
            titleView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        }
    }

    fun forceRequestAccessibilityFocusWhenAttached(enabled: Boolean) {
        requestAccessibilityFocusWhenAttached = enabled
    }

    fun getLeftImageView(): ImageView = leftImageView

    fun getRightImageView(): ImageView = rightImageView

    fun getTitleBarContainer(): ViewGroup = titleBarContainer

    fun getTitleView(): TextView = titleView

    fun setLeftButtonVisibility(visibility: Int) {
        leftImageView.visibility = visibility
    }

    fun setRightButtonVisibility(visibility: Int) {
        rightImageView.visibility = visibility
    }

    fun setLeftImageViewRes(resId: Int) {
        leftImageView.setImageResource(resId)
    }

    fun setRightImageRes(resId: Int) {
        rightImageView.setImageResource(resId)
    }

    fun setOnLeftButtonClickListener(listener: View.OnClickListener?) {
        leftClickListener = listener
    }

    fun setOnRightButtonClickListener(listener: View.OnClickListener?) {
        rightClickListener = listener
    }

    fun setShadowVisible(visible: Boolean) = Unit

    fun showTopShadow(visible: Boolean) = Unit

    fun setTitle(resId: Int) {
        setTitle(context.getText(resId))
    }

    fun setTitle(title: CharSequence?) {
        titleView.text = title ?: ""
    }

    fun setTitleBarBackgroundResource(resId: Int) {
        titleBarContainer.setBackgroundResource(resId)
    }

    fun setTitleSingleLine(singleLine: Boolean) {
        titleView.setSingleLine(singleLine)
    }
}
