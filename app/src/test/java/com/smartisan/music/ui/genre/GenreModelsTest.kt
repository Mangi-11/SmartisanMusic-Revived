package com.smartisan.music.ui.genre

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class GenreModelsTest {

    @Test
    fun buildGenreSummariesGroupsSameGenreAndPreservesSongOrder() {
        val summaries = buildGenreSummaries(
            mediaItems = listOf(
                mediaItem(id = "first", title = "First"),
                mediaItem(id = "second", title = "Second"),
                mediaItem(id = "third", title = "Third"),
            ),
            genreMap = mapOf(
                "first" to "Blues",
                "second" to "Blues",
                "third" to "Jazz",
            ),
            unknownGenreTitle = "未知风格",
        )

        assertEquals(listOf("Blues", "Jazz"), summaries.map { it.name })
        assertEquals(listOf("first", "second"), summaries.first().songs.map { it.mediaId })
        assertEquals(2, summaries.first().trackCount)
    }

    @Test
    fun buildGenreSummariesPlacesUnknownGenreLast() {
        val summaries = buildGenreSummaries(
            mediaItems = listOf(
                mediaItem(id = "unknown", title = "Unknown"),
                mediaItem(id = "blues", title = "Blues"),
            ),
            genreMap = mapOf(
                "unknown" to null,
                "blues" to "Blues",
            ),
            unknownGenreTitle = "未知风格",
        )

        assertEquals(listOf("Blues", "未知风格"), summaries.map { it.name })
    }

    @Test
    fun buildGenreSummariesSortsChineseNamesByLocalizedOrder() {
        val summaries = buildGenreSummaries(
            mediaItems = listOf(
                mediaItem(id = "rock", title = "Rock"),
                mediaItem(id = "folk", title = "Folk"),
                mediaItem(id = "jazz", title = "Jazz"),
            ),
            genreMap = mapOf(
                "rock" to "摇滚",
                "folk" to "民谣",
                "jazz" to "爵士",
            ),
            unknownGenreTitle = "未知风格",
        )

        assertEquals(listOf("爵士", "民谣", "摇滚"), summaries.map { it.name })
    }

    @Test
    fun shouldResetGenreDetailSelectionReturnsTrueWhenPermissionChanges() {
        val shouldReset = shouldResetGenreDetailSelection(
            selectedGenreId = "genre:blues",
            hasPermission = false,
            hasSongs = true,
            hasGenres = true,
        )

        assertEquals(true, shouldReset)
    }

    @Test
    fun shouldResetGenreDetailSelectionReturnsTrueWhenSongsDisappear() {
        val shouldReset = shouldResetGenreDetailSelection(
            selectedGenreId = "genre:blues",
            hasPermission = true,
            hasSongs = false,
            hasGenres = true,
        )

        assertEquals(true, shouldReset)
    }

    @Test
    fun shouldResetGenreDetailSelectionReturnsTrueWhenGenreListBecomesEmpty() {
        val shouldReset = shouldResetGenreDetailSelection(
            selectedGenreId = "genre:blues",
            hasPermission = true,
            hasSongs = true,
            hasGenres = false,
        )

        assertEquals(true, shouldReset)
    }

    @Test
    fun isGenreSelectionAvailableReturnsFalseWhenSelectedGenreMissing() {
        val genres = buildGenreSummaries(
            mediaItems = listOf(
                mediaItem(id = "first", title = "First"),
            ),
            genreMap = mapOf(
                "first" to "Jazz",
            ),
            unknownGenreTitle = "未知风格",
        )

        val available = isGenreSelectionAvailable(
            selectedGenreId = "genre:blues",
            genres = genres,
        )

        assertEquals(false, available)
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
                    .build(),
            )
            .build()
    }
}
