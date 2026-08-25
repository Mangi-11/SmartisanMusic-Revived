package com.smartisan.music.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class StretchTextViewTest {
    @Test
    fun `short title keeps indicator after text`() {
        assertEquals(
            120,
            trailingPlayIndicatorLeft(
                preferredLeft = 120,
                viewWidth = 300,
                drawableWidth = 24,
            ),
        )
    }

    @Test
    fun `long title keeps indicator in reserved trailing edge`() {
        assertEquals(
            276,
            trailingPlayIndicatorLeft(
                preferredLeft = 360,
                viewWidth = 300,
                drawableWidth = 24,
            ),
        )
    }
}
