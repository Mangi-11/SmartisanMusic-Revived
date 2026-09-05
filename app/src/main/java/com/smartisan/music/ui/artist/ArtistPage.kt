package com.smartisan.music.ui.artist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.shell.PageStackTransition

internal sealed interface ArtistTarget {
    val artistId: String
    val artistName: String
    val title: String

    data class Albums(
        override val artistId: String,
        override val artistName: String,
    ) : ArtistTarget {
        override val title: String = artistName
    }

    data class AllSongs(
        override val artistId: String,
        override val artistName: String,
        override val title: String,
    ) : ArtistTarget

    data class Album(
        override val artistId: String,
        override val artistName: String,
        val albumId: String,
        override val title: String,
        val fromArtistAlbums: Boolean,
    ) : ArtistTarget
}

internal fun ArtistTarget.parentTarget(): ArtistTarget? {
    return when (this) {
        is ArtistTarget.Albums -> null
        is ArtistTarget.AllSongs ->
            ArtistTarget.Albums(
                artistId = artistId,
                artistName = artistName,
            )
        is ArtistTarget.Album ->
            if (fromArtistAlbums) {
                ArtistTarget.Albums(
                    artistId = artistId,
                    artistName = artistName,
                )
            } else {
                null
            }
    }
}

internal val ArtistTarget.showsAlbumSwitch: Boolean
    get() = this is ArtistTarget.Albums

@Composable
internal fun ArtistPage(
    mediaItems: List<MediaItem>,
    active: Boolean,
    selectedTarget: ArtistTarget?,
    albumViewMode: AlbumViewMode,
    modifier: Modifier = Modifier,
    rootPredictiveBackProgress: Float? = null,
    rootPredictiveBackExitConsumed: Boolean = false,
    onRootPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    nestedPredictiveBackProgress: Float? = null,
    nestedPredictiveBackExitConsumed: Boolean = false,
    onNestedPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    hiddenMediaIds: Set<String>,
    onTargetChanged: (ArtistTarget?) -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val visibleSongs =
        remember(mediaItems, hiddenMediaIds) {
            mediaItems.filterNot { mediaItem -> mediaItem.mediaId in hiddenMediaIds }
        }
    val unknownArtistTitle = stringResource(R.string.unknown_artist)
    val unknownAlbumTitle = stringResource(R.string.unknown_album)
    val allSongsTitle = stringResource(R.string.artist_all_songs)
    val artists =
        remember(visibleSongs, unknownArtistTitle, unknownAlbumTitle, artistSettings) {
            buildArtistSummaries(
                mediaItems = visibleSongs,
                unknownArtistTitle = unknownArtistTitle,
                unknownAlbumTitle = unknownAlbumTitle,
                artistSettings = artistSettings,
            )
        }
    val selectedArtist =
        remember(artists, selectedTarget) {
            selectedTarget?.artistId?.let { artistId ->
                artists.firstOrNull { artist -> artist.id == artistId }
            }
        }
    val selectedArtistAlbums =
        remember(selectedArtist, context, artistSettings) {
            selectedArtist
                ?.albumSummaries(
                    context = context,
                    artistSettings = artistSettings,
                )
                .orEmpty()
        }
    val selectedArtistState =
        remember(selectedArtist, selectedArtistAlbums, selectedTarget) {
            if (selectedArtist != null && selectedTarget != null) {
                val retainedTarget =
                    selectedTarget.parentTarget()?.takeIf { parentTarget ->
                        parentTarget.artistId == selectedArtist.id
                    } ?: selectedTarget
                SelectedArtistState(
                    artist = selectedArtist,
                    target = retainedTarget,
                    albums = selectedArtistAlbums,
                )
            } else {
                null
            }
        }

    PageStackTransition(
        secondaryKey = selectedArtistState,
        modifier = modifier,
        label = "artist transition",
        predictiveBackProgress = rootPredictiveBackProgress,
        predictiveBackExitConsumed = rootPredictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onRootPredictiveBackExitConsumedReset,
        primaryContent = {
            ArtistOverviewPage(
                active = active,
                artists = artists,
                onArtistSelected = { artist ->
                    val albums =
                        artist.albumSummaries(
                            context = context,
                            artistSettings = artistSettings,
                        )
                    val target =
                        if (albums.size > 1) {
                            ArtistTarget.Albums(
                                artistId = artist.id,
                                artistName = artist.name,
                            )
                        } else {
                            val album = albums.firstOrNull()
                            if (album == null) {
                                ArtistTarget.AllSongs(
                                    artistId = artist.id,
                                    artistName = artist.name,
                                    title = allSongsTitle,
                                )
                            } else {
                                ArtistTarget.Album(
                                    artistId = artist.id,
                                    artistName = artist.name,
                                    albumId = album.id,
                                    title = album.title,
                                    fromArtistAlbums = false,
                                )
                            }
                        }
                    onTargetChanged(target)
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
        secondaryContent = { state ->
            SelectedArtistPage(
                artist = state.artist,
                albums =
                    state.albums.ifEmpty {
                        state.artist.albumSummaries(
                            context = context,
                            artistSettings = artistSettings,
                        )
                    },
                target =
                    selectedTarget?.takeIf { target -> target.artistId == state.artist.id }
                        ?: state.target,
                browser = browser,
                albumViewMode = albumViewMode,
                predictiveBackProgress = nestedPredictiveBackProgress,
                predictiveBackExitConsumed = nestedPredictiveBackExitConsumed,
                onPredictiveBackExitConsumedReset = onNestedPredictiveBackExitConsumedReset,
                onTargetChanged = onTargetChanged,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onTrackMoreClick = onTrackMoreClick,
                artistSettings = artistSettings,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}
