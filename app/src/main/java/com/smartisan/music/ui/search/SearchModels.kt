package com.smartisan.music.ui.search

import androidx.media3.common.MediaItem
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.album.buildAlbumSummaries
import com.smartisan.music.ui.artist.ArtistSummary
import com.smartisan.music.ui.artist.buildArtistSummaries
import com.smartisan.music.ui.artist.toArtistDisplayNames
import java.util.Locale

internal data class SearchResults(
    val query: String,
    val songs: List<MediaItem>,
    val albums: List<AlbumSummary>,
    val artists: List<ArtistSummary>,
) {
    val hasResults: Boolean = songs.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()
}

internal fun buildSearchResults(
    query: String,
    songs: List<MediaItem>,
    unknownAlbumTitle: String,
    unknownArtistTitle: String,
    multipleArtistsTitle: String,
    artistSettings: ArtistSettings = ArtistSettings(),
): SearchResults {
    val normalizedQuery = normalizeSearchQuery(query)
    if (normalizedQuery.isEmpty()) {
        return SearchResults(
            query = normalizedQuery,
            songs = emptyList(),
            albums = emptyList(),
            artists = emptyList(),
        )
    }

    val matchedSongs = songs.filter { item ->
        item.searchableSongFields().any { field ->
            field.contains(normalizedQuery)
        }
    }
    val matchedAlbums = buildAlbumSummaries(
        mediaItems = songs,
        unknownAlbumTitle = unknownAlbumTitle,
        multipleArtistsTitle = multipleArtistsTitle,
        artistSettings = artistSettings,
    ).filter { album ->
        album.searchableAlbumFields(
            artistSettings = artistSettings,
            unknownArtistTitle = unknownArtistTitle,
        ).any { field ->
            field.contains(normalizedQuery)
        }
    }
    val matchedArtists = buildArtistSummaries(
        mediaItems = songs,
        unknownArtistTitle = unknownArtistTitle,
        unknownAlbumTitle = unknownAlbumTitle,
        artistSettings = artistSettings,
    ).filter { artist ->
        artist.searchableArtistFields().any { field ->
            field.contains(normalizedQuery)
        }
    }

    return SearchResults(
        query = normalizedQuery,
        songs = matchedSongs,
        albums = matchedAlbums,
        artists = matchedArtists,
    )
}

internal fun normalizeSearchQuery(query: String): String {
    return query.trim().lowercase(Locale.ROOT)
}

private fun MediaItem.searchableSongFields(): List<String> {
    val metadata = mediaMetadata
    return listOfNotNull(
        metadata.title?.toString(),
        metadata.displayTitle?.toString(),
        metadata.artist?.toString(),
        metadata.albumTitle?.toString(),
        metadata.albumArtist?.toString(),
    ).map(::normalizeSearchQuery)
}

private fun AlbumSummary.searchableAlbumFields(
    artistSettings: ArtistSettings,
    unknownArtistTitle: String,
): List<String> {
    if (artistSettings.separators.isEmpty()) {
        return listOf(title, artist).map(::normalizeSearchQuery)
    }
    val songArtistNames = songs.flatMap { item ->
        item.mediaMetadata.artist.toArtistDisplayNames(
            artistSettings = artistSettings,
            unknownArtistTitle = unknownArtistTitle,
        )
    }
    return (listOf(title, artist) + songArtistNames)
        .distinctBy(::normalizeSearchQuery)
        .map(::normalizeSearchQuery)
}

private fun ArtistSummary.searchableArtistFields(): List<String> {
    return listOf(name).map(::normalizeSearchQuery)
}
