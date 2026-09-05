package com.smartisan.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartisanScrollbarTest {
    @Test
    fun viewportFitAndUnknownMeasurementsHaveNoThumb() {
        assertNull(smartisanScrollbarGeometry(300, 4, 0, 300, 300))
        assertNull(smartisanScrollbarGeometry(300, 4, 0, Int.MAX_VALUE, 300))
        assertNull(smartisanScrollbarGeometry(300, 4, Int.MAX_VALUE, 1_000, 300))
        assertNull(smartisanScrollbarGeometry(0, 4, 0, 1_000, 300))
    }

    @Test
    fun thumbKeepsPlatformMinimumLengthAndClampsAtBothEnds() {
        assertEquals(
            SmartisanScrollbarGeometry(0, 8),
            smartisanScrollbarGeometry(300, 4, -100, 100_000, 300),
        )
        assertEquals(
            SmartisanScrollbarGeometry(292, 8),
            smartisanScrollbarGeometry(300, 4, 100_000, 100_000, 300),
        )
    }

    @Test
    fun estimatedScrollRangeMapsToFullAvailableTrack() {
        assertEquals(
            SmartisanScrollbarGeometry(0, 90),
            smartisanScrollbarGeometry(300, 4, 0, 1_000, 300),
        )
        assertEquals(
            SmartisanScrollbarGeometry(105, 90),
            smartisanScrollbarGeometry(300, 4, 350, 1_000, 300),
        )
        assertEquals(
            SmartisanScrollbarGeometry(210, 90),
            smartisanScrollbarGeometry(300, 4, 700, 1_000, 300),
        )
    }
}
