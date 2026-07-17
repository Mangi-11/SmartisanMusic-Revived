package com.smartisan.music.ui.artist

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisan.music.data.settings.ArtistSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistModelsTest {

    @Test
    fun buildArtistSummariesGroupsArtistsAndCountsAlbums() {
        val summaries = buildArtistSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "pu-2",
                    title = "Second",
                    artist = "朴树",
                    album = "二号专辑",
                    trackNumber = 2,
                ),
                mediaItem(
                    id = "pu-1",
                    title = "First",
                    artist = "朴树",
                    album = "一号专辑",
                    trackNumber = 1,
                ),
                mediaItem(
                    id = "zhao-1",
                    title = "Solo",
                    artist = "赵雷",
                    album = "民谣",
                    trackNumber = 1,
                ),
            ),
            unknownArtistTitle = "未知艺术家",
            unknownAlbumTitle = "未知专辑",
        )

        assertEquals(listOf("朴树", "赵雷"), summaries.map { it.name })
        assertEquals(2, summaries.first().albumCount)
        assertEquals(2, summaries.first().trackCount)
        assertEquals(listOf("pu-1", "pu-2"), summaries.first().songs.map { it.mediaId })
    }

    @Test
    fun buildArtistSummariesSortsChineseArtistsByPinyin() {
        val summaries = buildArtistSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "pu",
                    title = "Track",
                    artist = "朴树",
                    album = "一号专辑",
                ),
                mediaItem(
                    id = "zhao",
                    title = "Track",
                    artist = "赵雷",
                    album = "民谣",
                ),
                mediaItem(
                    id = "bao",
                    title = "Track",
                    artist = "鲍家街43号",
                    album = "鲍家街43号",
                ),
            ),
            unknownArtistTitle = "未知艺术家",
            unknownAlbumTitle = "未知专辑",
        )

        assertEquals(listOf("鲍家街43号", "朴树", "赵雷"), summaries.map { it.name })
    }

    @Test
    fun buildArtistSummariesUsesUnknownArtistFallback() {
        val summaries = buildArtistSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "unknown",
                    title = "Track",
                    artist = "",
                    album = "",
                ),
            ),
            unknownArtistTitle = "未知艺术家",
            unknownAlbumTitle = "未知专辑",
        )

        assertEquals("未知艺术家", summaries.single().name)
        assertEquals(1, summaries.single().albumCount)
    }

    @Test
    fun buildArtistSummariesKeepsCompoundArtistByDefault() {
        val summaries = buildArtistSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "duet",
                    title = "Track",
                    artist = "Singer A/Singer B",
                    album = "Single",
                ),
            ),
            unknownArtistTitle = "未知艺术家",
            unknownAlbumTitle = "未知专辑",
        )

        assertEquals(listOf("Singer A/Singer B"), summaries.map { it.name })
    }

    @Test
    fun buildArtistSummariesSplitsArtistsByConfiguredSeparators() {
        val summaries = buildArtistSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "duet",
                    title = "Track",
                    artist = "Singer A/Singer B; Singer C、Singer D,Singer E",
                    album = "Single",
                ),
            ),
            unknownArtistTitle = "未知艺术家",
            unknownAlbumTitle = "未知专辑",
            artistSettings = ArtistSettings(
                separators = setOf("/", ";", "、", ","),
            ),
        )

        assertEquals(
            setOf("Singer A", "Singer B", "Singer C", "Singer D", "Singer E"),
            summaries.map { it.name }.toSet(),
        )
        summaries.forEach { summary ->
            assertEquals(listOf("duet"), summary.songs.map { it.mediaId })
        }
    }

    @Test
    fun buildArtistSummariesDropsBlankAndDuplicateSplitArtists() {
        val summaries = buildArtistSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "duplicate",
                    title = "Track",
                    artist = " Singer A / / singer a / Singer B ",
                    album = "Single",
                ),
            ),
            unknownArtistTitle = "未知艺术家",
            unknownAlbumTitle = "未知专辑",
            artistSettings = ArtistSettings(
                separators = setOf("/"),
            ),
        )

        assertEquals(setOf("Singer A", "Singer B"), summaries.map { it.name }.toSet())
        summaries.forEach { summary ->
            assertEquals(listOf("duplicate"), summary.songs.map { it.mediaId })
        }
    }

    @Test
    fun buildArtistSummariesUsesUnknownArtistWhenSplitLeavesNoNames() {
        val summaries = buildArtistSummaries(
            mediaItems = listOf(
                mediaItem(
                    id = "blank",
                    title = "Track",
                    artist = " / / ",
                    album = "Single",
                ),
            ),
            unknownArtistTitle = "未知艺术家",
            unknownAlbumTitle = "未知专辑",
            artistSettings = ArtistSettings(
                separators = setOf("/"),
            ),
        )

        assertEquals(listOf("未知艺术家"), summaries.map { it.name })
    }

    private fun mediaItem(
        id: String,
        title: String,
        artist: String,
        album: String,
        trackNumber: Int? = null,
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)

        if (trackNumber != null) {
            metadataBuilder.setTrackNumber(trackNumber)
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}
