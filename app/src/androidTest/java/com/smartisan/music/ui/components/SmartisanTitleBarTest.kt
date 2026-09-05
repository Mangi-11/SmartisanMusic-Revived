package com.smartisan.music.ui.components

import android.content.res.Configuration
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartisan.music.R
import com.smartisan.music.ui.reference.ReferenceTitleBar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartisanTitleBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun titlePixelsMatchFakeBoldForLatinCjkAndEllipsis() {
        val samples =
            listOf(
                TitleSample("Smartisan Music"),
                TitleSample("应用图标"),
                TitleSample(
                    "A long music library title that must end in an ellipsis",
                    fontScale = 1.5f,
                ),
                TitleSample("外观与主题设置", fontScale = 1.5f, night = true),
            )
        var sample by mutableStateOf(samples.first())
        var contentInsetPx = 0
        compose.setContent {
            val baseContext = LocalContext.current
            val baseConfiguration = LocalConfiguration.current
            val configuredContext =
                remember(baseContext, baseConfiguration, sample) {
                    val configuration =
                        Configuration(baseConfiguration).apply {
                            fontScale = sample.fontScale
                            uiMode =
                                (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                    if (sample.night) Configuration.UI_MODE_NIGHT_YES
                                    else Configuration.UI_MODE_NIGHT_NO
                        }
                    ContextThemeWrapper(
                        baseContext.createConfigurationContext(configuration),
                        R.style.Theme_Music,
                    )
                }
            val resources = configuredContext.resources
            contentInsetPx =
                resources.getDimensionPixelSize(R.dimen.bar_margin_edge) +
                    resources.getDimensionPixelSize(R.dimen.standard_icon_size)
            CompositionLocalProvider(
                LocalContext provides configuredContext,
                LocalResources provides resources,
                LocalConfiguration provides resources.configuration,
                LocalDensity provides Density(resources.displayMetrics.density, sample.fontScale),
            ) {
                Column(Modifier.width(280.dp)) {
                    SmartisanTitleBar(
                        title = sample.title,
                        navigationIcon =
                            SmartisanTitleBarAction(
                                R.drawable.standard_icon_back_selector,
                                "Back",
                                onClick = {},
                            ),
                        includeStatusBar = false,
                        showShadow = false,
                        modifier = Modifier.testTag(ComposeTitleTag),
                    )
                    key(sample) {
                        AndroidView(
                            factory = { context ->
                                ReferenceTitleBar(context).apply {
                                    setCenterText(sample.title)
                                    setShadowVisible(false)
                                    addLeftImageView(R.drawable.standard_icon_back_selector)
                                }
                            },
                            modifier = Modifier.width(280.dp).height(50.dp).testTag(TitleTag),
                        )
                    }
                }
            }
        }

        samples.forEach { nextSample ->
            compose.runOnIdle { sample = nextSample }
            compose
                .onNode(hasText(nextSample.title) and hasAnyAncestor(hasTestTag(ComposeTitleTag)))
                .assertIsDisplayed()
            val actual = compose.onNodeWithTag(ComposeTitleTag).captureToImage()
            val expected = compose.onNodeWithTag(TitleTag).captureToImage()
            assertTitlePixelsMatch(expected, actual, contentInsetPx, nextSample.toString())
        }
    }

    @Test
    fun buttonEdgeTapsMatchThe36DpTarget() {
        var composeClicks = 0
        var clicks = 0
        lateinit var titleBar: ReferenceTitleBar
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            Column(Modifier.width(280.dp)) {
                SmartisanTitleBar(
                    title = "Music",
                    navigationIcon =
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_back_selector,
                            "Back",
                            onClick = {
                                composeClicks++
                            },
                        ),
                    includeStatusBar = false,
                    showShadow = false,
                    modifier = Modifier.testTag(ComposeTitleTag),
                )
                AndroidView(
                    factory = { context ->
                        ReferenceTitleBar(context).apply {
                            setCenterText("Music")
                            setShadowVisible(false)
                            addLeftImageView(R.drawable.standard_icon_back_selector)
                                .setOnClickListener {
                                    clicks++
                                }
                            titleBar = this
                        }
                    },
                    modifier = Modifier.width(280.dp).height(50.dp),
                )
            }
        }

        // The icon occupies x=[6,42), y=[7,43) in the calibrated 50dp title bar.
        // Check outside and inside all four edges, not only the center of its semantic node.
        val samples =
            listOf(
                Offset(5f, 25f) to false,
                Offset(43f, 25f) to false,
                Offset(24f, 6f) to false,
                Offset(24f, 44f) to false,
                Offset(7f, 25f) to true,
                Offset(41f, 25f) to true,
                Offset(24f, 8f) to true,
                Offset(24f, 42f) to true,
                Offset(24f, 25f) to true,
            )
        var expectedClicks = 0
        samples.forEach { (positionDp, shouldClick) ->
            compose.onNodeWithTag(ComposeTitleTag).performTouchInput {
                click(positionDp * density)
            }
            compose.runOnIdle {
                val time = SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed {
                    index,
                    action ->
                    val event =
                        MotionEvent.obtain(
                            time,
                            time + index * 10L,
                            action,
                            positionDp.x * density,
                            positionDp.y * density,
                            0,
                        )
                    try {
                        titleBar.dispatchTouchEvent(event)
                    } finally {
                        event.recycle()
                    }
                }
            }

            // View.onTouchEvent posts PerformClick on ACTION_UP. Let that queued callback
            // run before comparing it with Compose, whose injected gesture has already settled.
            compose.runOnIdle {
                if (shouldClick) expectedClicks++
                assertEquals("Reference tap at ${positionDp}dp", expectedClicks, clicks)
                assertEquals("Compose tap at ${positionDp}dp", clicks, composeClicks)
            }
        }
        compose.runOnIdle { assertEquals(5, composeClicks) }
    }

    private fun assertTitlePixelsMatch(
        expected: ImageBitmap,
        actual: ImageBitmap,
        inset: Int,
        label: String,
    ) {
        assertEquals("$label width", expected.width, actual.width)
        assertEquals("$label height", expected.height, actual.height)
        val expectedPixels = expected.toPixelMap()
        val actualPixels = actual.toPixelMap()
        val xRange = (inset + 2) until (expected.width - inset - 2)
        val yRange = 2 until (expected.height - 2)
        var bestMismatchCount = Int.MAX_VALUE
        // Allow one physical pixel of placement rounding and small GPU antialiasing differences.
        // A real bold Typeface changes the glyph shapes and fails this whole-title comparison.
        for (dx in -1..1) {
            for (dy in -1..1) {
                var mismatchCount = 0
                for (y in yRange) {
                    for (x in xRange) {
                        val reference = expectedPixels[x, y]
                        val rendered = actualPixels[x + dx, y + dy]
                        val channelError =
                            max(
                                abs(reference.red - rendered.red),
                                max(
                                    abs(reference.green - rendered.green),
                                    abs(reference.blue - rendered.blue),
                                ),
                            )
                        if (channelError > 16f / 255f) mismatchCount++
                    }
                }
                bestMismatchCount = minOf(bestMismatchCount, mismatchCount)
            }
        }
        val allowedMismatches = (xRange.count() * yRange.count() * 0.0025f).roundToInt()
        assertTrue(
            "$label: $bestMismatchCount title pixels differ from the reference View (limit $allowedMismatches)",
            bestMismatchCount <= allowedMismatches,
        )
    }

    private data class TitleSample(
        val title: String,
        val fontScale: Float = 1f,
        val night: Boolean = false,
    )

    private companion object {
        const val ComposeTitleTag = "compose-title"
        const val TitleTag = "reference-title"
    }
}
