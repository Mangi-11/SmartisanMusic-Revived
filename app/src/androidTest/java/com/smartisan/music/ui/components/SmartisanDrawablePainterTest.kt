package com.smartisan.music.ui.components

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.NinePatchDrawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartisan.music.R
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartisanDrawablePainterTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var renderedResources: RenderedResources
    private lateinit var referenceView: ReferenceDrawableView

    @Test
    fun selectorAndColorStateListUpdateWithoutReplacingThePainter() {
        var state by mutableStateOf(DrawableState())
        compose.setContent {
            DrawableSample(R.drawable.group_list_item_bg_mid, state)
        }
        val originalPainter = compose.runOnIdle { renderedResources.painter }
        val normal = captureDrawable()
        assertMatchesNativeDrawable(normal, R.drawable.group_list_item_bg_mid)

        listOf(
                DrawableState(pressed = true),
                DrawableState(focused = true),
                DrawableState(enabled = false, pressed = true),
                DrawableState(),
            )
            .forEach { nextState ->
                compose.runOnIdle { state = nextState }
                val actual = captureDrawable()
                val resources = compose.runOnIdle { renderedResources }
                assertSame(originalPainter, resources.painter)
                assertMatchesNativeDrawable(actual, R.drawable.group_list_item_bg_mid, nextState)
                assertMatchesNativeColorStateList(resources, nextState)
                if (nextState.pressed && nextState.enabled) {
                    assertNotEquals(centerPixel(normal), centerPixel(actual))
                }
            }
    }

    @Test
    fun nightConfigurationReloadsDrawableAndColorResources() {
        var night by mutableStateOf(false)
        compose.setContent {
            val baseContext = LocalContext.current
            val baseConfiguration = LocalConfiguration.current
            val context =
                remember(baseContext, baseConfiguration, night) {
                    val configuration =
                        Configuration(baseConfiguration).apply {
                            uiMode =
                                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                    if (night) Configuration.UI_MODE_NIGHT_YES
                                    else Configuration.UI_MODE_NIGHT_NO
                        }
                    baseContext.createConfigurationContext(configuration)
                }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
            ) {
                DrawableSample(R.drawable.group_list_item_bg_mid)
            }
        }
        val dayImage = captureDrawable()
        val dayResources = compose.runOnIdle { renderedResources }
        assertMatchesNativeDrawable(dayImage, R.drawable.group_list_item_bg_mid)
        assertMatchesNativeColorStateList(dayResources)

        compose.runOnIdle { night = true }
        val nightImage = captureDrawable()
        val nightResources = compose.runOnIdle { renderedResources }
        assertNotSame(dayResources.painter, nightResources.painter)
        assertNotEquals(centerPixel(dayImage), centerPixel(nightImage))
        assertNotEquals(dayResources.color, nightResources.color)
        assertMatchesNativeDrawable(nightImage, R.drawable.group_list_item_bg_mid)
        assertMatchesNativeColorStateList(nightResources)

        compose.runOnIdle { night = false }
        val restoredDayImage = captureDrawable()
        assertPixelsClose(dayImage, pixels(restoredDayImage), "restoring day configuration")
    }

    @Test
    fun ninePatchStretchingMatchesNativeDrawingAfterBoundsChange() {
        var dimensions by mutableStateOf(DpSize(173.dp, 61.dp))
        compose.setContent {
            DrawableSample(R.drawable.group_list_mid, dimensions = dimensions)
        }
        val resources = compose.runOnIdle { renderedResources }
        assertTrue(resources.context.getDrawable(R.drawable.group_list_mid) is NinePatchDrawable)
        val firstImage = captureDrawable()
        assertMatchesNativeDrawable(firstImage, R.drawable.group_list_mid)

        compose.runOnIdle { dimensions = DpSize(251.dp, 91.dp) }
        val resizedImage = captureDrawable()
        compose.runOnIdle { assertSame(resources.painter, renderedResources.painter) }
        assertNotEquals(firstImage.width, resizedImage.width)
        assertNotEquals(firstImage.height, resizedImage.height)
        assertMatchesNativeDrawable(resizedImage, R.drawable.group_list_mid)
    }

    @Composable
    private fun DrawableSample(
        @DrawableRes resource: Int,
        state: DrawableState = DrawableState(),
        dimensions: DpSize = DpSize(173.dp, 61.dp),
    ) {
        val context = LocalContext.current
        val painter =
            rememberSmartisanDrawablePainter(
                resource,
                enabled = state.enabled,
                pressed = state.pressed,
                focused = state.focused,
            )
        val color =
            smartisanStateColor(
                R.color.setting_item_text_colorlist,
                enabled = state.enabled,
                pressed = state.pressed,
                focused = state.focused,
            )
        val layoutDirection =
            if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                View.LAYOUT_DIRECTION_RTL
            } else {
                View.LAYOUT_DIRECTION_LTR
            }
        SideEffect {
            renderedResources = RenderedResources(context, painter, color.toArgb())
        }
        Column {
            Box(
                Modifier.size(dimensions)
                    .background(Color.White)
                    .smartisanPainterBackground(painter)
                    .testTag(DrawableTag)
            )
            Box(Modifier.size(20.dp).background(color).testTag(ColorTag))
            key(context, resource) {
                AndroidView(
                    factory = { referenceContext ->
                        ReferenceDrawableView(referenceContext, resource).also {
                            referenceView = it
                        }
                    },
                    update = { it.update(state.platformState(), layoutDirection) },
                    modifier = Modifier.size(dimensions).testTag(ReferenceTag),
                )
            }
        }
    }

    private fun captureDrawable(): ImageBitmap = compose.onNodeWithTag(DrawableTag).captureToImage()

    private fun assertMatchesNativeColorStateList(
        resources: RenderedResources,
        state: DrawableState = DrawableState(),
    ) {
        val colors = resources.context.getColorStateList(R.color.setting_item_text_colorlist)
        val expected = colors.getColorForState(state.platformState(), colors.defaultColor)
        assertEquals(expected, resources.color)
        assertEquals(expected, centerPixel(compose.onNodeWithTag(ColorTag).captureToImage()))
    }

    private fun assertMatchesNativeDrawable(
        actual: ImageBitmap,
        @DrawableRes resource: Int,
        state: DrawableState = DrawableState(),
    ) {
        // Compare the actual View and Compose screen paths on the same GPU. A bitmap
        // Canvas uses a different rasterizer, including nine-patch density-scaled edges.
        val reference = compose.onNodeWithTag(ReferenceTag).captureToImage()
        compose.runOnIdle {
            assertTrue(
                "The reference must use the hardware Canvas",
                referenceView.hardwareCanvasUsed,
            )
        }
        assertEquals("drawable width", reference.width, actual.width)
        assertEquals("drawable height", reference.height, actual.height)
        assertPixelsClose(actual, pixels(reference), "drawable $resource / $state")
    }

    private fun assertPixelsClose(actual: ImageBitmap, expected: IntArray, description: String) {
        val captured = pixels(actual)
        assertEquals(expected.size, captured.size)
        var largestDifference = 0
        var worstPixel = 0
        captured.indices.forEach { index ->
            for (shift in 0..24 step 8) {
                val difference =
                    abs(
                        ((captured[index] ushr shift) and 0xff) -
                            ((expected[index] ushr shift) and 0xff)
                    )
                if (difference > largestDifference) {
                    largestDifference = difference
                    worstPixel = index
                }
            }
        }
        // Permit only 3/255 per channel for raster rounding, including nine-patch edges.
        assertTrue(
            "$description differs by $largestDifference at (${worstPixel % actual.width}, ${worstPixel / actual.width})",
            largestDifference <= 3,
        )
    }

    private fun pixels(image: ImageBitmap): IntArray =
        IntArray(image.width * image.height).also { image.readPixels(it) }

    private fun centerPixel(image: ImageBitmap): Int =
        pixels(image)[image.width * (image.height / 2) + image.width / 2]

    private data class RenderedResources(
        val context: Context,
        val painter: Painter,
        val color: Int,
    )

    private class ReferenceDrawableView(context: Context, @DrawableRes resource: Int) :
        View(context) {
        private val referenceDrawable = requireNotNull(context.getDrawable(resource)).mutate()
        private val backgroundPaint = Paint().apply { color = android.graphics.Color.WHITE }
        var hardwareCanvasUsed: Boolean = false
            private set

        init {
            setWillNotDraw(false)
        }

        fun update(state: IntArray, direction: Int) {
            referenceDrawable.state = state
            referenceDrawable.layoutDirection = direction
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            hardwareCanvasUsed = canvas.isHardwareAccelerated
            val saveCount = canvas.save()
            try {
                // AndroidView's display list shares the Compose surface. Bound the reference
                // explicitly so its background cannot clear the neighboring Compose sample.
                canvas.clipRect(0, 0, width, height)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
                referenceDrawable.setBounds(0, 0, width, height)
                referenceDrawable.draw(canvas)
            } finally {
                canvas.restoreToCount(saveCount)
            }
        }
    }

    private data class DrawableState(
        val enabled: Boolean = true,
        val pressed: Boolean = false,
        val focused: Boolean = false,
    ) {
        fun platformState(): IntArray = buildList {
            if (enabled) add(android.R.attr.state_enabled)
            if (pressed) add(android.R.attr.state_pressed)
            if (focused) add(android.R.attr.state_focused)
        }
            .toIntArray()
    }

    private companion object {
        const val DrawableTag = "drawable-sample"
        const val ColorTag = "state-color-sample"
        const val ReferenceTag = "reference-drawable"
    }
}
