package com.smartisan.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueOperationsTest {

    @Test
    fun playbackQueueStartIndexReturnsNullForEmptyQueue() {
        assertNull(playbackQueueStartIndex(itemCount = 0, startIndex = 0))
    }

    @Test
    fun playbackQueueStartIndexKeepsValidStartIndex() {
        assertEquals(2, playbackQueueStartIndex(itemCount = 5, startIndex = 2))
    }

    @Test
    fun playbackQueueStartIndexCoercesNegativeStartIndex() {
        assertEquals(0, playbackQueueStartIndex(itemCount = 5, startIndex = -1))
    }

    @Test
    fun playbackQueueStartIndexCoercesOverflowStartIndex() {
        assertEquals(4, playbackQueueStartIndex(itemCount = 5, startIndex = 10))
    }
}
