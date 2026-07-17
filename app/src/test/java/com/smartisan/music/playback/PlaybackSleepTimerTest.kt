package com.smartisan.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSleepTimerTest {

    @Test
    fun state_remainingMsUsesElapsedRealtimeDeadline() {
        val startedAt = 10_000L
        val state = createPlaybackSleepTimerState(
            durationMs = 60_000L,
            startedAtElapsedRealtimeMs = startedAt,
            endsAtElapsedRealtimeMs = startedAt + 60_000L,
            nowElapsedRealtimeMs = startedAt + 45_500L,
        )

        assertEquals(14_500L, state.remainingMs)
        assertTrue(state.isActive)
    }

    @Test
    fun state_expiredTimerIsInactiveAndClampedToZero() {
        val startedAt = 10_000L
        val state = createPlaybackSleepTimerState(
            durationMs = 15_000L,
            startedAtElapsedRealtimeMs = startedAt,
            endsAtElapsedRealtimeMs = startedAt + 15_000L,
            nowElapsedRealtimeMs = startedAt + 20_000L,
        )

        assertEquals(0L, state.remainingMs)
        assertFalse(state.isActive)
    }
}
