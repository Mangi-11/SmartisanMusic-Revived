package com.smartisan.music.ui.artist

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.album.AlbumDetailPage
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.album.albumDetailArtistText
import com.smartisan.music.ui.album.buildAlbumSummaries
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanTextSize
import com.smartisan.music.ui.library.LibraryAlbumEntry
import com.smartisan.music.ui.library.LibraryDivider
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.LibraryIconButton
import com.smartisan.music.ui.library.SmartisanAlbumCollection
import com.smartisan.music.ui.library.SmartisanAlbumTrackRow
import com.smartisan.music.ui.library.libraryTexture
import com.smartisan.music.ui.shell.PageStackTransition
import com.smartisan.music.ui.songs.rememberSongPlaybackState
import java.text.Collator
import java.util.Locale

@Composable
internal fun SelectedArtistPage(
    artist: ArtistSummary,
    albums: List<AlbumSummary>,
    target: ArtistTarget?,
    browser: Player?,
    albumViewMode: AlbumViewMode,
    modifier: Modifier = Modifier,
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    onTargetChanged: (ArtistTarget?) -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
) {
    val directAlbumTarget = target as? ArtistTarget.Album
    if (directAlbumTarget != null && !directAlbumTarget.fromArtistAlbums) {
        albums
            .firstOrNull { album -> album.id == directAlbumTarget.albumId }
            ?.let { album ->
                AlbumDetailPage(
                    album = album,
                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                    onRequestAddToQueue = onRequestAddToQueue,
                    onTrackMoreClick = onTrackMoreClick,
                    artistSettings = artistSettings,
                    modifier = modifier,
                )
                return
            }
    }

    val nestedTarget = target?.takeIf { currentTarget ->
        currentTarget.artistId == artist.id && currentTarget !is ArtistTarget.Albums
    }
    val allSongsTitle = stringResource(R.string.artist_all_songs)
    val entries =
        remember(artist, albums, allSongsTitle) {
            buildArtistAlbumEntries(
                artist = artist,
                albums = albums,
                allSongsTitle = allSongsTitle,
            )
        }
    PageStackTransition(
        secondaryKey = nestedTarget,
        modifier = modifier,
        label = "selected artist transition",
        predictiveBackProgress = predictiveBackProgress,
        predictiveBackExitConsumed = predictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onPredictiveBackExitConsumedReset,
        primaryContent = {
            ArtistAlbumsPage(
                artist = artist,
                entries = entries,
                browser = browser,
                viewMode = albumViewMode,
                onTargetChanged = onTargetChanged,
                modifier = Modifier.fillMaxSize(),
            )
        },
        secondaryContent = { detailTarget ->
            when (detailTarget) {
                is ArtistTarget.AllSongs -> {
                    ArtistAllSongsPage(
                        artistName = artist.name,
                        songs = artist.songs,
                        onTrackMoreClick = onTrackMoreClick,
                        artistSettings = artistSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is ArtistTarget.Album -> {
                    albums
                        .firstOrNull { album -> album.id == detailTarget.albumId }
                        ?.let { album ->
                            AlbumDetailPage(
                                album = album,
                                onRequestAddToPlaylist = onRequestAddToPlaylist,
                                onRequestAddToQueue = onRequestAddToQueue,
                                onTrackMoreClick = onTrackMoreClick,
                                artistSettings = artistSettings,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                }
                is ArtistTarget.Albums -> Unit
            }
        },
    )
}

@Composable
private fun ArtistAlbumsPage(
    artist: ArtistSummary,
    entries: List<ArtistAlbumEntry>,
    browser: Player?,
    viewMode: AlbumViewMode,
    onTargetChanged: (ArtistTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val playback = rememberSongPlaybackState(browser)
    val allSongsTitle = stringResource(R.string.artist_all_songs)
    val libraryEntries =
        remember(entries, resources) {
            entries.map { item ->
                LibraryAlbumEntry(
                    item.stableId,
                    item.title,
                    resources.getQuantityString(
                        R.plurals.album_track_count,
                        item.trackCount,
                        item.trackCount,
                    ),
                    (item as? ArtistAlbumEntry.Album)?.album,
                    item.songs,
                )
            }
        }
    SmartisanAlbumCollection(
        libraryEntries,
        active = true,
        viewMode = viewMode,
        currentMediaId = playback.mediaId,
        onClick = { entry ->
            entries
                .firstOrNull { it.stableId == entry.id }
                ?.toTarget(artist, allSongsTitle)
                ?.let(onTargetChanged)
        },
        artistAlbums = true,
        modifier = modifier,
    )
}

@Composable
private fun ArtistAllSongsPage(
    artistName: String,
    songs: List<MediaItem>,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings,
    modifier: Modifier = Modifier,
) {
    val browser = LocalPlaybackBrowser.current
    val playback = rememberSongPlaybackState()
    val sortedSongs = remember(songs) { songs.sortedWith(artistAllSongsComparator()) }
    LazyColumn(modifier.fillMaxSize().libraryTexture()) {
        item(key = "artist-all-header") {
            ArtistAllSongsHeader(artistName, sortedSongs.isNotEmpty()) {
                browser.replaceQueueAndPlayShuffled(sortedSongs)
            }
        }
        itemsIndexed(sortedSongs, key = { index, item -> "${item.mediaId}:$index" }) { index, item
            ->
            SmartisanAlbumTrackRow(
                item,
                (index + 1).toString(),
                item.albumDetailArtistText(artistSettings),
                showArtist = true,
                playback = playback,
                onClick = { browser.replaceQueueAndPlay(sortedSongs, index) },
                onMoreClick = onTrackMoreClick,
            )
            LibraryDivider()
        }
        item(key = "artist-all-footer") { LibraryFooter(R.plurals.track_count, sortedSongs.size) }
    }
}

@Composable
private fun ArtistAllSongsHeader(artistName: String, enabled: Boolean, onShuffle: () -> Unit) {
    val coverSize = dimensionResource(R.dimen.gridview_item_ccontainer_height)
    Box(Modifier.fillMaxWidth().height(150.dp).libraryTexture(R.drawable.ablum_crosstexture_bg)) {
        Image(
            rememberSmartisanDrawablePainter(R.drawable.noalbumcover_all_songs2),
            null,
            Modifier.align(Alignment.CenterStart)
                .padding(start = dimensionResource(R.dimen.gridview_margin))
                .size(coverSize),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier.align(Alignment.CenterStart)
                .padding(
                    start =
                        dimensionResource(R.dimen.gridview_margin) +
                            coverSize +
                            dimensionResource(R.dimen.alum_tile_paddingleft)
                )
        ) {
            BasicText(
                stringResource(R.string.artist_all_songs),
                style =
                    TextStyle(
                        color = colorResource(R.color.setting_item_text_color),
                        fontSize = smartisanTextSize(R.dimen.text_size_medium),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                artistName,
                style =
                    TextStyle(
                        color = colorResource(R.color.setting_item_text_color),
                        fontSize = smartisanTextSize(R.dimen.text_size_small),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LibraryIconButton(
            R.drawable.btn_album_shuffle3_selector,
            stringResource(R.string.play_shuffle),
            onShuffle,
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 6.dp),
            enabled,
        )
        Image(
            rememberSmartisanDrawablePainter(R.drawable.ablum_crosstexture_bg_shadow),
            null,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp),
            contentScale = ContentScale.FillBounds,
        )
    }
}

private sealed class ArtistAlbumEntry {
    abstract val stableId: String
    abstract val title: String
    abstract val songs: List<MediaItem>

    val trackCount: Int
        get() = songs.size

    data class AllSongs(
        override val stableId: String,
        override val title: String,
        override val songs: List<MediaItem>,
    ) : ArtistAlbumEntry()

    data class Album(val album: AlbumSummary) : ArtistAlbumEntry() {
        override val stableId: String = album.id
        override val title: String = album.title
        override val songs: List<MediaItem> = album.songs
    }
}

private fun buildArtistAlbumEntries(
    artist: ArtistSummary,
    albums: List<AlbumSummary>,
    allSongsTitle: String,
): List<ArtistAlbumEntry> {
    return listOf(
        ArtistAlbumEntry.AllSongs(
            stableId = "${artist.id}:all",
            title = allSongsTitle,
            songs = artist.songs,
        )
    ) + albums.map { album -> ArtistAlbumEntry.Album(album) }
}

private fun ArtistAlbumEntry.toTarget(
    artist: ArtistSummary,
    allSongsTitle: String,
): ArtistTarget {
    return when (this) {
        is ArtistAlbumEntry.AllSongs ->
            ArtistTarget.AllSongs(
                artistId = artist.id,
                artistName = artist.name,
                title = allSongsTitle,
            )
        is ArtistAlbumEntry.Album ->
            ArtistTarget.Album(
                artistId = artist.id,
                artistName = artist.name,
                albumId = album.id,
                title = album.title,
                fromArtistAlbums = true,
            )
    }
}

internal fun ArtistSummary.albumSummaries(
    context: Context,
    artistSettings: ArtistSettings = ArtistSettings(),
): List<AlbumSummary> {
    return buildAlbumSummaries(
        mediaItems = songs,
        unknownAlbumTitle = context.getString(R.string.unknown_album),
        multipleArtistsTitle = context.getString(R.string.many_artist),
        artistSettings = artistSettings,
    )
}

private fun artistAllSongsComparator(): Comparator<MediaItem> {
    val collator =
        Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }
    return Comparator { left, right ->
        val leftTitle = left.artistAllSongsSortTitle()
        val rightTitle = right.artistAllSongsSortTitle()
        val localized = collator.compare(leftTitle, rightTitle)
        if (localized != 0) {
            localized
        } else {
            leftTitle.lowercase(Locale.ROOT).compareTo(rightTitle.lowercase(Locale.ROOT))
        }
    }
}

private fun MediaItem.artistAllSongsSortTitle(): String {
    return (mediaMetadata.displayTitle ?: mediaMetadata.title)?.toString()?.trim()?.takeIf {
        it.isNotEmpty()
    } ?: mediaId
}
