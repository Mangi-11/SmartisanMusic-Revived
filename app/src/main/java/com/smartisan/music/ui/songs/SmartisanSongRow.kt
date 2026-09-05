package com.smartisan.music.ui.songs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.isPlaybackActiveForUi
import com.smartisan.music.ui.components.*

internal data class SongPlaybackState(val mediaId: String?, val playing: Boolean)

@Composable
internal fun rememberSongPlaybackState(
    browser: Player? = LocalPlaybackBrowser.current
): SongPlaybackState {
    var state by
        remember(browser) {
            mutableStateOf(
                SongPlaybackState(
                    browser?.currentMediaItem?.mediaId,
                    browser?.isPlaybackActiveForUi() == true,
                )
            )
        }
    DisposableEffect(browser) {
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    state =
                        SongPlaybackState(
                            player.currentMediaItem?.mediaId,
                            player.isPlaybackActiveForUi(),
                        )
                }
            }
        browser?.addListener(listener)
        onDispose { browser?.removeListener(listener) }
    }
    return state
}

/** One row contract for song destinations; the service remains the playback state owner. */
@Composable
internal fun SmartisanSongRow(
    item: MediaItem,
    onClick: () -> Unit,
    onMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    selected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit = {},
    displayMode: SongSortDisplayMode = SongSortDisplayMode.Name,
    durationText: String? = null,
    contentEndInset: androidx.compose.ui.unit.Dp = 0.dp,
    subtitle: String? = null,
    playback: SongPlaybackState = rememberSongPlaybackState(),
    onCheckboxBoundsChanged: (Rect?) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by
        animateFloatAsState(
            if (editMode) 1f else 0f,
            tween(200, easing = SmartisanEaseInOut),
            label = "songEdit",
        )
    val checkbox =
        rememberSmartisanDrawablePainter(R.drawable.check_box_selector, checked = selected)
    val density = LocalDensity.current
    val checkWidth = with(density) { checkbox.intrinsicSize.width.toDp() }
    val checkInset = dimensionResource(R.dimen.check_box_margin_left)
    val metadata = item.mediaMetadata
    val title =
        metadata.displayTitle?.toString()
            ?: metadata.title?.toString()
            ?: stringResource(R.string.unknown_song_title)
    val artist =
        metadata.artist?.toString()
            ?: metadata.subtitle?.toString()
            ?: stringResource(R.string.unknown_artist)
    val album = metadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
    val secondary =
        subtitle
            ?: if (displayMode == SongSortDisplayMode.Score || album == null) artist
            else "$artist - $album"
    val textDirection =
        if (
            androidx.compose.ui.platform.LocalLayoutDirection.current ==
                androidx.compose.ui.unit.LayoutDirection.Rtl
        )
            androidx.compose.ui.text.style.TextDirection.ContentOrRtl
        else androidx.compose.ui.text.style.TextDirection.ContentOrLtr
    val playing = !editMode && item.mediaId == playback.mediaId
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Row(
            modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.listview_item_height))
                .clipToBounds()
                .smartisanPainterBackground(
                    rememberSmartisanDrawablePainter(
                        R.drawable.listview_selector,
                        pressed = pressed,
                    )
                )
                .semantics { if (editMode) this.selected = selected }
                .clickable(
                    interaction,
                    null,
                    role = if (editMode) Role.Checkbox else Role.Button,
                    onClick =
                        smartisanClick {
                            if (editMode) onSelectionChange(!selected) else onClick()
                        },
                )
                .padding(end = contentEndInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width((checkWidth + checkInset) * progress).fillMaxHeight()) {
                if (progress > 0f)
                    Image(
                        checkbox,
                        null,
                        Modifier.align(Alignment.CenterEnd)
                            .requiredWidth(checkWidth)
                            .graphicsLayer { alpha = progress }
                            .smartisanCheckboxBounds(onCheckboxBoundsChanged),
                    )
            }
            Spacer(Modifier.width(dimensionResource(R.dimen.listview_items_margin_left)))
            Column(Modifier.weight(1f)) {
                SmartisanPlayingTitle(
                    title,
                    playing,
                    playback.playing,
                    smartisanTextSize(R.dimen.text_size_large),
                    textDirection = textDirection,
                )
                Row(
                    Modifier.padding(
                        top = dimensionResource(R.dimen.listview_item_line_two_paddingtop)
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.qualityBadge()?.let { badge ->
                        Image(
                            rememberSmartisanDrawablePainter(badge),
                            null,
                            Modifier.padding(
                                end = dimensionResource(R.dimen.album_song_index_margin_left)
                            ),
                        )
                    }
                    BasicText(
                        secondary,
                        style =
                            TextStyle(
                                textDirection = textDirection,
                                color = colorResource(R.color.list_text_color_small),
                                fontSize = smartisanTextSize(R.dimen.text_size_micro),
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (displayMode == SongSortDisplayMode.Score) {
                Spacer(Modifier.width(dimensionResource(R.dimen.quick_context_line_height)))
                SongRating(item.songRating().toInt())
                Spacer(Modifier.width(30.dp))
            } else if (displayMode == SongSortDisplayMode.PlayCount) {
                BasicText(
                    item.songPlayCount().toString(),
                    Modifier.padding(start = 18.dp),
                    style =
                        TextStyle(
                            color = colorResource(R.color.list_text_color_small),
                            fontSize = smartisanTextSize(R.dimen.text_size_micro),
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                )
            }
            if (durationText != null)
                BasicText(
                    durationText,
                    Modifier.padding(horizontal = 4.dp),
                    style =
                        TextStyle(
                            color = colorResource(R.color.list_text_color_small),
                            fontSize = smartisanTextSize(R.dimen.text_size_micro),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                )
            if (progress < 1f) {
                val moreInteraction = remember { MutableInteractionSource() }
                val morePressed by moreInteraction.collectIsPressedAsState()
                Image(
                    rememberSmartisanDrawablePainter(
                        R.drawable.btn_more_selector,
                        pressed = pressed || morePressed,
                    ),
                    stringResource(R.string.tab_more),
                    Modifier.graphicsLayer { alpha = 1f - progress }
                        .clickable(
                            moreInteraction,
                            null,
                            enabled = !editMode,
                            role = Role.Button,
                            onClick = smartisanClick { onMoreClick(item) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun SongRating(rating: Int) {
    Box(Modifier.height(14.dp)) {
        Image(
            rememberSmartisanDrawablePainter(R.drawable.song_score_empty),
            null,
            Modifier.fillMaxHeight(),
        )
        Image(
            rememberSmartisanDrawablePainter(R.drawable.song_score_full),
            null,
            Modifier.fillMaxHeight().drawWithContent {
                clipRect(right = size.width * rating.coerceIn(0, 5) / 5f) {
                    this@drawWithContent.drawContent()
                }
            },
        )
    }
}

internal fun MediaItem.qualityBadge(): Int? =
    when (mediaMetadata.extras?.getString(LocalAudioLibrary.AudioQualityBadgeExtraKey)) {
        "flac" -> R.drawable.audio_quality_flac
        "ape" -> R.drawable.audio_quality_ape
        "wav" -> R.drawable.audio_quality_wav
        "aiff" -> R.drawable.audio_quality_aiff
        "alac" -> R.drawable.audio_quality_alac
        "cue" -> R.drawable.audio_quality_cue
        else -> null
    }

@Composable
internal fun SmartisanPlayingTitle(
    title: String,
    playing: Boolean,
    isPlaying: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    includeFontPadding: Boolean = true,
    textDirection: androidx.compose.ui.text.style.TextDirection =
        androidx.compose.ui.text.style.TextDirection.Content,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            title,
            Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE),
            style =
                TextStyle(
                    textDirection = textDirection,
                    color = colorResource(R.color.setting_item_text_color),
                    fontSize = fontSize,
                    platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
                ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (playing)
            Image(
                rememberSmartisanDrawablePainter(
                    if (isPlaying) R.drawable.playing_blueplay2_selector
                    else R.drawable.playing_bluepause_selector
                ),
                null,
                Modifier.padding(start = 10.dp),
            )
    }
}
