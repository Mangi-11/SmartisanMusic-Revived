package com.smartisan.music.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartisanListDragGeometryTest {
    @Test
    fun previewMovesOnlyRowsBetweenSourceAndTarget() {
        assertEquals(0f, smartisanDragRowTranslation(2, 2, 5, 60f), 0f)
        assertEquals(-60f, smartisanDragRowTranslation(3, 2, 5, 60f), 0f)
        assertEquals(-60f, smartisanDragRowTranslation(5, 2, 5, 60f), 0f)
        assertEquals(0f, smartisanDragRowTranslation(6, 2, 5, 60f), 0f)
        assertEquals(60f, smartisanDragRowTranslation(2, 5, 2, 60f), 0f)
        assertEquals(0f, smartisanDragRowTranslation(3, null, null, 60f), 0f)
    }

    @Test
    fun targetSkipsQueueHeadersAndNeverCrossesTheAllowedSection() {
        val visible =
            listOf(
                SmartisanDragRowBounds(1, 0, 60),
                SmartisanDragRowBounds(2, 60, 30),
                SmartisanDragRowBounds(3, 90, 60),
                SmartisanDragRowBounds(4, 151, 60),
            )
        assertEquals(3, smartisanDragTargetAt(20f, visible, 3..4, 260))
        assertEquals(3, smartisanDragTargetAt(149f, visible, 3..4, 260))
        assertEquals(3, smartisanDragTargetAt(150f, visible, 3..4, 260))
        assertEquals(4, smartisanDragTargetAt(151f, visible, 3..4, 260))
        assertEquals(4, smartisanDragTargetAt(300f, visible, 3..4, 260))
        assertNull(smartisanDragTargetAt(120f, visible, IntRange.EMPTY, 260))
        assertNull(smartisanDragTargetAt(120f, visible, -1..-1, 260))
    }

    @Test
    fun playlistEdgeTargetsRetainAbsoluteFirstAndLastIndices() {
        val visible = listOf(SmartisanDragRowBounds(2, 0, 60), SmartisanDragRowBounds(3, 61, 60))
        assertEquals(0, smartisanDragTargetAt(-1f, visible, 0..7, 121))
        assertEquals(7, smartisanDragTargetAt(122f, visible, 0..7, 121))
        assertEquals(3, smartisanDragTargetAt(95f, visible, 0..7, 121))
    }

    @Test
    fun floatingRowPreservesPointerOffsetAndBothShadowBounds() {
        assertEquals(76f, smartisanDragFloatingY(100.9f, 24, 300, 60, 4, 6), 0f)
        assertEquals(-4f, smartisanDragFloatingY(-100f, 24, 300, 60, 4, 6), 0f)
        assertEquals(230f, smartisanDragFloatingY(500f, 24, 300, 60, 4, 6), 0f)
        assertEquals(-4f, smartisanDragFloatingY(500f, 24, 40, 60, 4, 6), 0f)
    }

    @Test
    fun handleHitUsesPhysicalRightMarginAndSlopAroundAllFourSides() {
        fun hit(x: Float, y: Float) =
            smartisanDragHandleHit(Offset(x, y), 360, 100, 60, Size(30f, 24f), 4, 8f)
        assertTrue(hit(318f, 110f))
        assertTrue(hit(364f, 150f))
        assertFalse(hit(317f, 130f))
        assertFalse(hit(340f, 109f))
        assertFalse(hit(30f, 130f))
    }
}
