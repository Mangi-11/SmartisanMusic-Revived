package com.smartisan.music.ui.shell.playback

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.isExternalAudioLaunchItem
import com.smartisan.music.playback.NowPlayingArtworkRepository
import com.smartisan.music.ui.components.SmartisanTouchShield
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanTextSize

@Composable
internal fun PlaybackBar(
    snapshot: PlaybackBarSnapshot,
    shown: Boolean,
    favoriteIds: Set<String>,
    artworkBitmap: Bitmap?,
    onHidden: () -> Unit,
    onOpenPlayback: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    bottomDividerVisible: Boolean = true,
) {
    val offset = remember { Animatable(1f) }
    val latestOnHidden = rememberUpdatedState(onHidden)
    // A cancelling show/hide effect never delivers the preceding hide's completion callback.
    LaunchedEffect(shown) {
        if (!shown && offset.value == 1f) {
            latestOnHidden.value()
            return@LaunchedEffect
        }
        offset.animateTo(
            if (shown) 0f else 1f,
            tween(
                300,
                easing = {
                    val inverse = it - 1f
                    inverse * inverse * inverse + 1f
                },
            ),
        )
        if (!shown) latestOnHidden.value()
    }
    var retainedItem by remember { mutableStateOf(snapshot.mediaItem) }
    SideEffect { snapshot.mediaItem?.let { retainedItem = it } }
    val mediaItem = snapshot.mediaItem ?: retainedItem
    val active = shown || offset.value < 1f
    val configuration = LocalViewConfiguration.current
    val exactTargets =
        remember(configuration) {
            object : ViewConfiguration by configuration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
        }
    Box(modifier.clipToBounds()) {
        if (active && mediaItem != null) {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalViewConfiguration provides exactTargets,
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationY = size.height * offset.value
                    }
                ) {
                    SmartisanTouchShield()
                    Column(Modifier.fillMaxSize()) {
                        Image(
                            rememberSmartisanDrawablePainter(R.drawable.now_playing_bar_shadow),
                            null,
                            Modifier.fillMaxWidth().height(6.dp),
                            contentScale = ContentScale.FillBounds,
                        )
                        Column(
                            Modifier.fillMaxWidth()
                                .height(dimensionResource(R.dimen.play_back_content_height))
                                .background(colorResource(R.color.surface_card))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val coverPadding =
                                    dimensionResource(R.dimen.float_cover_padding_left)
                                val coverSize =
                                    dimensionResource(R.dimen.daily_recommend_cover_width)
                                val open = smartisanClick(onOpenPlayback)
                                Box(
                                    Modifier.padding(start = coverPadding)
                                        .size(coverSize)
                                        .clickable(
                                            remember { MutableInteractionSource() },
                                            null,
                                            onClick = open,
                                        )
                                ) {
                                    if (artworkBitmap != null) {
                                        Image(
                                            artworkBitmap.asImageBitmap(),
                                            null,
                                            Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Inside,
                                        )
                                    } else {
                                        Image(
                                            rememberSmartisanDrawablePainter(
                                                R.drawable.noalbumcover_220
                                            ),
                                            null,
                                            Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Inside,
                                        )
                                    }
                                }
                                val metadata = mediaItem.mediaMetadata
                                val title =
                                    metadata.displayTitle?.toString()
                                        ?: metadata.title?.toString()
                                        ?: stringResource(R.string.unknown_song_title)
                                val artist =
                                    if (snapshot.isPlaybackBuffering)
                                        stringResource(R.string.playback_buffering)
                                    else
                                        metadata.subtitle?.toString()
                                            ?: metadata.artist?.toString()
                                            ?: stringResource(R.string.unknown_artist)
                                Box(
                                    Modifier.weight(1f)
                                        .padding(start = coverPadding)
                                        .clipToBounds()
                                        .clickable(
                                            remember { MutableInteractionSource() },
                                            null,
                                            onClick = open,
                                        ),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Column(
                                        Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                                            .width(
                                                dimensionResource(R.dimen.playback_bar_text_width)
                                            )
                                            .padding(end = coverPadding)
                                    ) {
                                        BasicText(
                                            title,
                                            Modifier.fillMaxWidth()
                                                .basicMarquee(iterations = Int.MAX_VALUE),
                                            style =
                                                TextStyle(
                                                    fontSize =
                                                        smartisanTextSize(R.dimen.text_size_medium),
                                                    color =
                                                        colorResource(R.color.list_item_first_line),
                                                    platformStyle =
                                                        PlatformTextStyle(
                                                            includeFontPadding = true
                                                        ),
                                                ),
                                            maxLines = 1,
                                        )
                                        BasicText(
                                            artist,
                                            Modifier.fillMaxWidth()
                                                .padding(
                                                    top =
                                                        dimensionResource(
                                                            R.dimen.listview_items_margin_top1
                                                        )
                                                ),
                                            style =
                                                TextStyle(
                                                    fontSize =
                                                        smartisanTextSize(R.dimen.text_size_small),
                                                    color =
                                                        colorResource(
                                                            R.color.list_item_second_line
                                                        ),
                                                    platformStyle =
                                                        PlatformTextStyle(
                                                            includeFontPadding = true
                                                        ),
                                                ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                val gap = dimensionResource(R.dimen.float_button_margin)
                                Row(
                                    Modifier.padding(end = gap).fillMaxHeight(),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val favorite =
                                        mediaItem.mediaId in favoriteIds &&
                                            !mediaItem.isExternalAudioLaunchItem()
                                    PlaybackBarButton(
                                        if (favorite) R.drawable.float_favor_cancel_selector
                                        else R.drawable.float_favor_add_selector,
                                        stringResource(
                                            if (favorite) R.string.cancel_love else R.string.love
                                        ),
                                        { onToggleFavorite(mediaItem) },
                                        enabled = !mediaItem.isExternalAudioLaunchItem(),
                                    )
                                    PlaybackBarButton(
                                        R.drawable.float_btn_prev_selector,
                                        stringResource(R.string.previous_song),
                                        onPrevious,
                                    )
                                    PlaybackBarButton(
                                        if (snapshot.isPlaybackActive)
                                            R.drawable.float_btn_pause_selector
                                        else R.drawable.float_btn_play_selector,
                                        stringResource(
                                            if (snapshot.isPlaybackActive) R.string.pause
                                            else R.string.play
                                        ),
                                        onPlayPause,
                                    )
                                    PlaybackBarButton(
                                        R.drawable.float_btn_next_selector,
                                        stringResource(R.string.next_song),
                                        onNext,
                                    )
                                }
                            }
                            if (bottomDividerVisible)
                                Spacer(
                                    Modifier.fillMaxWidth()
                                        .height(dimensionResource(R.dimen.nav_divider_height))
                                        .background(colorResource(R.color.nav_list_line))
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackBarButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Image(
        rememberSmartisanDrawablePainter(icon, enabled = enabled, pressed = pressed),
        description,
        Modifier.clickable(
            interaction,
            null,
            enabled = enabled,
            role = Role.Button,
            onClick = smartisanClick(onClick),
        ),
    )
}

internal data class PlaybackBarSnapshot(
    val mediaItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
) {
    val isPlaybackActive: Boolean
        get() = isPlaying || (playWhenReady && isBuffering)

    val isPlaybackBuffering: Boolean
        get() = playWhenReady && isBuffering
}

internal fun Player?.playbackBarSnapshot(): PlaybackBarSnapshot {
    val player = this ?: return PlaybackBarSnapshot()
    return PlaybackBarSnapshot(
        mediaItem = player.currentMediaItem,
        isPlaying = player.isPlaying,
        playWhenReady = player.playWhenReady,
        isBuffering = player.playbackState == Player.STATE_BUFFERING,
    )
}

internal suspend fun loadArtworkBitmap(
    context: android.content.Context,
    mediaItem: MediaItem,
): Bitmap? = NowPlayingArtworkRepository.load(context, mediaItem, PlaybackBarArtworkDecodeSize)

internal fun peekArtworkBitmap(mediaItem: MediaItem): Bitmap? =
    NowPlayingArtworkRepository.peek(mediaItem, PlaybackBarArtworkDecodeSize)

private val PlaybackBarArtworkDecodeSize = Size(128, 128)
