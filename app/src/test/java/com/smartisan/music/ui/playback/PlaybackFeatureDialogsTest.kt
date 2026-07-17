package com.smartisan.music.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFeatureDialogsTest {

    @Test
    fun formatSleepTimerRemaining_formatsMinutesAndSeconds() {
        assertEquals("15:00", formatSleepTimerRemaining(15L * 60_000L))
        assertEquals("00:01", formatSleepTimerRemaining(1L))
    }

    @Test
    fun formatSleepTimerRemaining_formatsHoursWhenNeeded() {
        assertEquals("1:30:00", formatSleepTimerRemaining(90L * 60_000L))
        assertEquals("2:00:00", formatSleepTimerRemaining(120L * 60_000L))
    }
}
