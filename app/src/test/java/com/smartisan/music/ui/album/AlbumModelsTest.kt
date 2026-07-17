package com.smartisan.music.ui.album

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisan.music.data.settings.ArtistSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumModelsTest {

    @Test
    fun buildAlbumSummariesGroupsAlbumsAndSortsTracks() {
        val summaries = buildAlbumSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "beta-1",
                    title = "Intro",
                    album = "Beta",
                    artist = "Beta Artist",
                    trackNumber = 1,
                ),
                mediaItem(
                    id = "alpha-2",
                    title = "Second",
                    album = "Alpha",
                    artist = "Artist B",
                    trackNumber = 2,
                ),
                mediaItem(
                    id = "alpha-1",
                    title = "First",
                    album = "Alpha",
                    artist = "Artist A",
                    trackNumber = 1,
                ),
            ),
            unknownAlbumTitle = "未知专辑",
            multipleArtistsTitle = "多位艺术家",
        )

        assertEquals(listOf("Alpha", "Beta"), summaries.map { it.title })
        assertEquals(listOf("alpha-1", "alpha-2"), summaries.first().songs.map { it.mediaId })
        assertEquals("多位艺术家", summaries.first().artist)
        assertEquals(2, summaries.first().trackCount)
    }

    @Test
    fun buildAlbumSummariesUsesAlbumArtistAsSeparateGroupKey() {
        val summaries = buildAlbumSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "same-title-a",
                    title = "Song A",
                    album = "Greatest Hits",
                    artist = "Singer A",
                    albumArtist = "Singer A",
                ),
                mediaItem(
                    id = "same-title-b",
                    title = "Song B",
                    album = "Greatest Hits",
                    artist = "Singer B",
                    albumArtist = "Singer B",
                ),
            ),
            unknownAlbumTitle = "未知专辑",
            multipleArtistsTitle = "多位艺术家",
        )

        assertEquals(2, summaries.size)
        assertEquals(listOf("Singer A", "Singer B"), summaries.map { it.artist })
    }

    @Test
    fun buildAlbumSummariesUsesSplitArtistForFallbackDisplay() {
        val summaries = buildAlbumSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "duet",
                    title = "Track",
                    album = "Single",
                    artist = "Singer A/Singer B",
                ),
            ),
            unknownAlbumTitle = "未知专辑",
            multipleArtistsTitle = "多位艺术家",
            artistSettings = ArtistSettings(
                separators = setOf("/"),
            ),
        )

        assertEquals("多位艺术家", summaries.single().artist)
    }

    @Test
    fun displayTrackNumberStripsDiscPrefix() {
        val item = mediaItem(
            id = "disc-track",
            title = "Track",
            album = "Album",
            artist = "Artist",
            trackNumber = 2007,
        )

        assertEquals("7", item.displayTrackNumber())
    }

    @Test
    fun displayTrackNumberUsesSymbolWhenMetadataIsMissing() {
        val item = mediaItem(
            id = "missing-track",
            title = "Track",
            album = "Album",
            artist = "Artist",
        )

        assertEquals(MissingAlbumTrackNumberSymbol, item.displayTrackNumber())
    }

    private fun mediaItem(
        id: String,
        title: String,
        album: String,
        artist: String,
        albumArtist: String? = null,
        trackNumber: Int? = null,
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setAlbumTitle(album)
            .setArtist(artist)

        if (albumArtist != null) {
            metadataBuilder.setAlbumArtist(albumArtist)
        }
        if (trackNumber != null) {
            metadataBuilder.setTrackNumber(trackNumber)
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}
