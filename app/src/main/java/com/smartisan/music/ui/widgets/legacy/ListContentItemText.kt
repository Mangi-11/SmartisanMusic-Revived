package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.smartisan.music.R

class ListContentItemText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
    private val leftContainer: FrameLayout
    private val iconView: ImageView
    private val midContainer: LinearLayout
    private val titleView: TextView
    private val summaryView: TextView
    private val rightContainer: LinearLayout
    private val rightExpandView: LinearLayout
    private val subtitleView: TextView
    private val arrowView: ImageView

    init {
        gravity = Gravity.CENTER_VERTICAL
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.list_content_item_layout, this, true)

        leftContainer = findViewById(R.id.left_container)
        midContainer = findViewById(R.id.mid_container)
        rightContainer = findViewById(R.id.right_container)

        inflater.inflate(R.layout.list_content_left_image_view, leftContainer, true)
        inflater.inflate(R.layout.list_content_mid_primary_2line, midContainer, true)
        inflater.inflate(R.layout.list_content_right_subtitle_arrow, rightContainer, true)

        iconView = findViewById(R.id.left_icon)
        titleView = findViewById(R.id.item_title)
        summaryView = findViewById(R.id.item_summary)
        rightExpandView = findViewById(R.id.rightExpandView)
        subtitleView = findViewById(R.id.subtitle)
        arrowView = findViewById(R.id.arrow)

        setLeftContainerVisible(false)
        setSummary(null)
        setSubtitle(null)
        setArrowVisible(true)
    }

    fun getIconView(): ImageView = iconView

    fun getTitleView(): TextView = titleView

    fun getSummaryView(): TextView = summaryView

    fun getSubTitleView(): TextView = subtitleView

    fun getArrowImageView(): ImageView = arrowView

    fun setIcon(resId: Int) {
        if (resId > 0) {
            iconView.setImageResource(resId)
        } else {
            iconView.setImageDrawable(null)
        }
        setLeftContainerVisible(resId > 0)
    }

    fun setTitle(text: CharSequence?) {
        titleView.text = text ?: ""
    }

    fun setSummary(text: CharSequence?) {
        summaryView.text = text ?: ""
        summaryView.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setSubtitle(text: CharSequence?) {
        subtitleView.text = text ?: ""
        subtitleView.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setArrowVisible(visible: Boolean) {
        arrowView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setRightExpandView(view: View?) {
        rightExpandView.removeAllViews()
        if (view == null) {
            rightExpandView.visibility = View.GONE
            return
        }
        rightExpandView.addView(view)
        rightExpandView.visibility = View.VISIBLE
    }

    private fun setLeftContainerVisible(visible: Boolean) {
        leftContainer.visibility = if (visible) View.VISIBLE else View.GONE
        val params = midContainer.layoutParams as LayoutParams
        if (visible) {
            params.removeRule(ALIGN_PARENT_LEFT)
            params.addRule(RIGHT_OF, R.id.left_container)
            setMidContentPaddingLeft(0)
        } else {
            params.removeRule(RIGHT_OF)
            params.addRule(ALIGN_PARENT_LEFT)
            setMidContentPaddingLeft(resources.getDimensionPixelSize(R.dimen.flexible_space))
        }
        midContainer.layoutParams = params
        titleView.ellipsize = TextUtils.TruncateAt.END
    }

    private fun setMidContentPaddingLeft(left: Int) {
        midContainer.setPadding(
            left,
            midContainer.paddingTop,
            midContainer.paddingRight,
            midContainer.paddingBottom,
        )
    }
}
