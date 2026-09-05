package com.smartisan.music.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartisanSwitchGeometryTest {
    @Test
    fun diagonalMicroMoveStillTogglesAndEachAxisKeepsTheDragBoundary() {
        assertTrue(smartisanSwitchTarget(false, .1f, Offset(8f, 8f), 100, 10f))
        assertFalse(smartisanSwitchTarget(false, .1f, Offset(10f, 0f), 100, 10f))
        assertFalse(smartisanSwitchTarget(false, .1f, Offset(0f, -10f), 100, 10f))
    }

    @Test
    fun longHoldChoosesThePhysicalHalfwayPointWithoutToggling() {
        assertFalse(smartisanSwitchTarget(false, .5f, Offset.Zero, 300, 10f))
        assertTrue(smartisanSwitchTarget(false, .501f, Offset.Zero, 300, 10f))
        assertFalse(smartisanSwitchTarget(true, .2f, Offset.Zero, 500, 10f))
    }

    @Test
    fun SettleDurationScalesWithTheRemainingDistance() {
        assertEquals(84, smartisanSwitchSettleMillis(0f, 1f))
        assertEquals(42, smartisanSwitchSettleMillis(.5f, 1f))
        assertEquals(21, smartisanSwitchSettleMillis(.25f, 0f))
        assertEquals(0, smartisanSwitchSettleMillis(1f, 1f))
    }
}
