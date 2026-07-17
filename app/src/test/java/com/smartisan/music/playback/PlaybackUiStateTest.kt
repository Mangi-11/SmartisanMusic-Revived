package com.smartisan.music.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUiStateTest {

    @Test
    fun playbackActiveForUiTreatsBufferingWithPlayIntentAsActive() {
        assertTrue(
            playbackActiveForUiState(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
    }

    @Test
    fun playbackActiveForUiKeepsPausedBufferingInactive() {
        assertFalse(
            playbackActiveForUiState(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
    }

}
