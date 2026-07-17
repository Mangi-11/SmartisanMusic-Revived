package com.smartisan.music.ui.artist

import androidx.media3.common.MediaItem
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalAudioLibrary
import java.text.Collator
import java.util.Locale

internal data class ArtistSummary(
    val id: String,
    val name: String,
    val songs: List<MediaItem>,
    val albumCount: Int,
) {
    val representative: MediaItem = songs.first()
    val trackCount: Int = songs.size
}

internal fun buildArtistSummaries(
    mediaItems: List<MediaItem>,
    unknownArtistTitle: String,
    unknownAlbumTitle: String,
    artistSettings: ArtistSettings = ArtistSettings(),
): List<ArtistSummary> {
    val groups = linkedMapOf<String, MutableArtistGroup>()

    mediaItems.forEach { item ->
        val metadata = item.mediaMetadata
        val artistNames = metadata.artist.toArtistDisplayNames(
            artistSettings = artistSettings,
            unknownArtistTitle = unknownArtistTitle,
        )
        artistNames.forEach { artistName ->
            val artistKey = artistName.artistNormalizedKey()
            val group = groups.getOrPut(artistKey) {
                MutableArtistGroup(
                    id = "artist:$artistKey",
                    name = artistName,
                )
            }
            group.songs += item
            group.albumKeys += item.albumGroupingKey(unknownAlbumTitle)
        }
    }

    return groups.values
        .map { group ->
            ArtistSummary(
                id = group.id,
                name = group.name,
                songs = group.songs.sortedWith(
                    compareBy<MediaItem> {
                        it.mediaMetadata.albumTitle.toArtistDisplayText()?.artistNormalizedKey().orEmpty()
                    }
                        .thenBy { it.mediaMetadata.trackNumber ?: Int.MAX_VALUE }
                        .thenBy { it.mediaMetadata.title?.toString().orEmpty().artistNormalizedKey() },
                ),
                albumCount = group.albumKeys.size,
            )
        }
        .sortedWith(artistNameComparator())
}

private data class MutableArtistGroup(
    val id: String,
    val name: String,
    val songs: MutableList<MediaItem> = mutableListOf(),
    val albumKeys: MutableSet<String> = linkedSetOf(),
)

private fun MediaItem.albumGroupingKey(unknownAlbumTitle: String): String {
    val albumId = mediaMetadata.extras
        ?.getLong(LocalAudioLibrary.AlbumIdExtraKey)
        ?.takeIf { it > 0L }
    return albumId?.let { "album:$it" }
        ?: (mediaMetadata.albumTitle.toArtistDisplayText() ?: unknownAlbumTitle).artistNormalizedKey()
}

private fun artistNameComparator(): Comparator<ArtistSummary> {
    val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }
    return Comparator { left, right ->
        val localizedOrder = collator.compare(left.name, right.name)
        if (localizedOrder != 0) {
            localizedOrder
        } else {
            left.name.artistNormalizedKey().compareTo(right.name.artistNormalizedKey())
        }
    }
}
