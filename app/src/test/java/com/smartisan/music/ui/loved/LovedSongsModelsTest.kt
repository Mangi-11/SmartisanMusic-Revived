package com.smartisan.music.ui.loved

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisan.music.data.favorite.FavoriteSongRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LovedSongsModelsTest {

    private val titleComparator = Comparator<String> { left, right ->
        left.compareTo(right)
    }

    @Test
    fun buildLovedSongEntriesKeepsOnlyVisibleSongs() {
        val entries = buildLovedSongEntries(
            favorites = listOf(
                FavoriteSongRecord(mediaId = "visible", likedAt = 30L),
                FavoriteSongRecord(mediaId = "missing", likedAt = 20L),
            ),
            visibleSongs = listOf(
                mediaItem(id = "visible", title = "Visible Song"),
            ),
        )

        assertEquals(listOf("visible"), entries.map { it.mediaItem.mediaId })
        assertEquals(listOf(30L), entries.map { it.likedAt })
    }

    @Test
    fun sortLovedSongEntriesByTimeUsesLatestFirst() {
        val sorted = sortLovedSongEntries(
            entries = listOf(
                lovedSongEntry(id = "older", title = "B", likedAt = 10L),
                lovedSongEntry(id = "newer", title = "A", likedAt = 40L),
                lovedSongEntry(id = "middle", title = "C", likedAt = 20L),
            ),
            sortMode = LovedSongsSortMode.Time,
            titleComparator = titleComparator,
        )

        assertEquals(listOf("newer", "middle", "older"), sorted.map { it.mediaItem.mediaId })
    }

    @Test
    fun sortLovedSongEntriesBySongNameUsesComparatorThenLikedTime() {
        val sorted = sortLovedSongEntries(
            entries = listOf(
                lovedSongEntry(id = "gamma", title = "Gamma", likedAt = 10L),
                lovedSongEntry(id = "alpha-new", title = "Alpha", likedAt = 40L),
                lovedSongEntry(id = "alpha-old", title = "Alpha", likedAt = 20L),
            ),
            sortMode = LovedSongsSortMode.SongName,
            titleComparator = titleComparator,
        )

        assertEquals(
            listOf("alpha-new", "alpha-old", "gamma"),
            sorted.map { it.mediaItem.mediaId },
        )
    }

    @Test
    fun buildLovedSongsPlayRequestReturnsWholeQueueAndClickedIndex() {
        val request = buildLovedSongsPlayRequest(
            entries = listOf(
                lovedSongEntry(id = "first", title = "A", likedAt = 30L),
                lovedSongEntry(id = "second", title = "B", likedAt = 20L),
                lovedSongEntry(id = "third", title = "C", likedAt = 10L),
            ),
            startMediaId = "second",
        )

        assertNotNull(request)
        assertEquals(listOf("first", "second", "third"), request!!.mediaItems.map { it.mediaId })
        assertEquals(1, request.startIndex)
    }

    @Test
    fun buildLovedSongsShuffleRequestKeepsSourceOrder() {
        val sourceEntries = listOf(
            lovedSongEntry(id = "first", title = "A", likedAt = 30L),
            lovedSongEntry(id = "second", title = "B", likedAt = 20L),
            lovedSongEntry(id = "third", title = "C", likedAt = 10L),
        )
        val request = buildLovedSongsShuffleRequest(entries = sourceEntries)
        val expectedOrder = sourceEntries
            .map(LovedSongEntry::mediaItem)
            .map(MediaItem::mediaId)

        assertNotNull(request)
        assertEquals(expectedOrder, request!!.mediaItems.map { it.mediaId })
        assertEquals(0, request.startIndex)
    }

    private fun lovedSongEntry(
        id: String,
        title: String,
        likedAt: Long,
    ): LovedSongEntry {
        return LovedSongEntry(
            mediaItem = mediaItem(id = id, title = title),
            likedAt = likedAt,
        )
    }

    private fun mediaItem(
        id: String,
        title: String,
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .build(),
            )
            .build()
    }
}
