package com.smartisan.music.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackScreenUtilsTest {

    @Test
    fun `format playback time matches the legacy player labels`() {
        assertEquals("0:00", formatPlaybackTime(-1L))
        assertEquals("1:05", formatPlaybackTime(65_000L))
        assertEquals("1:01:05", formatPlaybackTime(3_665_000L))
    }

    @Test
    fun `fraction from position clamps to valid range`() {
        assertEquals(0f, fractionFromPosition(positionX = -12f, trackWidthPx = 120), 0.0001f)
        assertEquals(0.5f, fractionFromPosition(positionX = 60f, trackWidthPx = 120), 0.0001f)
        assertEquals(1f, fractionFromPosition(positionX = 132f, trackWidthPx = 120), 0.0001f)
    }
}
