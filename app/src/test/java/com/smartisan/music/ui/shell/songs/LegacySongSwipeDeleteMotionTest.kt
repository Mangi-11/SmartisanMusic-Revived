package com.smartisan.music.ui.shell.songs

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySongSwipeDeleteMotionTest {
    @Test
    fun `movement stays pending inside touch slop`() {
        assertEquals(
            LegacySongSwipeDeleteMotion.Gesture.PENDING,
            resolve(deltaX = 7f, deltaY = 5f),
        )
    }

    @Test
    fun `only right dominant movement claims swipe delete`() {
        assertEquals(
            LegacySongSwipeDeleteMotion.Gesture.SWIPE_DELETE,
            resolve(deltaX = 20f, deltaY = 5f),
        )
        assertEquals(
            LegacySongSwipeDeleteMotion.Gesture.LIST_SCROLL,
            resolve(deltaX = 5f, deltaY = 20f),
        )
        assertEquals(
            LegacySongSwipeDeleteMotion.Gesture.LIST_SCROLL,
            resolve(deltaX = -20f, deltaY = 5f),
        )
    }

    @Test
    fun `first resolved gesture owns the rest of the touch stream`() {
        assertEquals(
            LegacySongSwipeDeleteMotion.Gesture.LIST_SCROLL,
            LegacySongSwipeDeleteMotion.resolve(
                current = LegacySongSwipeDeleteMotion.Gesture.LIST_SCROLL,
                deltaX = 40f,
                deltaY = 2f,
                touchSlop = TouchSlop,
            ),
        )
        assertEquals(
            LegacySongSwipeDeleteMotion.Gesture.SWIPE_DELETE,
            LegacySongSwipeDeleteMotion.resolve(
                current = LegacySongSwipeDeleteMotion.Gesture.SWIPE_DELETE,
                deltaX = 2f,
                deltaY = 40f,
                touchSlop = TouchSlop,
            ),
        )
    }

    private fun resolve(deltaX: Float, deltaY: Float): LegacySongSwipeDeleteMotion.Gesture =
        LegacySongSwipeDeleteMotion.resolve(
            current = LegacySongSwipeDeleteMotion.Gesture.PENDING,
            deltaX = deltaX,
            deltaY = deltaY,
            touchSlop = TouchSlop,
        )

    private companion object {
        const val TouchSlop = 8f
    }
}
