package com.smartisan.music.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackScreenUtilsTest {

    @Test
    fun `format playback time pads minutes and seconds`() {
        assertEquals("00:00", formatPlaybackTime(-1L))
        assertEquals("01:05", formatPlaybackTime(65_000L))
    }

    @Test
    fun `fraction from position clamps to valid range`() {
        assertEquals(0f, fractionFromPosition(positionX = -12f, trackWidthPx = 120), 0.0001f)
        assertEquals(0.5f, fractionFromPosition(positionX = 60f, trackWidthPx = 120), 0.0001f)
        assertEquals(1f, fractionFromPosition(positionX = 132f, trackWidthPx = 120), 0.0001f)
    }
}
