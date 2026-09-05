package com.smartisan.music.ui.components

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.annotation.AnyRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import kotlin.math.roundToInt

/** Keeps Android selectors and nine-patch stretch regions intact without hosting a View. */
@Composable
internal fun rememberSmartisanDrawablePainter(
    @DrawableRes drawableRes: Int,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
    focused: Boolean = false,
    checked: Boolean = false,
    progressLevel: Int? = null,
    activated: Boolean = false,
): Painter {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val painter =
        remember(context, configuration, drawableRes) {
                DrawablePainterOwner(
                    requireNotNull(AppCompatResources.getDrawable(context, drawableRes)).mutate()
                )
            }
            .painter
    UpdateDrawableState(painter, enabled, pressed, selected, focused, checked, activated)
    SideEffect {
        progressLevel?.let { level ->
            (painter.drawable as? LayerDrawable)?.apply {
                findDrawableByLayerId(android.R.id.progress)?.level = level.coerceIn(0, 10_000)
                findDrawableByLayerId(android.R.id.secondaryProgress)?.level = 0
            }
        }
    }
    return painter
}

/** Projects the original group-row shadow outside its bounds, as the View background did. */
@Composable
internal fun Modifier.smartisanShadowBackground(
    @DrawableRes backgroundRes: Int,
    @DrawableRes shadowRes: Int,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
    focused: Boolean = false,
): Modifier {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val painter =
        remember(context, configuration, backgroundRes, shadowRes) {
                val target =
                    requireNotNull(AppCompatResources.getDrawable(context, backgroundRes)).mutate()
                val shadow =
                    requireNotNull(AppCompatResources.getDrawable(context, shadowRes)).mutate()
                val padding = Rect()
                shadow.getPadding(padding)
                DrawablePainterOwner(ShadowDrawable(shadow, target, padding.left, padding.top))
            }
            .painter
    UpdateDrawableState(painter, enabled, pressed, selected, focused)
    return smartisanPainterBackground(painter)
}

/** Unlike colorResource, this preserves enabled, pressed and keyboard-focus colors. */
@Composable
@SuppressLint("LocalContextGetResourceValueCall")
internal fun smartisanStateColor(
    @AnyRes colorRes: Int,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
    focused: Boolean = false,
    activated: Boolean = false,
): Color {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = remember(context, configuration, colorRes) { context.getColorStateList(colorRes) }
    return Color(
        colors.getColorForState(
            drawableState(enabled, pressed, selected, focused, activated = activated),
            colors.defaultColor,
        )
    )
}

@Composable
internal fun smartisanTextSize(@DimenRes dimensionRes: Int): TextUnit {
    val pixels = LocalResources.current.getDimension(dimensionRes)
    // XML contains both dp and sp text dimensions. Convert the resolved pixels instead of
    // treating every resource as sp, which would apply font scaling twice to sp values.
    return with(LocalDensity.current) { pixels.toSp() }
}

@Composable
private fun UpdateDrawableState(
    painter: SmartisanDrawablePainter,
    enabled: Boolean,
    pressed: Boolean,
    selected: Boolean,
    focused: Boolean,
    checked: Boolean = false,
    activated: Boolean = false,
) {
    val direction = LocalLayoutDirection.current
    SideEffect {
        painter.drawable.state =
            drawableState(enabled, pressed, selected, focused, checked, activated)
        painter.drawable.layoutDirection =
            if (direction == LayoutDirection.Rtl) {
                View.LAYOUT_DIRECTION_RTL
            } else {
                View.LAYOUT_DIRECTION_LTR
            }
    }
}

private fun drawableState(
    enabled: Boolean,
    pressed: Boolean,
    selected: Boolean,
    focused: Boolean,
    checked: Boolean = false,
    activated: Boolean = false,
): IntArray =
    intArrayOf(
        if (enabled) android.R.attr.state_enabled else -android.R.attr.state_enabled,
        if (pressed) android.R.attr.state_pressed else -android.R.attr.state_pressed,
        if (selected) android.R.attr.state_selected else -android.R.attr.state_selected,
        if (focused) android.R.attr.state_focused else -android.R.attr.state_focused,
        if (checked) android.R.attr.state_checked else -android.R.attr.state_checked,
        if (activated) android.R.attr.state_activated else -android.R.attr.state_activated,
    )

// Keep the observer private to its original remember scope: a caller remembering the
// returned Painter must not acquire a second, shorter lifetime for its drawable callbacks.
private class DrawablePainterOwner(drawable: Drawable) : RememberObserver {
    val painter = SmartisanDrawablePainter(drawable)

    override fun onRemembered() = painter.attach()

    override fun onForgotten() = painter.detach()

    override fun onAbandoned() = painter.detach()
}

private class SmartisanDrawablePainter(val drawable: Drawable) : Painter(), Drawable.Callback {
    private var invalidation by mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())

    override val intrinsicSize: Size
        get() =
            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            } else {
                Size.Unspecified
            }

    override fun DrawScope.onDraw() {
        // Read during drawing so drawable callbacks invalidate only this draw scope.
        @Suppress("UNUSED_VARIABLE") val generation = invalidation
        drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }

    fun attach() {
        drawable.callback = this
        drawable.setVisible(true, true)
    }

    fun detach() {
        drawable.setVisible(false, false)
        drawable.callback = null
        handler.removeCallbacksAndMessages(drawable)
    }

    override fun invalidateDrawable(who: Drawable) {
        invalidation++
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        handler.postAtTime(what, drawable, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        handler.removeCallbacks(what, drawable)
    }
}
