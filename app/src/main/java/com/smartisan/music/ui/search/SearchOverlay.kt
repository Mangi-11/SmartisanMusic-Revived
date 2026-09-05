package com.smartisan.music.ui.search

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.ui.album.AlbumDetailPage
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.album.buildAlbumSummaries
import com.smartisan.music.ui.artist.ArtistPage
import com.smartisan.music.ui.artist.ArtistTarget
import com.smartisan.music.ui.artist.ArtistTitleStack
import com.smartisan.music.ui.artist.parentTarget
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.PageStackTransition
import com.smartisan.music.ui.shell.titlebar.SearchDetailTitleBar

private const val SearchTransitionDurationMillis = 300
private const val SearchExitOffsetMultiplier = 1.09f

internal sealed interface SearchDrilldownTarget {
    data class Album(
        val albumId: String,
        val albumTitle: String,
    ) : SearchDrilldownTarget

    data class Artist(val target: ArtistTarget) : SearchDrilldownTarget
}

private sealed interface SearchDrilldownPageKey {
    data class Album(
        val albumId: String,
        val albumTitle: String,
    ) : SearchDrilldownPageKey

    data class Artist(
        val artistId: String,
        val artistName: String,
    ) : SearchDrilldownPageKey
}

private fun SearchDrilldownTarget.toPageKey(): SearchDrilldownPageKey {
    return when (this) {
        is SearchDrilldownTarget.Album ->
            SearchDrilldownPageKey.Album(
                albumId = albumId,
                albumTitle = albumTitle,
            )
        is SearchDrilldownTarget.Artist ->
            SearchDrilldownPageKey.Artist(
                artistId = target.artistId,
                artistName = target.artistName,
            )
    }
}

private fun SearchDrilldownPageKey.toRootTarget(): SearchDrilldownTarget {
    return when (this) {
        is SearchDrilldownPageKey.Album ->
            SearchDrilldownTarget.Album(
                albumId = albumId,
                albumTitle = albumTitle,
            )
        is SearchDrilldownPageKey.Artist ->
            SearchDrilldownTarget.Artist(
                target =
                    ArtistTarget.Albums(
                        artistId = artistId,
                        artistName = artistName,
                    )
            )
    }
}

private val SearchDecelerateEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

@Composable
internal fun SearchOverlay(
    visible: Boolean,
    query: String,
    mediaItems: List<MediaItem>,
    hiddenMediaIds: Set<String>,
    drilldownTarget: SearchDrilldownTarget?,
    libraryRefreshVersion: Int,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenPlayback: () -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    onDrilldownTargetChanged: (SearchDrilldownTarget?) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    artistAlbumViewMode: AlbumViewMode,
    onToggleArtistAlbumViewMode: () -> Unit,
    modifier: Modifier = Modifier,
    artistSettings: ArtistSettings = ArtistSettings(),
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter =
            slideInVertically(
                animationSpec =
                    tween(
                        durationMillis = SearchTransitionDurationMillis,
                        easing = SearchDecelerateEasing,
                    ),
                initialOffsetY = { fullHeight -> fullHeight },
            ),
        exit =
            slideOutVertically(
                animationSpec =
                    tween(
                        durationMillis = SearchTransitionDurationMillis,
                        easing = SearchDecelerateEasing,
                    ),
                targetOffsetY = { fullHeight ->
                    (fullHeight * SearchExitOffsetMultiplier).toInt()
                },
            ),
    ) {
        val drilldownPageKey = drilldownTarget?.toPageKey()
        PageStackTransition(
            secondaryKey = drilldownPageKey,
            modifier = Modifier.fillMaxSize(),
            label = "search detail transition",
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
                val target =
                    drilldownTarget?.takeIf { currentTarget ->
                        currentTarget.toPageKey() == pageKey
                    } ?: pageKey.toRootTarget()
                SearchDrilldownPage(
                    target = target,
                    mediaItems = mediaItems,
                    hiddenMediaIds = hiddenMediaIds,
                    artistAlbumViewMode = artistAlbumViewMode,
                    onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
                    onBack = {
                        when (target) {
                            is SearchDrilldownTarget.Album -> onDrilldownTargetChanged(null)
                            is SearchDrilldownTarget.Artist -> {
                                val parentTarget = target.target.parentTarget()
                                onDrilldownTargetChanged(
                                    parentTarget?.let(SearchDrilldownTarget::Artist)
                                )
                            }
                        }
                    },
                    onRequestAddToPlaylist = onRequestAddToPlaylist,
                    onRequestAddToQueue = onRequestAddToQueue,
                    onTrackMoreClick = onTrackMoreClick,
                    artistSettings = artistSettings,
                    onArtistTargetChanged = { artistTarget ->
                        onDrilldownTargetChanged(artistTarget?.let(SearchDrilldownTarget::Artist))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}

@Composable
private fun SearchDrilldownPage(
    target: SearchDrilldownTarget,
    mediaItems: List<MediaItem>,
    hiddenMediaIds: Set<String>,
    artistAlbumViewMode: AlbumViewMode,
    onToggleArtistAlbumViewMode: () -> Unit,
    onBack: () -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    onArtistTargetChanged: (ArtistTarget?) -> Unit,
    artistSettings: ArtistSettings,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
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
    val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
    val titleAreaHeight =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + titleContentHeight

    Column(modifier = modifier.background(colorResource(R.color.page_background))) {
        when (target) {
            is SearchDrilldownTarget.Album -> {
                val album =
                    remember(albums, target.albumId) {
                        albums.firstOrNull { album -> album.id == target.albumId }
                    }
                SearchDetailTitleBar(
                    destination = MusicDestination.Album,
                    albumDetailTitle = album?.title ?: target.albumTitle,
                    artistTarget = null,
                    onBack = onBack,
                    modifier = Modifier.fillMaxWidth().height(titleAreaHeight),
                )
                if (album != null) {
                    AlbumDetailPage(
                        album = album,
                        onRequestAddToPlaylist = onRequestAddToPlaylist,
                        onRequestAddToQueue = onRequestAddToQueue,
                        onTrackMoreClick = onTrackMoreClick,
                        artistSettings = artistSettings,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .weight(1f)
                                .background(colorResource(R.color.page_background))
                    )
                }
            }
            is SearchDrilldownTarget.Artist -> {
                ArtistTitleStack(
                    selectedTarget = target.target,
                    modifier = Modifier.fillMaxWidth().height(titleAreaHeight),
                ) { artistTarget, titleModifier ->
                    SearchDetailTitleBar(
                        destination = MusicDestination.Artist,
                        albumDetailTitle = null,
                        artistTarget = artistTarget,
                        onBack = onBack,
                        artistAlbumViewMode = artistAlbumViewMode,
                        onToggleArtistAlbumViewMode = onToggleArtistAlbumViewMode,
                        modifier = titleModifier,
                    )
                }
                ArtistPage(
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
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}
