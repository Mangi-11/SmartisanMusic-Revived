package com.smartisan.music.ui.songs

import org.junit.Assert.assertEquals
import org.junit.Test

class SongSwipeDeleteMotionTest {
    @Test
    fun `movement stays pending inside touch slop`() {
        assertEquals(
            SongSwipeDeleteMotion.Gesture.PENDING,
            resolve(deltaX = 7f, deltaY = 5f),
        )
    }

    @Test
    fun `only right dominant movement claims swipe delete`() {
        assertEquals(
            SongSwipeDeleteMotion.Gesture.SWIPE_DELETE,
            resolve(deltaX = 20f, deltaY = 5f),
        )
        assertEquals(
            SongSwipeDeleteMotion.Gesture.LIST_SCROLL,
            resolve(deltaX = 5f, deltaY = 20f),
        )
        assertEquals(
            SongSwipeDeleteMotion.Gesture.LIST_SCROLL,
            resolve(deltaX = -20f, deltaY = 5f),
        )
    }

    @Test
    fun `first resolved gesture owns the rest of the touch stream`() {
        assertEquals(
            SongSwipeDeleteMotion.Gesture.LIST_SCROLL,
            SongSwipeDeleteMotion.resolve(
                current = SongSwipeDeleteMotion.Gesture.LIST_SCROLL,
                deltaX = 40f,
                deltaY = 2f,
                touchSlop = TouchSlop,
            ),
        )
        assertEquals(
            SongSwipeDeleteMotion.Gesture.SWIPE_DELETE,
            SongSwipeDeleteMotion.resolve(
                current = SongSwipeDeleteMotion.Gesture.SWIPE_DELETE,
                deltaX = 2f,
                deltaY = 40f,
                touchSlop = TouchSlop,
            ),
        )
    }

    private fun resolve(deltaX: Float, deltaY: Float): SongSwipeDeleteMotion.Gesture =
        SongSwipeDeleteMotion.resolve(
            current = SongSwipeDeleteMotion.Gesture.PENDING,
            deltaX = deltaX,
            deltaY = deltaY,
            touchSlop = TouchSlop,
        )

    private companion object {
        const val TouchSlop = 8f
    }
}
