package com.smartisan.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartisanListDragTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: SmartisanListDragState
    private val version = mutableIntStateOf(0)
    private val commits = mutableListOf<Pair<Int, Int>>()

    private fun setContent() {
        compose.setContent {
            state = rememberSmartisanListDragState(version.intValue)
            Column {
                repeat(4) { index ->
                    val layer = rememberSmartisanDragLayer(state, index)
                    Box(
                        Modifier.width(200.dp)
                            .height(60.dp)
                            .smartisanDragRecording(layer)
                            .background(Color.Gray)
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun release(released: Boolean = true) {
        assertTrue(state.start(1, 60, 60, 90f, 4))
        state.move(220f, 300, 4, 6, 8f) { 3 }
        state.settle(released, 4, rowTop = { it * 60 }, isCurrent = { true }) { from, to ->
            commits += from to to
        }
    }

    @Test
    fun commitWaitsForSettleAndCannotBeSubmittedTwice() {
        setContent()
        compose.runOnIdle {
            release()
            state.settle(true, 4, rowTop = { it * 60 }, isCurrent = { true }) { from, to ->
                commits += from to to
            }
            assertTrue(state.settling)
            assertTrue(commits.isEmpty())
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertTrue(commits.isEmpty()) }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertEquals(listOf(1 to 3), commits)
            assertNull(state.drag)
            assertFalse(state.settling)
        }
    }

    @Test
    fun cancellationAndPageResetNeverCommit() {
        setContent()
        compose.runOnIdle { release(released = false) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertTrue(commits.isEmpty())
            assertNull(state.drag)
            release()
            state.reset()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertTrue(commits.isEmpty())
            assertNull(state.drag)
            assertFalse(state.settling)
        }
    }

    @Test
    fun replacementContentDisposesPendingCommit() {
        setContent()
        compose.runOnIdle { release() }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { version.intValue++ }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertTrue(commits.isEmpty())
            assertNull(state.drag)
        }
    }
}
