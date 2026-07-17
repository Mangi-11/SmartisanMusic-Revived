package com.smartisan.music.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackScratchMathTest {

    @Test
    fun `rewind fling lasts longer when playback should resume`() {
        val withoutResume = scratchFlingDurationMs(
            velocityDegreesPerSecond = -320f,
            resumePlaybackAfterDrag = false,
        )
        val withResume = scratchFlingDurationMs(
            velocityDegreesPerSecond = -320f,
            resumePlaybackAfterDrag = true,
        )

        assertTrue(withResume > withoutResume)
    }

    @Test
    fun `rewind resume keyframes include rebound toward playback`() {
        val keyframes = scratchFlingVelocityKeyframes(
            velocityDegreesPerSecond = -300f,
            resumePlaybackAfterDrag = true,
        )

        assertEquals(5, keyframes.size)
        assertEquals(-300f, keyframes.first(), 0.001f)
        assertEquals(60f, keyframes.last(), 0.001f)
    }

    @Test
    fun `velocity interpolation walks between scratch fling keyframes`() {
        val interpolated = scratchFlingVelocityAt(
            keyframes = floatArrayOf(120f, 0f),
            elapsedMs = 50f,
            durationMs = 100L,
        )

        assertEquals(60f, interpolated, 0.001f)
    }
}
