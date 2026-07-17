package com.smartisan.music.ui.album

import androidx.media3.common.MediaItem
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.ui.artist.artistNormalizedKey
import com.smartisan.music.ui.artist.toArtistDisplayNames
import com.smartisan.music.ui.artist.toArtistDisplayText
import java.util.Locale

enum class AlbumViewMode {
    List,
    Tile,
}

internal data class AlbumSummary(
    val id: String,
    val title: String,
    val artist: String,
    val songs: List<MediaItem>,
    val year: Int?,
) {
    val representative: MediaItem = songs.first()
    val trackCount: Int = songs.size
    val durationMs: Long = songs.sumOf { it.mediaMetadata.durationMs ?: 0L }
}

internal fun buildAlbumSummaries(
    mediaItems: List<MediaItem>,
    unknownAlbumTitle: String,
    multipleArtistsTitle: String,
    artistSettings: ArtistSettings = ArtistSettings(),
): List<AlbumSummary> {
    val groups = linkedMapOf<String, MutableAlbumGroup>()

    mediaItems.forEach { item ->
        val metadata = item.mediaMetadata
        val albumTitle = metadata.albumTitle.toArtistDisplayText() ?: unknownAlbumTitle
        val albumArtist = metadata.albumArtist.toArtistDisplayText()
        val albumId = metadata.extras?.getLong(AlbumIdExtraKey)?.takeIf { it > 0L }
        val groupingKey = albumId?.let { "album:$it" }
            ?: "${albumTitle.normalizedKey()}|${albumArtist?.artistNormalizedKey().orEmpty()}"
        val group = groups.getOrPut(groupingKey) {
            MutableAlbumGroup(
                id = groupingKey,
                title = albumTitle,
                albumArtist = albumArtist,
            )
        }
        group.songs += item
    }

    return groups.values
        .map { group ->
            val sortedSongs = group.songs.sortedWith(
                compareBy<MediaItem> { it.mediaMetadata.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.mediaMetadata.title?.toString().orEmpty().normalizedKey() },
            )
            AlbumSummary(
                id = group.id,
                title = group.title,
                artist = group.albumArtist ?: group.songs.distinctArtistName(
                    multipleArtistsTitle = multipleArtistsTitle,
                    artistSettings = artistSettings,
                ),
                songs = sortedSongs,
                year = sortedSongs.firstNotNullOfOrNull { item ->
                    item.mediaMetadata.releaseYear ?: item.mediaMetadata.recordingYear
                },
            )
        }
        .sortedBy { it.title.normalizedKey() }
}

internal fun MediaItem.displayTrackNumber(): String {
    val rawTrackNumber = mediaMetadata.trackNumber ?: return MissingAlbumTrackNumberSymbol
    val trackNumber = rawTrackNumber % 1000
    return (trackNumber.takeIf { it > 0 } ?: rawTrackNumber).toString()
}

private data class MutableAlbumGroup(
    val id: String,
    val title: String,
    val albumArtist: String?,
    val songs: MutableList<MediaItem> = mutableListOf(),
)

private fun List<MediaItem>.distinctArtistName(
    multipleArtistsTitle: String,
    artistSettings: ArtistSettings,
): String {
    val artists = flatMap { item ->
        item.mediaMetadata.artist.toArtistDisplayNames(
            artistSettings = artistSettings,
            unknownArtistTitle = "",
        )
    }
        .filter { it.isNotBlank() }
        .distinctBy { it.artistNormalizedKey() }
    return artists.singleOrNull() ?: multipleArtistsTitle
}

private fun String.normalizedKey(): String {
    return lowercase(Locale.ROOT)
}

private const val AlbumIdExtraKey = "com.smartisan.music.extra.ALBUM_ID"
internal const val MissingAlbumTrackNumberSymbol = "•"
