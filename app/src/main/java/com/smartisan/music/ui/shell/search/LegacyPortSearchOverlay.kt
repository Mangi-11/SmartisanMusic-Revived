package com.smartisan.music.ui.shell.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.album.buildAlbumSummaries
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.search.GlobalSearchScreen
import com.smartisan.music.ui.shell.LegacyArtistTarget
import com.smartisan.music.ui.shell.LegacyPortAlbumDetailPage
import com.smartisan.music.ui.shell.LegacyPortArtistPage
import com.smartisan.music.ui.shell.LegacyPortArtistTitleStack
import com.smartisan.music.ui.shell.LegacyPortPageStackTransition
import com.smartisan.music.ui.shell.parentTarget
import com.smartisan.music.ui.shell.titlebar.LegacyPortSearchDetailTitleBar

private const val LegacySearchTransitionDurationMillis = 300
private const val LegacySearchExitOffsetMultiplier = 1.09f

internal sealed interface LegacySearchDrilldownTarget {
    data class Album(
        val albumId: String,
        val albumTitle: String,
    ) : LegacySearchDrilldownTarget

    data class Artist(
        val target: LegacyArtistTarget,
    ) : LegacySearchDrilldownTarget
}

private sealed interface LegacySearchDrilldownPageKey {
    data class Album(
        val albumId: String,
        val albumTitle: String,
    ) : LegacySearchDrilldownPageKey

    data class Artist(
        val artistId: String,
        val artistName: String,
    ) : LegacySearchDrilldownPageKey
}

private fun LegacySearchDrilldownTarget.toPageKey(): LegacySearchDrilldownPageKey {
    return when (this) {
        is LegacySearchDrilldownTarget.Album -> LegacySearchDrilldownPageKey.Album(
            albumId = albumId,
            albumTitle = albumTitle,
        )
        is LegacySearchDrilldownTarget.Artist -> LegacySearchDrilldownPageKey.Artist(
            artistId = target.artistId,
            artistName = target.artistName,
        )
    }
}

private fun LegacySearchDrilldownPageKey.toRootTarget(): LegacySearchDrilldownTarget {
    return when (this) {
        is LegacySearchDrilldownPageKey.Album -> LegacySearchDrilldownTarget.Album(
            albumId = albumId,
            albumTitle = albumTitle,
        )
        is LegacySearchDrilldownPageKey.Artist -> LegacySearchDrilldownTarget.Artist(
            target = LegacyArtistTarget.Albums(
                artistId = artistId,
                artistName = artistName,
            ),
        )
    }
}

private val LegacySearchDecelerateEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

@Composable
internal fun LegacyPortSearchOverlay(
    visible: Boolean,
    query: String,
    mediaItems: List<MediaItem>,
    hiddenMediaIds: Set<String>,
    drilldownTarget: LegacySearchDrilldownTarget?,
    libraryRefreshVersion: Int,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenPlayback: () -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    onDrilldownTargetChanged: (LegacySearchDrilldownTarget?) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    artistAlbumViewMode: AlbumViewMode,
    onToggleArtistAlbumViewMode: () -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = slideInVertically(
            animationSpec = tween(
                durationMillis = LegacySearchTransitionDurationMillis,
                easing = LegacySearchDecelerateEasing,
            ),
            initialOffsetY = { fullHeight -> fullHeight },
        ),
        exit = slideOutVertically(
            animationSpec = tween(
                durationMillis = LegacySearchTransitionDurationMillis,
                easing = LegacySearchDecelerateEasing,
            ),
            targetOffsetY = { fullHeight ->
                (fullHeight * LegacySearchExitOffsetMultiplier).toInt()
            },
        ),
    ) {
        val drilldownPageKey = drilldownTarget?.toPageKey()
        LegacyPortPageStackTransition(
            secondaryKey = drilldownPageKey,
            modifier = Modifier.fillMaxSize(),
            label = "legacy search detail transition",
            primaryContent = {
                GlobalSearchScreen(
                    query = query,
                    libraryRefreshVersion = libraryRefreshVersion,
                    onQueryChange = onQueryChange,
                    onDismiss = onDismiss,
                    onOpenPlayback = onOpenPlayback,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    artistSettings = artistSettings,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            secondaryContent = { pageKey ->
                val target = drilldownTarget
                    ?.takeIf { currentTarget -> currentTarget.toPageKey() == pageKey }
                    ?: pageKey.toRootTarget()
                LegacyPortSearchDrilldownPage(
                    target = target,
                    mediaItems = mediaItems,
                    hiddenMediaIds = hiddenMediaIds,
                    artistAlbumViewMode = artistAlbumViewMode,
                    onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
                    onBack = {
                        when (target) {
                            is LegacySearchDrilldownTarget.Album -> onDrilldownTargetChanged(null)
                            is LegacySearchDrilldownTarget.Artist -> {
                                val parentTarget = target.target.parentTarget()
                                onDrilldownTargetChanged(
                                    parentTarget?.let(LegacySearchDrilldownTarget::Artist),
                                )
                            }
                        }
                    },
                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                    onRequestAddToQueue = onRequestAddToQueue,
                    onTrackMoreClick = onTrackMoreClick,
                    artistSettings = artistSettings,
                    onArtistTargetChanged = { artistTarget ->
                        onDrilldownTargetChanged(
                            artistTarget?.let(LegacySearchDrilldownTarget::Artist),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}

@Composable
private fun LegacyPortSearchDrilldownPage(
    target: LegacySearchDrilldownTarget,
    mediaItems: List<MediaItem>,
    hiddenMediaIds: Set<String>,
    artistAlbumViewMode: AlbumViewMode,
    onToggleArtistAlbumViewMode: () -> Unit,
    onBack: () -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    onArtistTargetChanged: (LegacyArtistTarget?) -> Unit,
    artistSettings: ArtistSettings,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val visibleSongs = remember(mediaItems, hiddenMediaIds) {
        mediaItems.filterNot { mediaItem -> mediaItem.mediaId in hiddenMediaIds }
    }
    val unknownAlbumTitle = stringResource(R.string.unknown_album)
    val multipleArtistsTitle = stringResource(R.string.many_artist)
    val albums = remember(visibleSongs, unknownAlbumTitle, multipleArtistsTitle, artistSettings) {
        buildAlbumSummaries(
            mediaItems = visibleSongs,
            unknownAlbumTitle = unknownAlbumTitle,
            multipleArtistsTitle = multipleArtistsTitle,
            artistSettings = artistSettings,
        )
    }
    val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
    val titleAreaHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + titleContentHeight

    Column(
        modifier = modifier.background(ComposeColor.White),
    ) {
        when (target) {
            is LegacySearchDrilldownTarget.Album -> {
                val album = remember(albums, target.albumId) {
                    albums.firstOrNull { album -> album.id == target.albumId }
                }
                LegacyPortSearchDetailTitleBar(
                    destination = MusicDestination.Album,
                    albumDetailTitle = album?.title ?: target.albumTitle,
                    artistTarget = null,
                    onBack = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(titleAreaHeight),
                )
                if (album != null) {
                    LegacyPortAlbumDetailPage(
                        album = album,
                        onRequestAddToPlaylist = onRequestAddToPlaylist,
                        onRequestAddToQueue = onRequestAddToQueue,
                        onTrackMoreClick = onTrackMoreClick,
                        artistSettings = artistSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(ComposeColor.White),
                    )
                }
            }
            is LegacySearchDrilldownTarget.Artist -> {
                LegacyPortArtistTitleStack(
                    selectedTarget = target.target,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(titleAreaHeight),
                ) { artistTarget, titleModifier ->
                    LegacyPortSearchDetailTitleBar(
                        destination = MusicDestination.Artist,
                        albumDetailTitle = null,
                        artistTarget = artistTarget,
                        onBack = onBack,
                        artistAlbumViewMode = artistAlbumViewMode,
                        onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
                        modifier = titleModifier,
                    )
                }
                LegacyPortArtistPage(
                    mediaItems = visibleSongs,
                    active = true,
                    selectedTarget = target.target,
                    albumViewMode = artistAlbumViewMode,
                    hiddenMediaIds = emptySet(),
                    onTargetChanged = onArtistTargetChanged,
                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                    onRequestAddToQueue = onRequestAddToQueue,
                    onTrackMoreClick = onTrackMoreClick,
                    artistSettings = artistSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}
