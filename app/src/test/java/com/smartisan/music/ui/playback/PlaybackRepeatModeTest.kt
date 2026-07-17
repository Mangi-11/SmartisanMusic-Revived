package com.smartisan.music.ui.playback

import androidx.media3.common.Player
import com.smartisan.music.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRepeatModeTest {

    @Test
    fun `repeat button follows original three step order`() {
        assertEquals(Player.REPEAT_MODE_ALL, nextPlaybackRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextPlaybackRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextPlaybackRepeatMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `repeat button uses single repeat artwork`() {
        assertEquals(R.drawable.btn_playing_cycle_off, playbackRepeatButtonRes(Player.REPEAT_MODE_OFF))
        assertEquals(R.drawable.btn_playing_cycle_on, playbackRepeatButtonRes(Player.REPEAT_MODE_ALL))
        assertEquals(R.drawable.btn_playing_repeat_on, playbackRepeatButtonRes(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `shuffle button uses original toast text`() {
        assertEquals(R.string.shuffle_on, shuffleToastRes(true))
        assertEquals(R.string.shuffle_off, shuffleToastRes(false))
    }
}
