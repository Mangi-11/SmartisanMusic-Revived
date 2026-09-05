package com.smartisan.music.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartisanCheckboxHitTest {
    private val checkbox = Rect(18f, 16f, 42f, 44f)

    @Test
    fun expandsBothAxesAndKeepsRightAndBottomEdgesExclusive() {
        assertTrue(smartisanCheckboxHit(Offset(10f, 8f), checkbox, 8f))
        assertTrue(smartisanCheckboxHit(Offset(49.99f, 51.99f), checkbox, 8f))
        assertFalse(smartisanCheckboxHit(Offset(9.99f, 30f), checkbox, 8f))
        assertFalse(smartisanCheckboxHit(Offset(30f, 7.99f), checkbox, 8f))
        assertFalse(smartisanCheckboxHit(Offset(50f, 30f), checkbox, 8f))
        assertFalse(smartisanCheckboxHit(Offset(30f, 52f), checkbox, 8f))
    }

    @Test
    fun ignoresUndisplayedCheckboxesAndFollowsTheirAnimatedPosition() {
        assertFalse(smartisanCheckboxHit(Offset.Zero, null, 8f))
        assertFalse(smartisanCheckboxHit(Offset.Zero, Rect.Zero, 8f))
        val enteringCheckbox = checkbox.translate(Offset(-12f, 0f))
        assertTrue(smartisanCheckboxHit(Offset(0f, 30f), enteringCheckbox, 8f))
        assertFalse(smartisanCheckboxHit(Offset(0f, 30f), checkbox, 8f))
        assertFalse(smartisanCheckboxHit(Offset(22f, 0f), enteringCheckbox, 8f))
    }

    @Test
    fun comparesPointerAndCheckboxInTheSameCoordinateSpace() {
        val listOrigin = Offset(32f, 150f)
        val boundsInRoot = checkbox.translate(listOrigin)
        assertTrue(smartisanCheckboxHit(Offset(20f, 30f) + listOrigin, boundsInRoot, 8f))
        assertFalse(smartisanCheckboxHit(Offset(20f, 30f), boundsInRoot, 8f))
        assertFalse(smartisanCheckboxHit(Offset(12f, 30f), checkbox, 0f))
    }
}
