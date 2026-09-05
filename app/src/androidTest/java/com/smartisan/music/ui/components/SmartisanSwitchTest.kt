package com.smartisan.music.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartisanSwitchTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: SmartisanSwitchState
    private val changes = mutableListOf<Boolean>()
    private var checked by mutableStateOf(false)
    private var shown by mutableStateOf(true)
    private var haptics = 0

    private fun setContent(acceptChange: Boolean = true) {
        compose.setContent {
            if (shown) {
                val scope = rememberCoroutineScope()
                state = remember {
                    SmartisanSwitchState(
                        false,
                        scope,
                        { checked },
                        { true },
                        {
                            changes += it
                            if (acceptChange) checked = it
                        },
                        { haptics++ },
                    )
                }
                DisposableEffect(state) { onDispose { state.dispose() } }
                Row(
                    Modifier.width(200.dp)
                        .testTag("row")
                        .toggleable(
                            checked,
                            remember { MutableInteractionSource() },
                            null,
                            role = Role.Switch,
                            onValueChange = { state.toggle() },
                        )
                ) {
                    SmartisanSwitch(checked, {}, Modifier.testTag("switch"), state = state)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun directTouchAndRowClickEachDispatchOneDelayedChangeAndOneHaptic() {
        setContent()
        compose.onNodeWithTag("switch", useUnmergedTree = true).performTouchInput {
            down(center)
            up()
        }
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        compose.mainClock.advanceTimeBy(16)
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertEquals(listOf(true), changes)
            assertEquals(1, haptics)
            assertEquals(0f, state.shadowAlpha, 0f)
        }
        compose.onNodeWithTag("row").performClick()
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertEquals(listOf(true, false), changes)
            assertEquals(2, haptics)
        }
    }

    @Test
    fun cancelledDragRetainsReleaseSemanticsAndNoOpDragDoesNotVibrate() {
        setContent()
        compose.onNodeWithTag("switch", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(center.copy(x = width.toFloat()))
            cancel()
        }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertEquals(listOf(true), changes)
            assertEquals(1, haptics)
            assertTrue(state.begin())
            state.dragTo(.8f)
            state.finish(true)
        }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertEquals(listOf(true), changes)
            assertEquals(1, haptics)
        }
    }

    @Test
    fun ownerCanRejectAChangeAndDisposalCancelsPendingCallbacks() {
        setContent(acceptChange = false)
        compose.runOnIdle { state.toggle() }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertEquals(listOf(true), changes)
            assertFalse(checked)
            assertEquals(0f, state.position, 0f)
            state.toggle()
            shown = false
        }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            assertEquals(listOf(true), changes)
            assertEquals(1, haptics)
        }
    }
}
