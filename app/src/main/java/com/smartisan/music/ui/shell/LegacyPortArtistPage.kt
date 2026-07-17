package com.smartisan.music.ui.shell

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.album.buildAlbumSummaries
import com.smartisan.music.ui.artist.ArtistSummary
import com.smartisan.music.ui.artist.buildArtistSummaries
import java.text.Collator
import java.util.Locale

internal const val ArtistAlbumSwitchBaseDurationMillis = 150L
internal const val ArtistAlbumSwitchStaggerMillis = 10L
internal const val LegacyArtistListFooterThreshold = 8
internal val LegacyArtistPrimaryTextColor = Color.rgb(0x35, 0x35, 0x39)
internal val LegacyArtistSecondaryTextColor = Color.rgb(0xa4, 0xa7, 0xac)

internal sealed interface LegacyArtistTarget {
    val artistId: String
    val artistName: String
    val title: String

    data class Albums(
        override val artistId: String,
        override val artistName: String,
    ) : LegacyArtistTarget {
        override val title: String = artistName
    }

    data class AllSongs(
        override val artistId: String,
        override val artistName: String,
        override val title: String,
    ) : LegacyArtistTarget

    data class Album(
        override val artistId: String,
        override val artistName: String,
        val albumId: String,
        override val title: String,
        val fromArtistAlbums: Boolean,
    ) : LegacyArtistTarget
}

internal fun LegacyArtistTarget.parentTarget(): LegacyArtistTarget? {
    return when (this) {
        is LegacyArtistTarget.Albums -> null
        is LegacyArtistTarget.AllSongs -> LegacyArtistTarget.Albums(
            artistId = artistId,
            artistName = artistName,
        )
        is LegacyArtistTarget.Album -> if (fromArtistAlbums) {
            LegacyArtistTarget.Albums(
                artistId = artistId,
                artistName = artistName,
            )
        } else {
            null
        }
    }
}

internal val LegacyArtistTarget.showsAlbumSwitch: Boolean
    get() = this is LegacyArtistTarget.Albums

@Composable
internal fun LegacyPortArtistPage(
    mediaItems: List<MediaItem>,
    active: Boolean,
    selectedTarget: LegacyArtistTarget?,
    albumViewMode: AlbumViewMode,
    rootPredictiveBackProgress: Float? = null,
    rootPredictiveBackExitConsumed: Boolean = false,
    onRootPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    nestedPredictiveBackProgress: Float? = null,
    nestedPredictiveBackExitConsumed: Boolean = false,
    onNestedPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    hiddenMediaIds: Set<String>,
    onTargetChanged: (LegacyArtistTarget?) -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val visibleSongs = remember(mediaItems, hiddenMediaIds) {
        mediaItems.filterNot { mediaItem -> mediaItem.mediaId in hiddenMediaIds }
    }
    val unknownArtistTitle = stringResource(R.string.unknown_artist)
    val unknownAlbumTitle = stringResource(R.string.unknown_album)
    val allSongsTitle = stringResource(R.string.artist_all_songs)
    val artists = remember(visibleSongs, unknownArtistTitle, unknownAlbumTitle, artistSettings) {
        buildArtistSummaries(
            mediaItems = visibleSongs,
            unknownArtistTitle = unknownArtistTitle,
            unknownAlbumTitle = unknownAlbumTitle,
            artistSettings = artistSettings,
        )
    }
    val selectedArtist = remember(artists, selectedTarget) {
        selectedTarget?.artistId?.let { artistId ->
            artists.firstOrNull { artist -> artist.id == artistId }
        }
    }
    val selectedArtistAlbums = remember(selectedArtist, context, artistSettings) {
        selectedArtist?.albumSummaries(
            context = context,
            artistSettings = artistSettings,
        ).orEmpty()
    }
    val selectedArtistState = remember(selectedArtist, selectedArtistAlbums, selectedTarget) {
        if (selectedArtist != null && selectedTarget != null) {
            val retainedTarget = selectedTarget.parentTarget()?.takeIf { parentTarget ->
                parentTarget.artistId == selectedArtist.id
            } ?: selectedTarget
            LegacySelectedArtistState(
                artist = selectedArtist,
                target = retainedTarget,
                albums = selectedArtistAlbums,
            )
        } else {
            null
        }
    }
    val switchAnimator = remember { LegacyArtistAlbumViewSwitchAnimator() }

    LegacyPortPageStackTransition(
        secondaryKey = selectedArtistState,
        modifier = modifier,
        label = "legacy artist transition",
        predictiveBackProgress = rootPredictiveBackProgress,
        predictiveBackExitConsumed = rootPredictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onRootPredictiveBackExitConsumedReset,
        primaryContent = {
            LegacyPortArtistOverviewPage(
                active = active,
                artists = artists,
                onArtistSelected = { artist ->
                    val albums = artist.albumSummaries(
                        context = context,
                        artistSettings = artistSettings,
                    )
                    val target = if (albums.size > 1) {
                        LegacyArtistTarget.Albums(
                            artistId = artist.id,
                            artistName = artist.name,
                        )
                    } else {
                        val album = albums.firstOrNull()
                        if (album == null) {
                            LegacyArtistTarget.AllSongs(
                                artistId = artist.id,
                                artistName = artist.name,
                                title = allSongsTitle,
                            )
                        } else {
                            LegacyArtistTarget.Album(
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
            LegacyPortSelectedArtistPage(
                artist = state.artist,
                albums = state.albums.ifEmpty {
                    state.artist.albumSummaries(
                        context = context,
                        artistSettings = artistSettings,
                    )
                },
                target = selectedTarget?.takeIf { target -> target.artistId == state.artist.id } ?: state.target,
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
                switchAnimator = switchAnimator,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}
