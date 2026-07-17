package com.smartisan.music.ui.search

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisan.music.data.settings.ArtistSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchModelsTest {

    @Test
    fun buildSearchResultsMatchesAcrossSongFieldsAndTrimsQuery() {
        val songs = listOf(
            mediaItem(
                id = "title-match",
                title = "Road Song",
                artist = "Singer A",
                album = "Album A",
            ),
            mediaItem(
                id = "artist-match",
                title = "Track B",
                artist = "Road Band",
                album = "Album B",
            ),
            mediaItem(
                id = "album-match",
                title = "Track C",
                artist = "Singer C",
                album = "Road Stories",
            ),
        )

        val results = buildSearchResults(
            query = "  rOaD  ",
            songs = songs,
            unknownAlbumTitle = "未知专辑",
            unknownArtistTitle = "未知艺术家",
            multipleArtistsTitle = "多位艺术家",
        )

        assertEquals(listOf("title-match", "artist-match", "album-match"), results.songs.map { it.mediaId })
        assertTrue(results.albums.isNotEmpty())
        assertTrue(results.artists.isNotEmpty())
    }

    @Test
    fun buildSearchResultsKeepsAlbumSortOrderFromAlbumModels() {
        val results = buildSearchResults(
            query = "singer",
            songs = listOf(
                mediaItem(
                    id = "beta",
                    title = "Track Beta",
                    artist = "Singer B",
                    album = "Beta Album",
                ),
                mediaItem(
                    id = "alpha",
                    title = "Track Alpha",
                    artist = "Singer A",
                    album = "Alpha Album",
                ),
            ),
            unknownAlbumTitle = "未知专辑",
            unknownArtistTitle = "未知艺术家",
            multipleArtistsTitle = "多位艺术家",
        )

        assertEquals(listOf("Alpha Album", "Beta Album"), results.albums.map { it.title })
    }

    @Test
    fun buildSearchResultsMatchesSplitArtistNames() {
        val results = buildSearchResults(
            query = "singer b",
            songs = listOf(
                mediaItem(
                    id = "duet",
                    title = "Duet",
                    artist = "Singer A/Singer B",
                    album = "Single",
                ),
            ),
            unknownAlbumTitle = "未知专辑",
            unknownArtistTitle = "未知艺术家",
            multipleArtistsTitle = "多位艺术家",
            artistSettings = ArtistSettings(
                separators = setOf("/"),
            ),
        )

        assertEquals(listOf("duet"), results.songs.map { it.mediaId })
        assertEquals(listOf("Single"), results.albums.map { it.title })
        assertEquals(listOf("Singer B"), results.artists.map { it.name })
    }

    @Test
    fun buildSearchResultsKeepsArtistSortOrderFromArtistModels() {
        val results = buildSearchResults(
            query = "乐队",
            songs = listOf(
                mediaItem(
                    id = "pu",
                    title = "Track",
                    artist = "朴乐队",
                    album = "A",
                ),
                mediaItem(
                    id = "zhao",
                    title = "Track",
                    artist = "赵乐队",
                    album = "B",
                ),
                mediaItem(
                    id = "bao",
                    title = "Track",
                    artist = "鲍乐队",
                    album = "C",
                ),
            ),
            unknownAlbumTitle = "未知专辑",
            unknownArtistTitle = "未知艺术家",
            multipleArtistsTitle = "多位艺术家",
        )

        assertEquals(listOf("鲍乐队", "朴乐队", "赵乐队"), results.artists.map { it.name })
    }

    private fun mediaItem(
        id: String,
        title: String,
        artist: String,
        album: String,
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build(),
            )
            .build()
    }
}
