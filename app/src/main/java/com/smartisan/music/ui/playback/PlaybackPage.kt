package com.smartisan.music.ui.playback

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.SessionResult
import com.smartisan.music.R
import com.smartisan.music.data.favorite.FavoriteSongsRepository
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.playback.LocalPlaybackController
import com.smartisan.music.playback.await
import com.smartisan.music.playback.setTrackRating
import com.smartisan.music.ui.components.SmartisanTitleBar
import com.smartisan.music.ui.components.SmartisanTitleBarAction
import com.smartisan.music.ui.shell.titlebar.TitleBarShadow
import com.smartisan.music.ui.songs.songRating
import kotlinx.coroutines.launch

@Composable
internal fun PlaybackPage(
    playbackSettings: PlaybackSettings,
    ratingOverrides: Map<String, Int>,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onTrackRatingChanged: (String, Int) -> Unit,
    onFavoriteToggle: (MediaItem) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current
    val titleState = rememberPlaybackTitleState()
    val favoriteRepository =
        remember(context.applicationContext) {
            FavoriteSongsRepository.getInstance(context.applicationContext)
        }
    val favoriteIds by favoriteRepository.observeFavoriteIds().collectAsState(initial = emptySet())
    var pendingRatingRequests by remember { mutableStateOf(emptyMap<String, Int>()) }
    var queueSnapshot by
        remember(controller, context, favoriteIds, ratingOverrides) {
            mutableStateOf(
                controller.toPlaybackQueueSnapshot(
                    context = context,
                    favoriteIds = favoriteIds,
                    ratingOverrides = ratingOverrides,
                )
            )
        }
    var queueVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val titleContentHeight = dimensionResource(R.dimen.titlebar_height)
    val density = LocalDensity.current
    val statusBarHeight =
        with(density) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val titleTopPadding = statusBarHeight + titleContentHeight

    DisposableEffect(controller, context, favoriteIds, ratingOverrides) {
        val playbackController = controller ?: return@DisposableEffect onDispose {}
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    queueSnapshot =
                        player.toPlaybackQueueSnapshot(
                            context = context,
                            favoriteIds = favoriteIds,
                            ratingOverrides = ratingOverrides,
                        )
                }
            }
        playbackController.addListener(listener)
        queueSnapshot =
            playbackController.toPlaybackQueueSnapshot(
                context = context,
                favoriteIds = favoriteIds,
                ratingOverrides = ratingOverrides,
            )
        onDispose {
            playbackController.removeListener(listener)
        }
    }

    BackHandler(enabled = queueVisible) {
        queueVisible = false
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(colorResource(R.color.page_background))
    ) {
        val screenHeightPx = with(density) { maxHeight.roundToPx() }
        val playerOffsetY by
            animateFloatAsState(
                targetValue = if (queueVisible) screenHeightPx.toFloat() else 0f,
                animationSpec =
                    tween(
                        durationMillis = QueueRevealDurationMillis,
                        easing = { fraction ->
                            val inverse = 1f - fraction
                            1f - inverse * inverse * inverse
                        },
                    ),
                label = "playback queue reveal",
            )

        PlaybackQueueLayer(
            snapshot = queueSnapshot,
            onItemClick = { queueIndex ->
                controller?.seekToDefaultPosition(queueIndex)
            },
            onFavoriteCurrentClick = {
                queueSnapshot.current?.mediaItem?.let(onFavoriteToggle)
            },
            onCurrentRatingChanged = { mediaId, score ->
                if (mediaId.isNotBlank()) {
                    val playbackController = controller
                    val previousScore =
                        queueSnapshot.current
                            ?.takeIf { current -> current.mediaId == mediaId }
                            ?.score ?: ratingOverrides[mediaId] ?: 0
                    if (playbackController != null) {
                        pendingRatingRequests = pendingRatingRequests + (mediaId to score)
                        onTrackRatingChanged(mediaId, score)
                        queueSnapshot = queueSnapshot.withCurrentRating(mediaId, score)
                        scope.launch {
                            val successful = runCatching {
                                playbackController
                                    .setTrackRating(mediaId, score)
                                    .await(context)
                                    .resultCode == SessionResult.RESULT_SUCCESS
                            }
                                .getOrDefault(false)
                            if (pendingRatingRequests[mediaId] != score) {
                                return@launch
                            }
                            pendingRatingRequests = pendingRatingRequests - mediaId
                            if (!successful) {
                                onTrackRatingChanged(mediaId, previousScore)
                                queueSnapshot =
                                    queueSnapshot.withCurrentRating(mediaId, previousScore)
                            }
                        }
                    }
                }
            },
            onClearUpcomingClick = {
                val playbackController = controller
                if (playbackController != null) {
                    val currentIndex = playbackController.currentMediaItemIndex
                    val upcomingIndexes =
                        playbackController
                            .toPlaybackQueueSnapshot(
                                context = context,
                                favoriteIds = favoriteIds,
                                ratingOverrides = ratingOverrides,
                            )
                            .upcoming
                            .map { track -> track.queueIndex }
                            .filter { index -> index >= 0 && index != currentIndex }
                            .distinct()
                            .sortedDescending()
                    upcomingIndexes.forEach { index ->
                        if (index in 0 until playbackController.mediaItemCount) {
                            playbackController.removeMediaItem(index)
                        }
                    }
                }
            },
            onMoveRequest = { fromIndex, toIndex ->
                val playbackController = controller
                if (
                    playbackController != null &&
                        fromIndex != toIndex &&
                        playbackController.canReorderUpcomingQueue
                ) {
                    val itemCount = playbackController.mediaItemCount
                    if (fromIndex in 0 until itemCount && toIndex in 0 until itemCount) {
                        playbackController.moveMediaItem(fromIndex, toIndex)
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(top = statusBarHeight).zIndex(0f),
        )
        Box(
            modifier =
                Modifier.fillMaxSize().zIndex(1f).graphicsLayer {
                    translationY = playerOffsetY
                }
        ) {
            PlaybackScreen(
                playbackSettings = playbackSettings,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onScratchEnabledChange = onScratchEnabledChange,
                onFavoriteToggle = onFavoriteToggle,
                onCollapse = onCollapse,
                showTopBar = false,
                modifier = Modifier.fillMaxSize().padding(top = titleTopPadding),
            )
        }
        TitleBarShadow(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = titleTopPadding)
                    .height(dimensionResource(R.dimen.title_bar_shadow_height))
                    .zIndex(2f)
        )
        PlaybackTitleBar(
            title = titleState.title,
            artist = titleState.artist,
            queueVisible = queueVisible,
            onCollapse = onCollapse,
            onQueueClick = {
                queueVisible = !queueVisible
            },
            modifier = Modifier.align(Alignment.TopCenter).zIndex(3f),
        )
    }
}

@Composable
private fun PlaybackTitleBar(
    title: String,
    artist: String,
    queueVisible: Boolean,
    onCollapse: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartisanTitleBar(
        title = if (queueVisible) stringResource(R.string.playlist_title) else title,
        modifier = modifier,
        showShadow = false,
        contentHeight = dimensionResource(R.dimen.titlebar_height),
        navigationIcon =
            SmartisanTitleBarAction(
                R.drawable.btn_current_playing_back_selector,
                stringResource(R.string.playback_left_btn_content_description),
                onCollapse,
            ),
        action =
            SmartisanTitleBarAction(
                R.drawable.btn_current_playing_check_selector,
                stringResource(
                    if (queueVisible) R.string.playqueue_btn_hide_content_description
                    else R.string.playqueue_btn_show_content_description
                ),
                onQueueClick,
            ),
        centerContent =
            if (queueVisible) null
            else {
                { PlaybackCenterTitle(title, artist) }
            },
    )
}

@Composable
private fun PlaybackCenterTitle(title: String, artist: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // The previous horizontally-scrolling TextView sizes against a very wide area:
        // its 16..20sp auto-size range therefore stays at 20sp before marquee starts.
        BasicText(
            title,
            Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
            style =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.title_color),
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
        )
        if (artist.isNotBlank())
            BasicText(
                artist,
                Modifier.fillMaxWidth(),
                style =
                    TextStyle(
                        fontSize = 10.sp,
                        color = colorResource(R.color.sub_title_text_color),
                        textAlign = TextAlign.Center,
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
    }
}

@Composable
private fun rememberPlaybackTitleState(): PlaybackTitleState {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current
    var titleState by
        remember(controller, context) {
            mutableStateOf(controller.toPlaybackTitleState(context))
        }

    DisposableEffect(controller, context) {
        val playbackController = controller ?: return@DisposableEffect onDispose {}
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    titleState = player.toPlaybackTitleState(context)
                }
            }
        playbackController.addListener(listener)
        titleState = playbackController.toPlaybackTitleState(context)
        onDispose {
            playbackController.removeListener(listener)
        }
    }

    return titleState
}

private data class PlaybackTitleState(
    val title: String,
    val artist: String,
)

private fun Player?.toPlaybackTitleState(context: Context): PlaybackTitleState {
    val metadata = this?.currentMediaItem?.mediaMetadata
    val title =
        metadata?.displayTitle?.toString()?.takeIf(String::isNotBlank)
            ?: metadata?.title?.toString()?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.unknown_song_title)
    val artist =
        metadata?.artist?.toString()?.takeIf {
            it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
        }
            ?: metadata?.albumArtist?.toString()?.takeIf {
                it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
            }
            ?: ""
    return PlaybackTitleState(title = title, artist = artist)
}

private fun Player?.toPlaybackQueueSnapshot(
    context: Context,
    favoriteIds: Set<String>,
    ratingOverrides: Map<String, Int>,
): PlaybackQueueSnapshot {
    val player = this ?: return PlaybackQueueSnapshot()
    val itemCount = player.mediaItemCount
    if (itemCount <= 0) {
        val current =
            player.currentMediaItem?.toPlaybackQueueTrack(
                context = context,
                queueIndex = 0,
                ratingOverrides = ratingOverrides,
            )
        return PlaybackQueueSnapshot(
            current = current,
            isCurrentFavorite = current?.mediaId?.let(favoriteIds::contains) == true,
        )
    }
    val currentIndex = player.currentMediaItemIndex.takeIf { it in 0 until itemCount } ?: -1
    val tracks =
        (0 until itemCount).mapNotNull { index ->
            runCatching {
                player
                    .getMediaItemAt(index)
                    .toPlaybackQueueTrack(
                        context = context,
                        queueIndex = index,
                        ratingOverrides = ratingOverrides,
                    )
            }
                .getOrNull()
        }
    val current =
        tracks.firstOrNull { it.queueIndex == currentIndex }
            ?: player.currentMediaItem?.toPlaybackQueueTrack(
                context = context,
                queueIndex = currentIndex.coerceAtLeast(0),
                ratingOverrides = ratingOverrides,
            )
    return PlaybackQueueSnapshot(
        history = tracks.filter { it.queueIndex < currentIndex }.takeLast(QueueHistoryLimit),
        current = current,
        upcoming =
            player.toUpcomingQueueTracks(
                context = context,
                currentIndex = currentIndex,
                ratingOverrides = ratingOverrides,
            ),
        isCurrentFavorite = current?.mediaId?.let(favoriteIds::contains) == true,
        reorderEnabled = player.canReorderUpcomingQueue,
    )
}

private val Player.canReorderUpcomingQueue: Boolean
    get() = !shuffleModeEnabled && repeatMode != Player.REPEAT_MODE_ALL

private fun Player.toUpcomingQueueTracks(
    context: Context,
    currentIndex: Int,
    ratingOverrides: Map<String, Int>,
): List<PlaybackQueueTrack> {
    if (currentIndex !in 0 until mediaItemCount || mediaItemCount <= 1 || currentTimeline.isEmpty) {
        return emptyList()
    }

    val timeline = currentTimeline
    val effectiveRepeatMode =
        repeatMode.takeUnless { it == Player.REPEAT_MODE_ONE } ?: Player.REPEAT_MODE_OFF
    val visitedIndexes = mutableSetOf(currentIndex)
    return buildList {
        var nextIndex =
            timeline.getNextWindowIndex(
                currentIndex,
                effectiveRepeatMode,
                shuffleModeEnabled,
            )
        while (
            nextIndex != C.INDEX_UNSET &&
                nextIndex in 0 until mediaItemCount &&
                nextIndex !in visitedIndexes
        ) {
            visitedIndexes += nextIndex
            val track = runCatching {
                getMediaItemAt(nextIndex)
                    .toPlaybackQueueTrack(
                        context = context,
                        queueIndex = nextIndex,
                        ratingOverrides = ratingOverrides,
                    )
            }
                .getOrNull()
            if (track != null) {
                add(track)
            }
            nextIndex =
                timeline.getNextWindowIndex(
                    nextIndex,
                    effectiveRepeatMode,
                    shuffleModeEnabled,
                )
        }
    }
}

private fun MediaItem.toPlaybackQueueTrack(
    context: Context,
    queueIndex: Int,
    ratingOverrides: Map<String, Int>,
): PlaybackQueueTrack {
    val metadata = mediaMetadata
    val title =
        metadata.displayTitle?.toString()?.takeIf(String::isNotBlank)
            ?: metadata.title?.toString()?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.unknown_song_title)
    val artist =
        metadata.artist?.toString()?.takeIf {
            it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
        }
            ?: metadata.albumArtist?.toString()?.takeIf {
                it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
            }
            ?: ""
    return PlaybackQueueTrack(
        queueIndex = queueIndex,
        mediaId = mediaId,
        title = title,
        artist = artist,
        score = ratingOverrides[mediaId] ?: songRating().toInt(),
        mediaItem = this,
    )
}

private fun PlaybackQueueSnapshot.withCurrentRating(
    mediaId: String,
    score: Int,
): PlaybackQueueSnapshot {
    val currentTrack = current ?: return this
    if (currentTrack.mediaId != mediaId) {
        return this
    }
    return copy(current = currentTrack.copy(score = score.coerceIn(0, 5)))
}

private const val QueueRevealDurationMillis = 300
private const val QueueHistoryLimit = 2
