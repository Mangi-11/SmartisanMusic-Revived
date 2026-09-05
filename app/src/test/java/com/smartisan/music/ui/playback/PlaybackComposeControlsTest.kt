package com.smartisan.music.ui.playback

import com.smartisan.music.ui.components.smartisanRatingAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackComposeControlsTest {
    @Test
    fun volumeKeepsSeparateThumbDrawingAndHitGeometry() {
        // The original thumb's 5dp offset differs from its half-width; preserve that asymmetry.
        assertEquals(21.5f, playbackVolumeThumbLeft(26.5f, 247f, 31f, 5f, 0f), 0f)
        assertEquals(247.5f, playbackVolumeThumbLeft(26.5f, 247f, 31f, 5f, 1f), 0f)
        assertTrue(playbackVolumeThumbHit(169.5f, 26.5f, 247f, .5f, 31f, 4f))
        assertFalse(playbackVolumeThumbHit(170f, 26.5f, 247f, .5f, 31f, 4f))
        assertFalse(playbackVolumeThumbHit(40f, 26.5f, 247f, .5f, 31f, 4f))
    }

    @Test
    fun volumePreservesGrabOffsetAndIntegerProgress() {
        assertEquals(50, playbackVolumeTouchProgress(154f, 27f, 246f, -4f / 246f))
        assertEquals(0, playbackVolumeTouchProgress(-1f, 27f, 246f, .1f))
        assertEquals(100, playbackVolumeTouchProgress(301f, 27f, 246f, -.1f))
    }

    @Test
    fun ratingUsesPlatformStarBoundaryOffset() {
        assertEquals(0, smartisanRatingAt(-2f, 155f))
        assertEquals(1, smartisanRatingAt(0f, 155f))
        assertEquals(2, smartisanRatingAt(31f, 155f))
        assertEquals(3, smartisanRatingAt(77.5f, 155f))
        assertEquals(5, smartisanRatingAt(155f, 155f))
        assertEquals(0, smartisanRatingAt(10f, 0f))
    }

    @Test
    fun sleepTimerSelectionAccountsForActiveCountdownRow() {
        val durations = listOf(0L, 15L, 30L, 60L, 90L, 120L).map { it * 60_000L }
        durations.forEachIndexed { index, duration ->
            assertEquals(duration, mapSleepTimerDuration(active = false, value = index + 1))
            assertEquals(duration, mapSleepTimerDuration(active = true, value = index + 2))
        }
        assertEquals(0L, mapSleepTimerDuration(active = true, value = 1))
    }
}
