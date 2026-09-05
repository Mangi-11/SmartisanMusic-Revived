package com.smartisan.music.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartisan.music.R
import com.smartisan.music.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsPageTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun radioChoicesEmitEachThemeAndWaitForTheOwnerToUpdateSelection() {
        val selectedMode = mutableStateOf(ThemeMode.System)
        val requests = mutableListOf<ThemeMode>()
        composeRule.setContent {
            ThemeSettingsPage(
                active = true,
                themeMode = selectedMode.value,
                onClose = {},
                onThemeModeChange = { requests += it },
            )
        }

        composeRule.onAllNodes(radioRole).assertCountEquals(3)
        listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System).forEach { requestedMode ->
            composeRule
                .onNodeWithText(string(requestedMode.labelRes))
                .assert(radioRole)
                .assertIsNotSelected()
                .performClick()
            composeRule.runOnIdle { assertEquals(requestedMode, requests.last()) }

            // The settings owner confirms the change; a request is not yet an applied theme.
            composeRule.onNodeWithText(string(selectedMode.value.labelRes)).assertIsSelected()
            composeRule.onNodeWithText(string(requestedMode.labelRes)).assertIsNotSelected()
            composeRule.runOnIdle { selectedMode.value = requestedMode }
            ThemeMode.entries.forEach { mode ->
                val option = composeRule.onNodeWithText(string(mode.labelRes))
                if (mode == requestedMode) option.assertIsSelected()
                else option.assertIsNotSelected()
            }
        }
        composeRule.runOnIdle {
            assertEquals(listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System), requests)
        }

        // The existing theme page forwards every selection, including the selected row.
        composeRule.onNodeWithText(string(ThemeMode.System.labelRes)).performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System, ThemeMode.System),
                requests,
            )
        }
    }

    @Test
    fun hiddenPageHasNoInputTargetsAndRestoresTheLatestTheme() {
        val active = mutableStateOf(true)
        val selectedMode = mutableStateOf(ThemeMode.System)
        val requests = mutableListOf<ThemeMode>()
        var closeCount = 0
        composeRule.setContent {
            ThemeSettingsPage(
                active = active.value,
                themeMode = selectedMode.value,
                onClose = { closeCount += 1 },
                onThemeModeChange = { requests += it },
            )
        }
        composeRule.onNodeWithText(string(ThemeMode.System.labelRes)).assertIsSelected()

        composeRule.runOnIdle { active.value = false }
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
        composeRule.onAllNodes(radioRole).assertCountEquals(0)
        composeRule.runOnIdle {
            assertTrue(requests.isEmpty())
            assertEquals(0, closeCount)
            selectedMode.value = ThemeMode.Dark
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { active.value = true }

        composeRule.onNodeWithText(string(ThemeMode.Dark.labelRes)).assertIsSelected()
        composeRule.onNodeWithText(string(ThemeMode.System.labelRes)).assertIsNotSelected()
        composeRule.onNodeWithText(string(ThemeMode.Light.labelRes)).assertIsNotSelected()
        composeRule.runOnIdle { assertTrue(requests.isEmpty()) }
    }

    @Test
    fun backActionUsesTheLatestCallback() {
        var initialCloseCount = 0
        var replacementCloseCount = 0
        val onClose = mutableStateOf<() -> Unit>({ initialCloseCount += 1 })
        composeRule.setContent {
            ThemeSettingsPage(
                active = true,
                themeMode = ThemeMode.System,
                onClose = onClose.value,
                onThemeModeChange = {},
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

    private val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun string(resourceId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
    }
}
