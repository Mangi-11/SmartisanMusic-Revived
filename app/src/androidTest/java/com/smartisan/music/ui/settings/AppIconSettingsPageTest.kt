package com.smartisan.music.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartisan.music.R
import com.smartisan.music.launcher.AppIcon
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconSettingsPageTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun selectionUsesParentStateAndIgnoresTheAlreadySelectedIcon() {
        val selectedIcon = mutableStateOf(AppIcon.Original)
        val requests = mutableListOf<AppIcon>()
        composeRule.setContent {
            AppIconSettingsPage(
                active = true,
                selectedIcon = selectedIcon.value,
                onClose = {},
                onIconSelected = { requests += it },
            )
        }

        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(2)
        composeRule
            .onNodeWithContentDescription(description(AppIcon.Original))
            .assertIsSelected()
            .performClick()
        composeRule.runOnIdle { assertTrue(requests.isEmpty()) }

        composeRule
            .onNodeWithContentDescription(description(AppIcon.Modern))
            .assertIsNotSelected()
            .performClick()
        composeRule.runOnIdle { assertEquals(listOf(AppIcon.Modern), requests) }
        // A launcher change may fail; the page must wait for the owner to confirm the new icon.
        composeRule.onNodeWithContentDescription(description(AppIcon.Original)).assertIsSelected()
        composeRule.onNodeWithContentDescription(description(AppIcon.Modern)).assertIsNotSelected()

        composeRule.runOnIdle { selectedIcon.value = AppIcon.Modern }
        composeRule
            .onNodeWithContentDescription(description(AppIcon.Modern))
            .assertIsSelected()
            .performClick()
        composeRule
            .onNodeWithContentDescription(description(AppIcon.Original))
            .assertIsNotSelected()
        composeRule.runOnIdle { assertEquals(listOf(AppIcon.Modern), requests) }
    }

    @Test
    fun backActionUsesTheLatestCallback() {
        var initialCloseCount = 0
        var replacementCloseCount = 0
        val onClose = mutableStateOf<() -> Unit>({ initialCloseCount += 1 })
        composeRule.setContent {
            AppIconSettingsPage(
                active = true,
                selectedIcon = AppIcon.Modern,
                onClose = onClose.value,
                onIconSelected = {},
            )
        }
        composeRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        composeRule.runOnIdle { onClose.value = { replacementCloseCount += 1 } }
        composeRule.onNodeWithContentDescription(string(R.string.back)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, initialCloseCount)
            assertEquals(1, replacementCloseCount)
        }
    }

    @Test
    fun hidingThePageRemovesInputTargetsAndPreservesScrollPosition() {
        val active = mutableStateOf(true)
        composeRule.setContent {
            Box(Modifier.height(220.dp)) {
                AppIconSettingsPage(
                    active = active.value,
                    selectedIcon = AppIcon.Modern,
                    onClose = {},
                    onIconSelected = {},
                )
            }
        }
        composeRule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 80f)
        }
        composeRule.waitForIdle()
        val originalScrollPosition = scrollPosition()
        assertTrue(originalScrollPosition > 0f)

        composeRule.runOnIdle { active.value = false }
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(0)

        composeRule.runOnIdle { active.value = true }
        composeRule.waitForIdle()
        assertEquals(originalScrollPosition, scrollPosition(), 0.5f)
    }

    @Test
    fun rtlKeepsThePreviewAndSelectionMarkOnTheirPhysicalSides() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                AppIconSettingsPage(
                    active = true,
                    selectedIcon = AppIcon.Modern,
                    onClose = {},
                    onIconSelected = {},
                )
            }
        }
        val option = composeRule.onNodeWithContentDescription(description(AppIcon.Modern))
        val leftToRight = option.captureToImage().toPixelMap()
        composeRule.runOnIdle { direction.value = LayoutDirection.Rtl }
        val rightToLeft = option.captureToImage().toPixelMap()
        assertEquals(leftToRight.width, rightToLeft.width)
        assertEquals(leftToRight.height, rightToLeft.height)

        // The View layout anchors the 40 dp preview left and the 28 dp mark right.
        // Compare only those image regions; text correctly follows the locale's direction.
        val pixelsPerDp = leftToRight.height / 60f
        val previewRegionEnd = (56f * pixelsPerDp).roundToInt()
        val markRegionStart = leftToRight.width - (42f * pixelsPerDp).roundToInt()
        for (y in 0 until leftToRight.height) {
            for (x in 0 until leftToRight.width) {
                if (x < previewRegionEnd || x >= markRegionStart) {
                    assertEquals(
                        "Icon placement changed at ($x, $y)",
                        leftToRight[x, y],
                        rightToLeft[x, y],
                    )
                }
            }
        }
    }

    private fun scrollPosition(): Float {
        return composeRule
            .onNode(hasScrollAction())
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
            .value()
    }

    private fun description(icon: AppIcon): String {
        val summaryRes =
            when (icon) {
                AppIcon.Original -> R.string.app_icon_original_summary
                AppIcon.Modern -> R.string.app_icon_modern_summary
            }
        return InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(
                R.string.app_icon_option_description,
                string(icon.labelRes()),
                string(summaryRes),
            )
    }

    private fun string(resourceId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
    }
}
