package com.smartisan.music.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.BoringLayout
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.withClip
import com.smartisan.music.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws the title with the shim's normal typeface and Paint fake bold, without a TextView. */
@Composable
internal fun SmartisanTitleText(
    title: String,
    horizontalInset: Dp,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val density = LocalDensity.current.density
    val textSizePx = resources.getDimension(R.dimen.title_text_size)
    val titleColor = colorResource(R.color.title_color).toArgb()
    val locales = resources.configuration.locales
    val textDirection =
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            TextDirectionHeuristics.FIRSTSTRONG_RTL
        } else {
            TextDirectionHeuristics.FIRSTSTRONG_LTR
        }
    val paint =
        remember(textSizePx, density, titleColor, locales) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.density = density
                textSize = textSizePx
                color = titleColor
                typeface = Typeface.DEFAULT
                isFakeBoldText = true
                textLocales = locales
            }
        }
    // TextView.setSingleLine uses these same replacements before measuring and ellipsizing.
    val singleLineTitle = remember(title) { title.replace('\n', ' ').replace('\r', '\uFEFF') }
    Spacer(
        modifier
            .semantics { text = AnnotatedString(title) }
            .drawWithCache {
                val maximumWidth =
                    (size.width.roundToInt() - horizontalInset.roundToPx() * 2).coerceAtLeast(0)
                if (maximumWidth == 0 || singleLineTitle.isEmpty()) {
                    onDrawBehind {}
                } else {
                    val desiredWidth =
                        ceil(Layout.getDesiredWidth(singleLineTitle, paint))
                            .toInt()
                            .coerceAtLeast(1)
                    val measuredWidth =
                        if (Build.VERSION.SDK_INT >= 35) {
                            // targetSdk 35+ TextView includes glyph overhang in wrap-content width.
                            val measurement =
                                titleTextLayout(
                                    singleLineTitle,
                                    paint,
                                    textDirection,
                                    desiredWidth,
                                    Layout.Alignment.ALIGN_NORMAL,
                                    ellipsize = null,
                                )
                            val bounds = measurement.computeDrawingBoundingBox()
                            max(
                                desiredWidth,
                                ceil(
                                        max(bounds.right, desiredWidth.toFloat()) -
                                            min(bounds.left, 0f)
                                    )
                                    .toInt(),
                            )
                        } else {
                            desiredWidth
                        }
                    val textWidth = min(maximumWidth, measuredWidth)
                    val textLayout =
                        titleTextLayout(singleLineTitle, paint, textDirection, textWidth)
                    // RelativeLayout centers its wrap-content TextView with integer division.
                    val left = (size.width.roundToInt() - textWidth) / 2
                    val top = ((size.height.roundToInt() - textLayout.height) / 2).coerceAtLeast(0)
                    onDrawBehind {
                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            nativeCanvas.withClip(
                                left.toFloat(),
                                0f,
                                (left + textWidth).toFloat(),
                                size.height,
                            ) {
                                translate(left.toFloat(), top.toFloat())
                                textLayout.draw(this)
                            }
                        }
                    }
                }
            }
    )
}

// Public platform layout APIs, checked against Android SDK 37 TextView.makeSingleLayout
// and Compose 1.11.2 FontSynthesis.android.kt on 2026-09-05. FontSynthesis changes the
// Typeface weight, so it cannot express the shim's Paint.isFakeBoldText contract.
private fun titleTextLayout(
    text: String,
    paint: TextPaint,
    textDirection: TextDirectionHeuristic,
    width: Int,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    ellipsize: TextUtils.TruncateAt? = TextUtils.TruncateAt.END,
): Layout {
    if (Build.VERSION.SDK_INT >= 35) {
        return Layout.Builder(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setTextDirectionHeuristic(textDirection)
            .setFontPaddingIncluded(true)
            .setFallbackLineSpacingEnabled(true)
            .setUseBoundsForWidth(true)
            .setMaxLines(1)
            .setEllipsize(ellipsize)
            .setEllipsizedWidth(width)
            .build()
    }
    val boring =
        if (Build.VERSION.SDK_INT >= 33) {
            BoringLayout.isBoring(text, paint, textDirection, true, null)
        } else if (!textDirection.isRtl(text, 0, text.length)) {
            BoringLayout.isBoring(text, paint)
        } else {
            null
        }
    if (boring != null) {
        return if (Build.VERSION.SDK_INT >= 33) {
            BoringLayout.make(text, paint, width, alignment, boring, true, ellipsize, width, true)
        } else {
            BoringLayout.make(text, paint, width, alignment, 1f, 0f, boring, true, ellipsize, width)
        }
    }
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(alignment)
        .setTextDirection(textDirection)
        .setIncludePad(true)
        .setMaxLines(1)
        .setEllipsize(ellipsize)
        .setEllipsizedWidth(width)
        .apply {
            if (Build.VERSION.SDK_INT >= 28) setUseLineSpacingFromFallbacks(true)
        }
        .build()
}
