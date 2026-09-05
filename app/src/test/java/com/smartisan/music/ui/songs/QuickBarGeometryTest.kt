package com.smartisan.music.ui.songs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickBarGeometryTest {
    @Test
    fun compressedStripStillSelectsEveryLetterAcrossItsFullHeight() {
        assertEquals(
            listOf(0, 4, 8, 12, 16, 20, 24, 26),
            QuickBarGeometry.visibleLetters(206, 3, 12),
        )
        assertEquals((0..26).toList(), QuickBarGeometry.visibleLetters(400, 3, 12))
        (0..26).forEach { index ->
            assertEquals(index, QuickBarGeometry.letterAt((index + .5f) * 206 / 27, 206))
        }
        assertEquals(0, QuickBarGeometry.letterAt(-50f, 206))
        assertEquals(26, QuickBarGeometry.letterAt(300f, 206))
        assertEquals(listOf(0, 26), QuickBarGeometry.visibleLetters(30, 3, 12))
    }

    @Test
    fun draggingUsesDirectionThresholdAndLastMovementAtRelease() {
        assertFalse(QuickBarGeometry.startsDrag(20f, 0f, 4, hidden = true))
        assertFalse(QuickBarGeometry.startsDrag(-3f, 0f, 4, hidden = true))
        assertFalse(QuickBarGeometry.startsDrag(-20f, 50f, 4, hidden = true))
        assertTrue(QuickBarGeometry.startsDrag(-20f, 40f, 4, hidden = true))
        assertTrue(QuickBarGeometry.startsDrag(20f, 0f, 4, hidden = false))
        assertFalse(QuickBarGeometry.expandsOnRelease(40f, 110f, -1f))
        assertTrue(QuickBarGeometry.expandsOnRelease(40f, 111f, -1f))
        assertTrue(QuickBarGeometry.expandsOnRelease(40f, 111f, 0f))
        assertFalse(QuickBarGeometry.expandsOnRelease(40f, 200f, 1f))
    }

    @Test
    fun gridAssignsPixelRemainderToLastRowWithoutLosingHeight() {
        val heights = (0 until 27).map { QuickBarGeometry.cellHeight(it, 1001, 3, 3) }
        assertEquals(List(24) { 108 } + List(3) { 113 }, heights)
        assertEquals(1001, (0 until 9).sumOf { heights[it * 3] } + 8 * 3)
        assertTrue((0 until 27).all { QuickBarGeometry.cellHeight(it, 1, 3, 3) == 1 })
    }
}
