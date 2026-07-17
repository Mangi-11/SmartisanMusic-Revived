package com.smartisan.music.playback

import androidx.media3.common.MediaItem
import com.smartisan.music.ExternalAudioMediaIdPrefix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionMediaItemsTest {

    @Test
    fun externalAudioItemsCanResolveDirectlyWithoutLibraryLookup() {
        val item = MediaItem.Builder()
            .setMediaId("${ExternalAudioMediaIdPrefix}1")
            .build()

        assertTrue(item.canResolveDirectSessionPlaybackItem())
        assertNotNull(listOf(item).resolveDirectSessionPlaybackItemsOrNull())
    }

    @Test
    fun localLibraryItemsStillRequireLibraryLookup() {
        val item = MediaItem.Builder()
            .setMediaId("local-song-id")
            .build()

        assertFalse(item.canResolveDirectSessionPlaybackItem())
        assertNull(listOf(item).resolveDirectSessionPlaybackItemsOrNull())
    }

}
