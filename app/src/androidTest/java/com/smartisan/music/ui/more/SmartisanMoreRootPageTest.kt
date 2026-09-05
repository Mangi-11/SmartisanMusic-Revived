package com.smartisan.music.ui.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartisan.music.R
import com.smartisan.music.ui.navigation.MusicDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartisanMoreRootPageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rowSettingsAndSearchDispatchTheirOwnActions() {
        val destinations = listOf(MusicDestination.Genre, MusicDestination.Folder)
        val selected = mutableListOf<MusicDestination>()
        var settingsClicks = 0
        var searchClicks = 0
        compose.setContent {
            SmartisanMoreRootPage(
                active = true,
                destinations = destinations,
                onDestinationSelected = selected::add,
                onSettingsClick = { settingsClicks++ },
                onSearchClick = { searchClicks++ },
            )
        }

        destinations.forEach { destination ->
            compose.onNodeWithText(label(destination)).assertHasClickAction().performClick()
        }
        compose.onNodeWithContentDescription(string(R.string.setting)).performClick()
        compose.onNodeWithContentDescription(string(R.string.tab_local_search)).performClick()

        compose.runOnIdle {
            assertEquals(destinations, selected)
            assertEquals(1, settingsClicks)
            assertEquals(1, searchClicks)
        }
    }

    @Test
    fun destinationChangesRemoveOldRowsAndUseCurrentCallback() {
        var destinations by mutableStateOf(listOf(MusicDestination.Genre, MusicDestination.Folder))
        var callbackVersion by mutableStateOf(1)
        val events = mutableListOf<Pair<Int, MusicDestination>>()
        compose.setContent {
            val currentVersion = callbackVersion
            SmartisanMoreRootPage(
                active = true,
                destinations = destinations,
                onDestinationSelected = { events += currentVersion to it },
                onSettingsClick = {},
                onSearchClick = {},
            )
        }
        compose.onNodeWithText(label(MusicDestination.Genre)).performClick()
        compose.runOnIdle {
            destinations = listOf(MusicDestination.Folder, MusicDestination.Artist)
            callbackVersion = 2
        }

        compose.onNodeWithText(label(MusicDestination.Genre)).assertDoesNotExist()
        compose.onNodeWithText(label(MusicDestination.Artist)).assertIsDisplayed().performClick()
        compose.onNodeWithText(label(MusicDestination.Folder)).performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    1 to MusicDestination.Genre,
                    2 to MusicDestination.Artist,
                    2 to MusicDestination.Folder,
                ),
                events,
            )
        }
    }

    @Test
    fun inactivePageHasNoActionsAndRestoresItsScrollPosition() {
        var active by mutableStateOf(true)
        var clicks = 0
        compose.setContent {
            Box(Modifier.height(240.dp).testTag("more-host")) {
                SmartisanMoreRootPage(
                    active = active,
                    destinations = MusicDestination.movableEntries,
                    onDestinationSelected = { clicks++ },
                    onSettingsClick = { clicks++ },
                    onSearchClick = { clicks++ },
                )
            }
        }

        val folderLabel = label(MusicDestination.Folder)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(folderLabel))
        val rowBounds =
            compose
                .onNodeWithText(folderLabel)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val hostBounds = compose.onNodeWithTag("more-host").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { active = false }

        compose.onNodeWithText(folderLabel).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.setting)).assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.onNodeWithTag("more-host").performTouchInput {
            click(Offset(rowBounds.center.x - hostBounds.left, rowBounds.center.y - hostBounds.top))
        }
        compose.runOnIdle {
            assertEquals(0, clicks)
            active = true
        }
        compose.onNodeWithText(folderLabel).assertIsDisplayed()
    }

    private fun label(destination: MusicDestination) = string(destination.labelRes)

    private fun string(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
}
