package com.smartisan.music.ui.album

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.library.LibraryAlbumEntry
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.LibraryPlayActions
import com.smartisan.music.ui.library.SmartisanAlbumCollection
import com.smartisan.music.ui.shell.PageStackTransition

@Composable
internal fun AlbumPage(
    mediaItems: List<MediaItem>,
    active: Boolean,
    viewMode: AlbumViewMode,
    editMode: Boolean,
    selectedAlbumId: String?,
    selectedAlbumIds: Set<String>,
    modifier: Modifier = Modifier,
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    hiddenMediaIds: Set<String>,
    onAlbumSelected: (String, String) -> Unit,
    onAlbumSelectionChange: (String, Boolean) -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
) {
    val browser = LocalPlaybackBrowser.current
    val visibleSongs =
        remember(mediaItems, hiddenMediaIds) {
            mediaItems.filterNot { mediaItem -> mediaItem.mediaId in hiddenMediaIds }
        }
    val unknownAlbumTitle = stringResource(R.string.unknown_album)
    val multipleArtistsTitle = stringResource(R.string.many_artist)
    val albums =
        remember(visibleSongs, unknownAlbumTitle, multipleArtistsTitle, artistSettings) {
            buildAlbumSummaries(
                mediaItems = visibleSongs,
                unknownAlbumTitle = unknownAlbumTitle,
                multipleArtistsTitle = multipleArtistsTitle,
                artistSettings = artistSettings,
            )
        }
    val selectedAlbum =
        remember(albums, selectedAlbumId) {
            albums.firstOrNull { album -> album.id == selectedAlbumId }
        }
    var currentMediaId by
        remember(browser) {
            mutableStateOf(browser?.currentMediaItem?.mediaId)
        }

    DisposableEffect(browser) {
        val playbackBrowser = browser ?: return@DisposableEffect onDispose {}
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    currentMediaId = player.currentMediaItem?.mediaId
                }
            }
        playbackBrowser.addListener(listener)
        onDispose {
            playbackBrowser.removeListener(listener)
        }
    }

    PageStackTransition(
        secondaryKey = selectedAlbum,
        modifier = modifier,
        label = "album detail transition",
        predictiveBackProgress = predictiveBackProgress,
        predictiveBackExitConsumed = predictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onPredictiveBackExitConsumedReset,
        primaryContent = {
            AlbumOverviewPage(
                active = active,
                albums = albums,
                currentMediaId = currentMediaId,
                browser = browser,
                viewMode = viewMode,
                editMode = editMode,
                selectedAlbumIds = selectedAlbumIds,
                onAlbumSelected = onAlbumSelected,
                onAlbumSelectionChange = onAlbumSelectionChange,
                modifier = Modifier.fillMaxSize(),
            )
        },
        secondaryContent = { album ->
            AlbumDetailPage(
                album = album,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onTrackMoreClick = onTrackMoreClick,
                artistSettings = artistSettings,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun AlbumOverviewPage(
    active: Boolean,
    albums: List<AlbumSummary>,
    currentMediaId: String?,
    browser: Player?,
    viewMode: AlbumViewMode,
    editMode: Boolean,
    selectedAlbumIds: Set<String>,
    onAlbumSelected: (String, String) -> Unit,
    onAlbumSelectionChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries =
        remember(albums) {
            albums.map { LibraryAlbumEntry(it.id, it.title, it.artist, it, it.songs) }
        }
    Column(modifier.fillMaxSize()) {
        if (active && albums.isNotEmpty())
            LibraryPlayActions(
                enabled = !editMode,
                onPlay = { browser.replaceQueueAndPlay(albums.flatMap(AlbumSummary::songs)) },
                onShuffle = {
                    browser.replaceQueueAndPlayShuffled(albums.flatMap(AlbumSummary::songs))
                },
                modifier = Modifier.graphicsLayer { alpha = if (editMode) .22f else 1f },
            )
        SmartisanAlbumCollection(
            entries = entries,
            active = active,
            viewMode = viewMode,
            currentMediaId = currentMediaId,
            onClick = { onAlbumSelected(it.id, it.title) },
            editMode = editMode,
            selectedIds = selectedAlbumIds,
            onSelectionChange = onAlbumSelectionChange,
            revealInitialGrid = true,
            modifier = Modifier.fillMaxWidth().weight(1f),
            footer = { LibraryFooter(R.plurals.library_album_count, albums.size) },
        )
    }
}
