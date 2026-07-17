package com.smartisan.music.ui.widgets.legacy

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

class ShadowDrawable(
    private val shadow: Drawable,
    private val target: Drawable,
    private val insetLeftRight: Int,
    private val insetTopBottom: Int,
) : Drawable(), Drawable.Callback {

    private var shouldProjectBackwards = true

    init {
        shadow.callback = this
        target.callback = this
    }

    override fun draw(canvas: Canvas) {
        shadow.draw(canvas)
        target.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        target.bounds = bounds
        shadow.bounds = Rect(bounds).apply {
            inset(-insetLeftRight, -insetTopBottom)
        }
    }

    override fun isStateful(): Boolean = shadow.isStateful || target.isStateful

    override fun onStateChange(state: IntArray): Boolean {
        val targetChanged = if (target.isStateful) target.setState(state) else false
        val shadowChanged = if (shadow.isStateful) shadow.setState(state) else false
        if (targetChanged || shadowChanged) {
            invalidateSelf()
        }
        return targetChanged || shadowChanged
    }

    override fun getPadding(padding: Rect): Boolean = target.getPadding(padding)

    override fun setAlpha(alpha: Int) {
        shadow.alpha = alpha
        target.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        shadow.colorFilter = colorFilter
        target.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android SDK")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = target.intrinsicWidth

    override fun getIntrinsicHeight(): Int = target.intrinsicHeight

    override fun getMinimumWidth(): Int = target.minimumWidth

    override fun getMinimumHeight(): Int = target.minimumHeight

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    fun getBackgroundDrawable(): Drawable = target

    fun setProjectBackwards(shouldProject: Boolean) {
        shouldProjectBackwards = shouldProject
    }
}
