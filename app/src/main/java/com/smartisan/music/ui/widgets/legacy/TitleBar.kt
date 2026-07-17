package com.smartisan.music.ui.widgets.legacy

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.smartisan.music.R
import kotlin.math.max

class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
    private val leftViews = mutableListOf<View>()
    private val rightViews = mutableListOf<View>()
    private val titleView: TextView
    private val shadowView: ImageView
    private var centerView: View? = null
    private var titleBarHeight = resources.getDimensionPixelSize(R.dimen.title_bar_height)
    private val imageViewSize = resources.getDimensionPixelSize(R.dimen.standard_icon_size)
    private val marginEdge = resources.getDimensionPixelSize(R.dimen.bar_margin_edge)
    private val marginView = resources.getDimensionPixelSize(R.dimen.title_bar_margin_view)
    private val titleBarCenterLimit =
        resources.getDimensionPixelSize(R.dimen.title_bar_center_limite)

    init {
        setBackgroundColor(context.getColor(android.R.color.white))
        clipChildren = false
        clipToPadding = false
        elevation = 0.11f

        titleView = TextView(context).apply {
            id = View.generateViewId()
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            maxLines = 1
            setSingleLine(true)
            setTextColor(context.getColor(R.color.title_color))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.title_text_size))
            paint.isFakeBoldText = true
        }
        addView(
            titleView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(CENTER_IN_PARENT)
            },
        )

        shadowView = ImageView(context).apply {
            id = View.generateViewId()
            setBackgroundResource(R.drawable.title_bar_shadow)
            translationY = titleBarHeight.toFloat()
        }
        addView(
            shadowView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.title_bar_shadow_height),
            ).apply {
                addRule(ALIGN_PARENT_TOP)
            },
        )
    }

    fun setCenterText(text: String?) {
        clearCenterView()
        titleView.text = text.orEmpty()
    }

    fun setCenterText(resId: Int) {
        setCenterText(context.getString(resId))
    }

    fun setCenterTextColor(color: Int) {
        titleView.setTextColor(color)
    }

    fun getTitleView(): TextView = titleView

    fun setCenterView(view: View) {
        if (centerView === view) {
            return
        }
        clearCenterView()
        (view.parent as? ViewGroup)?.removeView(view)
        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
        centerView = view
        titleView.visibility = View.GONE
        addView(
            view,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(CENTER_IN_PARENT)
            },
        )
    }

    fun getShadowView(): View = shadowView

    fun setShadowVisible(visible: Boolean) {
        shadowView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setTitleBarHeight(height: Int) {
        titleBarHeight = height
        shadowView.translationY = titleBarHeight.toFloat()
        requestLayout()
    }

    fun addLeftImageView(resId: Int): ImageView {
        return addLeftImageView(resId, -1)
    }

    fun addLeftImageView(resId: Int, index: Int): ImageView {
        val imageView = newIconView(resId)
        addSideView(imageView, leftViews, index, alignLeft = true)
        return imageView
    }

    fun addRightImageView(resId: Int): ImageView {
        return addRightImageView(resId, -1)
    }

    fun addRightImageView(resId: Int, index: Int): ImageView {
        val imageView = newIconView(resId)
        addSideView(imageView, rightViews, index, alignLeft = false)
        return imageView
    }

    fun addLeftView(view: View) {
        addSideView(view, leftViews, -1, alignLeft = true)
    }

    fun addRightView(view: View) {
        addSideView(view, rightViews, -1, alignLeft = false)
    }

    fun removeAllLeftViews() {
        removeSideViews(leftViews)
    }

    fun removeAllRightViews() {
        removeSideViews(rightViews)
    }

    fun getLeftViewCount(): Int = leftViews.count { it.visibility != View.GONE }

    fun getRightViewCount(): Int = rightViews.count { it.visibility != View.GONE }

    fun getLeftViewByIndex(index: Int): View? = leftViews.filter { it.visibility != View.GONE }.getOrNull(index)

    fun getRightViewByIndex(index: Int): View? = rightViews.filter { it.visibility != View.GONE }.getOrNull(index)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val titleBarHeightSpec = MeasureSpec.makeMeasureSpec(titleBarHeight, MeasureSpec.EXACTLY)
        super.onMeasure(
            widthMeasureSpec,
            titleBarHeightSpec,
        )
        val activeCenterView = centerView ?: titleView
        val leftWidth = sideClusterWidth(leftViews)
        val rightWidth = sideClusterWidth(rightViews)
        val widestSideWidth = max(leftWidth, rightWidth)
        val contentWidth = measuredWidth - paddingLeft - paddingRight
        val centerMaxWidth = if (widestSideWidth == 0) {
            contentWidth
        } else {
            contentWidth - widestSideWidth * 2 + marginView * 2
        }.coerceAtLeast(0)

        var needsRemeasure = false
        val shouldHideCenter = widestSideWidth > titleBarCenterLimit || centerMaxWidth == 0
        val centerVisibility = if (shouldHideCenter) View.GONE else View.VISIBLE
        if (activeCenterView.visibility != centerVisibility) {
            activeCenterView.visibility = centerVisibility
            needsRemeasure = true
        }
        if (!shouldHideCenter && limitCenterViewWidth(activeCenterView, centerMaxWidth)) {
            needsRemeasure = true
        }
        if (needsRemeasure) {
            super.onMeasure(widthMeasureSpec, titleBarHeightSpec)
        }
    }

    private fun sideClusterWidth(sideViews: List<View>): Int {
        var width = 0
        var visibleCount = 0
        sideViews.forEach { view ->
            if (view.visibility != View.GONE) {
                if (visibleCount == 0) {
                    width += marginEdge
                }
                width += view.measuredWidth + marginView
                visibleCount += 1
            }
        }
        return width
    }

    private fun limitCenterViewWidth(view: View, width: Int): Boolean {
        if (view === titleView) {
            if (titleView.maxWidth == width) {
                return false
            }
            titleView.maxWidth = width
            return true
        }
        val params = view.layoutParams ?: return false
        if (params.width == width) {
            return false
        }
        params.width = width
        return true
    }

    private fun newIconView(resId: Int): ImageView {
        return ImageView(context).apply {
            id = View.generateViewId()
            setImageResource(resId)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setOnTouchListener(TitleBarIconScaleTouchListener(this))
        }
    }

    private fun clearCenterView() {
        centerView?.let { view ->
            removeView(view)
        }
        centerView = null
        titleView.visibility = View.VISIBLE
    }

    private fun addSideView(
        view: View,
        sideViews: MutableList<View>,
        index: Int,
        alignLeft: Boolean,
    ) {
        val insertIndex = index.takeIf { it in 0..sideViews.size } ?: sideViews.size
        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
        sideViews.add(insertIndex, view)
        addView(view, sideLayoutParams())
        relayoutSideViews(sideViews, alignLeft)
    }

    private fun sideLayoutParams(): LayoutParams {
        return LayoutParams(imageViewSize, imageViewSize).apply {
            addRule(CENTER_VERTICAL)
        }
    }

    private fun relayoutSideViews(
        sideViews: List<View>,
        alignLeft: Boolean,
    ) {
        sideViews.forEachIndexed { index, view ->
            val params = (view.layoutParams as? LayoutParams) ?: sideLayoutParams()
            params.alignWithParent = true
            params.removeRule(ALIGN_PARENT_LEFT)
            params.removeRule(ALIGN_PARENT_RIGHT)
            params.removeRule(RIGHT_OF)
            params.removeRule(LEFT_OF)
            params.addRule(CENTER_VERTICAL)

            val visibleIndex = sideViews.take(index).count { it.visibility != View.GONE }
            if (alignLeft) {
                if (visibleIndex == 0) {
                    params.addRule(ALIGN_PARENT_LEFT)
                    params.leftMargin = marginEdge
                } else {
                    val previous = sideViews
                        .take(index)
                        .lastOrNull { it.visibility != View.GONE }
                    if (previous != null) {
                        params.addRule(RIGHT_OF, previous.id)
                    }
                    params.leftMargin = marginView
                }
            } else {
                if (visibleIndex == 0) {
                    params.addRule(ALIGN_PARENT_RIGHT)
                    params.rightMargin = marginEdge
                } else {
                    val previous = sideViews
                        .take(index)
                        .lastOrNull { it.visibility != View.GONE }
                    if (previous != null) {
                        params.addRule(LEFT_OF, previous.id)
                    }
                    params.rightMargin = marginView
                }
            }
            view.layoutParams = params
        }
    }

    private fun removeSideViews(sideViews: MutableList<View>) {
        sideViews.forEach { view ->
            removeView(view)
        }
        sideViews.clear()
    }

    private class TitleBarIconScaleTouchListener(
        view: View,
    ) : View.OnTouchListener {
        private val scaleXAnimation = SpringAnimation(view, SpringAnimation.SCALE_X)
        private val scaleYAnimation = SpringAnimation(view, SpringAnimation.SCALE_Y)
        private var endScale = 1f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> playScale(TitleBarIconPressedScale)
                MotionEvent.ACTION_MOVE -> playScale(if (view.isPressed) TitleBarIconPressedScale else 1f)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                    -> playScale(1f)
            }
            return false
        }

        private fun playScale(targetScale: Float) {
            if (endScale == targetScale) {
                return
            }
            endScale = targetScale
            val spring = SpringForce(targetScale)
                .setDampingRatio(TitleBarIconDampingRatio)
                .setStiffness(TitleBarIconStiffness)
            scaleXAnimation.setSpring(spring).start()
            scaleYAnimation.setSpring(spring).start()
        }
    }

    private companion object {
        private const val TitleBarIconPressedScale = 1.33f
        private const val TitleBarIconDampingRatio = 0.55f
        private const val TitleBarIconStiffness = 800f
    }
}
