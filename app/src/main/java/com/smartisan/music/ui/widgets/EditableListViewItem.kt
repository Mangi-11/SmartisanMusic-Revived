package com.smartisan.music.ui.widgets

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.ImageView
import com.smartisan.music.R
import android.widget.RelativeLayout

class EditableListViewItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
    private var editMode = false
    private var playlistEditMode = false
    private var animator: Animator? = null
    private var playlistAnimator: Animator? = null

    fun bindLegacyEditState(
        enabled: Boolean,
        checked: Boolean,
        animate: Boolean,
    ) {
        val checkbox = findViewById<CheckBox>(R.id.cb_del) ?: return
        val content = findViewById<View>(R.id.relativeLayout1) ?: return
        val durationView = findViewById<View>(R.id.tv_duration)
        val more = findViewById<ImageView>(R.id.img_action_more)
        val offset = legacyCheckboxOffset(checkbox)
        val oldMode = editMode
        editMode = enabled
        checkbox.isChecked = checked
        checkbox.isClickable = false
        checkbox.isFocusable = false
        durationView?.visibility = if (enabled) View.GONE else View.VISIBLE
        more?.visibility = if (enabled) View.GONE else View.VISIBLE

        if (!animate || oldMode == enabled) {
            animator?.cancel()
            checkbox.visibility = if (enabled) View.VISIBLE else View.GONE
            checkbox.alpha = if (enabled) 1f else 0f
            checkbox.translationX = if (enabled) 0f else -offset
            content.translationX = if (enabled) offset else 0f
            return
        }

        animator?.cancel()
        checkbox.visibility = View.VISIBLE
        durationView?.visibility = View.GONE
        more?.visibility = View.GONE
        val nextAnimator = AnimatorSet().apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            playTogether(
                ObjectAnimator.ofFloat(checkbox, View.TRANSLATION_X, checkbox.translationX, if (enabled) 0f else -offset),
                ObjectAnimator.ofFloat(checkbox, View.ALPHA, checkbox.alpha, if (enabled) 1f else 0f),
                ObjectAnimator.ofFloat(content, View.TRANSLATION_X, content.translationX, if (enabled) offset else 0f),
            )
            addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        checkbox.visibility = if (enabled) View.VISIBLE else View.GONE
                        checkbox.alpha = if (enabled) 1f else 0f
                        checkbox.translationX = if (enabled) 0f else -offset
                        content.translationX = if (enabled) offset else 0f
                        durationView?.visibility = if (enabled) View.GONE else View.VISIBLE
                        more?.visibility = if (enabled) View.GONE else View.VISIBLE
                    }
                },
            )
        }
        animator = nextAnimator
        nextAnimator.start()
    }

    fun bindLegacyPlaylistEditState(
        enabled: Boolean,
        checked: Boolean,
        selectionOnly: Boolean,
        animate: Boolean,
    ) {
        val checkbox = findViewById<CheckBox>(R.id.cb_del) ?: return
        val drag = findViewById<ImageView>(R.id.iv_right)
        val durationView = findViewById<View>(R.id.tv_duration)
        val more = findViewById<ImageView>(R.id.img_action_more)
        val checkboxParams = checkbox.layoutParams as? LayoutParams ?: return
        val dragParams = drag?.layoutParams as? LayoutParams
        val oldMode = playlistEditMode
        playlistEditMode = enabled
        checkbox.isChecked = checked
        checkbox.isClickable = false
        checkbox.isFocusable = false

        val shownCheckboxMargin = shownLeftMargin(checkbox)
        val hiddenCheckboxMargin = -measuredWidthOf(checkbox)
        val shownDragMargin = dragParams?.rightMargin?.takeIf { it >= 0 }
            ?: resources.getDimensionPixelSize(R.dimen.listview_items_margin_right)
        val hiddenDragMargin = drag?.let { -measuredWidthOf(it) } ?: 0
        val targetCheckboxMargin = if (enabled) shownCheckboxMargin else hiddenCheckboxMargin
        val targetDragMargin = if (enabled && !selectionOnly) shownDragMargin else hiddenDragMargin

        if (!animate || oldMode == enabled) {
            playlistAnimator?.cancel()
            checkboxParams.leftMargin = targetCheckboxMargin
            checkbox.layoutParams = checkboxParams
            dragParams?.rightMargin = targetDragMargin
            if (drag != null && dragParams != null) {
                drag.layoutParams = dragParams
            }
            checkbox.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
            drag?.visibility = if (selectionOnly) View.GONE else View.VISIBLE
            durationView?.visibility = if (selectionOnly) View.GONE else View.VISIBLE
            more?.visibility = if (enabled) View.GONE else View.VISIBLE
            requestLayout()
            return
        }

        playlistAnimator?.cancel()
        checkbox.visibility = View.VISIBLE
        drag?.visibility = if (selectionOnly) View.GONE else View.VISIBLE
        durationView?.visibility = if (selectionOnly) View.GONE else View.VISIBLE
        more?.visibility = if (enabled) View.GONE else View.VISIBLE

        val animators = mutableListOf<Animator>(
            ValueAnimator.ofInt(checkboxParams.leftMargin, targetCheckboxMargin).apply {
                addUpdateListener { valueAnimator ->
                    checkboxParams.leftMargin = valueAnimator.animatedValue as Int
                    checkbox.layoutParams = checkboxParams
                    requestLayout()
                }
            },
        )
        if (drag != null && dragParams != null && !selectionOnly) {
            animators += ValueAnimator.ofInt(dragParams.rightMargin, targetDragMargin).apply {
                addUpdateListener { valueAnimator ->
                    dragParams.rightMargin = valueAnimator.animatedValue as Int
                    drag.layoutParams = dragParams
                    requestLayout()
                }
            }
        }
        val nextAnimator = AnimatorSet().apply {
            duration = 200L
            playTogether(animators)
            addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        checkboxParams.leftMargin = targetCheckboxMargin
                        checkbox.layoutParams = checkboxParams
                        dragParams?.rightMargin = targetDragMargin
                        if (drag != null && dragParams != null) {
                            drag.layoutParams = dragParams
                        }
                        checkbox.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
                        drag?.visibility = if (selectionOnly) View.GONE else View.VISIBLE
                        durationView?.visibility = if (selectionOnly) View.GONE else View.VISIBLE
                        more?.visibility = if (enabled) View.GONE else View.VISIBLE
                        requestLayout()
                    }
                },
            )
        }
        playlistAnimator = nextAnimator
        nextAnimator.start()
    }

    private fun legacyCheckboxOffset(checkbox: CheckBox): Float {
        if (checkbox.measuredWidth <= 0) {
            checkbox.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
        }
        val params = checkbox.layoutParams as? MarginLayoutParams
        return ((params?.leftMargin ?: 0) + checkbox.measuredWidth).toFloat()
    }

    private fun shownLeftMargin(view: View): Int {
        val params = view.layoutParams as? MarginLayoutParams
        return params?.leftMargin?.takeIf { it >= 0 }
            ?: resources.getDimensionPixelSize(R.dimen.check_box_margin_left)
    }

    private fun measuredWidthOf(view: View): Int {
        if (view.measuredWidth <= 0) {
            view.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
        }
        return view.measuredWidth
    }
}
