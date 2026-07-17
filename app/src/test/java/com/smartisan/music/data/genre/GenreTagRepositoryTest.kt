package com.smartisan.music.data.genre

import androidx.media3.common.MediaItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GenreTagRepositoryTest {

    @Before
    fun setUp() {
        resetGenreTagRepositoryCacheForTest()
    }

    @Test
    fun extractPrimaryGenreReturnsNullForBlankValues() {
        assertNull(extractPrimaryGenre(null))
        assertNull(extractPrimaryGenre(""))
        assertNull(extractPrimaryGenre("   "))
    }

    @Test
    fun extractPrimaryGenreReturnsFirstExplicitValue() {
        assertEquals("Blues", extractPrimaryGenre("Blues"))
        assertEquals("Blues", extractPrimaryGenre("Blues;Jazz"))
        assertEquals("Blues", extractPrimaryGenre("Blues；Jazz"))
        assertEquals("Blues", extractPrimaryGenre("Blues,Jazz"))
        assertEquals("Blues", extractPrimaryGenre("Blues，Jazz"))
        assertEquals("Blues", extractPrimaryGenre("Blues|Jazz"))
    }

    @Test
    fun extractPrimaryGenreSkipsEmptySegmentsBeforeReturningValue() {
        assertEquals("Blues", extractPrimaryGenre(" ; Blues ; Jazz"))
        assertEquals("民谣", extractPrimaryGenre(" ， 民谣，摇滚"))
    }

    @Test
    fun loadGenresRetriesMediaStoreAfterQueryFailure() = runBlocking {
        var mediaStoreReadCount = 0
        var embeddedReadCount = 0
        val repository = GenreTagRepository(
            mediaStoreVersionProvider = { "v1" },
            mediaStoreGenreReader = {
                mediaStoreReadCount += 1
                if (mediaStoreReadCount == 1) {
                    error("query failed")
                }
                mapOf("song-1" to "Blues")
            },
            embeddedGenreReader = {
                embeddedReadCount += 1
                null
            },
        )
        val song = mediaItem("song-1")

        val firstResult = repository.loadGenres(listOf(song))
        val secondResult = repository.loadGenres(listOf(song))

        assertEquals(mapOf("song-1" to null), firstResult)
        assertEquals(mapOf("song-1" to "Blues"), secondResult)
        assertEquals(2, mediaStoreReadCount)
        assertEquals(1, embeddedReadCount)
    }

    @Test
    fun loadGenresSkipsMediaStoreAfterSuccessfulPrime() = runBlocking {
        var mediaStoreReadCount = 0
        var embeddedReadCount = 0
        val repository = GenreTagRepository(
            mediaStoreVersionProvider = { "v1" },
            mediaStoreGenreReader = {
                mediaStoreReadCount += 1
                mapOf(
                    "song-1" to "Blues",
                    "song-2" to "Jazz",
                )
            },
            embeddedGenreReader = {
                embeddedReadCount += 1
                null
            },
        )
        val songs = listOf(
            mediaItem("song-1"),
            mediaItem("song-2"),
        )

        val firstResult = repository.loadGenres(songs)
        val secondResult = repository.loadGenres(songs)

        assertEquals(mapOf("song-1" to "Blues", "song-2" to "Jazz"), firstResult)
        assertEquals(firstResult, secondResult)
        assertEquals(1, mediaStoreReadCount)
        assertEquals(0, embeddedReadCount)
    }

    private fun mediaItem(id: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .build()
    }
}
