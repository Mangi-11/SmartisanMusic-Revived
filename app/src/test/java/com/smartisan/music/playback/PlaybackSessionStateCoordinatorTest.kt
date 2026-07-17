package com.smartisan.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionStateCoordinatorTest {

    @Test
    fun restorablePlaybackItemRequiresPlaybackUri() {
        assertTrue(isRestorablePlaybackItemState(hasPlaybackUri = true))
        assertFalse(isRestorablePlaybackItemState(hasPlaybackUri = false))
    }

    @Test
    fun queueItemsStoreRoundTripsStableKey() {
        val items = listOf(
            PlaybackQueueSnapshotItem(
                mediaId = "42",
                stableKey = "primary:Music/song.flac",
            ),
        )

        val decodedItems = items.encodeQueueItemsForStore().decodeQueueItemsFromStore()

        assertEquals(items, decodedItems)
    }

    @Test
    fun queueItemsStoreStillDecodesLegacyFormat() {
        val decodedItems = "42\tprimary:Music/song.flac".decodeQueueItemsFromStore()

        assertEquals(
            listOf(
                PlaybackQueueSnapshotItem(
                    mediaId = "42",
                    stableKey = "primary:Music/song.flac",
                ),
            ),
            decodedItems,
        )
    }

    @Test
    fun duplicateQueueOccurrencesAreNotCollapsed() {
        fun item(title: String): MediaItem = MediaItem.Builder()
            .setMediaId("same")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        val first = item("第一次出现")
        val second = item("第二次出现")
        val snapshots = listOf(
            PlaybackQueueSnapshotItem(mediaId = "same"),
            PlaybackQueueSnapshotItem(mediaId = "same"),
        )

        val restored = restoreQueueItemOccurrences(snapshots, listOf(first, second))

        assertEquals(listOf("第一次出现", "第二次出现"), restored.map { it.mediaMetadata.title })
    }

    @Test
    fun savedIndexWinsForDuplicateCurrentMediaId() {
        val first = MediaItem.Builder().setMediaId("same").build()
        val second = MediaItem.Builder().setMediaId("same").build()
        val snapshot = PlaybackSessionSnapshot(
            mediaIds = listOf("same", "same"),
            currentMediaId = "same",
            currentIndex = 1,
        )

        assertEquals(1, restoredQueueIndex(snapshot, listOf(first, second)))
    }

    @Test
    fun currentOccurrenceSurvivesIndexShiftWhenAnEarlierItemCannotBeRestored() {
        val live = MediaItem.Builder().setMediaId("live").build()
        val studio = MediaItem.Builder().setMediaId("studio").build()
        val snapshot = PlaybackSessionSnapshot(
            mediaIds = listOf("missing", "live", "studio"),
            queueItems = listOf(
                PlaybackQueueSnapshotItem(mediaId = "missing"),
                PlaybackQueueSnapshotItem(mediaId = "live"),
                PlaybackQueueSnapshotItem(mediaId = "studio"),
            ),
            currentMediaId = "live",
            currentIndex = 1,
        )

        assertEquals(0, restoredQueueIndex(snapshot, listOf(live, studio)))
    }
}
