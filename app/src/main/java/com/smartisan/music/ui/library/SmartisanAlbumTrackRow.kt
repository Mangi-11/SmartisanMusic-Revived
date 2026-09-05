package com.smartisan.music.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.components.smartisanTextSize
import com.smartisan.music.ui.songs.SmartisanPlayingTitle
import com.smartisan.music.ui.songs.SongPlaybackState
import java.util.Locale

/** The album row has a track index and duration; its geometry differs from the songs tab. */
@Composable
internal fun SmartisanAlbumTrackRow(
    item: MediaItem,
    trackNumber: String,
    artist: String,
    showArtist: Boolean,
    playback: SongPlaybackState,
    onClick: () -> Unit,
    onMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val metadata = item.mediaMetadata
    val rowHeight = dimensionResource(R.dimen.listview_item_height)
    Row(
        modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clipToBounds()
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(R.drawable.listview_selector, pressed = pressed)
            )
            .clickable(interaction, null, role = Role.Button, onClick = smartisanClick(onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            trackNumber,
            Modifier.padding(start = dimensionResource(R.dimen.album_song_index_margin_left))
                .width(dimensionResource(R.dimen.album_song_index_width)),
            style =
                TextStyle(
                    color =
                        smartisanStateColor(
                            R.drawable.song_index_color_selector,
                            pressed = pressed,
                        ),
                    fontSize = smartisanTextSize(R.dimen.text_size_large),
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            maxLines = 1,
        )
        Column(
            Modifier.weight(1f).padding(start = dimensionResource(R.dimen.album_text_padding_left))
        ) {
            SmartisanPlayingTitle(
                title = (metadata.displayTitle ?: metadata.title)?.toString().orEmpty(),
                playing = item.mediaId == playback.mediaId,
                isPlaying = playback.playing,
                fontSize = smartisanTextSize(R.dimen.text_size_medium),
                modifier = Modifier.fillMaxWidth(),
            )
            if (showArtist)
                BasicText(
                    artist,
                    style =
                        TextStyle(
                            color = colorResource(R.color.list_text_color_small),
                            fontSize = smartisanTextSize(R.dimen.text_size_micro),
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        BasicText(
            metadata.durationMs?.let(::libraryDuration).orEmpty(),
            Modifier.padding(end = dimensionResource(R.dimen.listview_items_margin_right)),
            style =
                TextStyle(
                    color =
                        smartisanStateColor(
                            R.drawable.text_color_white_and_gray1_selector,
                            pressed = pressed,
                        ),
                    fontSize = smartisanTextSize(R.dimen.text_size_micro),
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
        )
        val moreInteraction = remember { MutableInteractionSource() }
        val morePressed by moreInteraction.collectIsPressedAsState()
        Image(
            rememberSmartisanDrawablePainter(
                R.drawable.btn_more_selector,
                pressed = pressed || morePressed,
            ),
            stringResource(R.string.tab_more),
            Modifier.padding(end = dimensionResource(R.dimen.listview_items_margin_right))
                .size(rowHeight)
                .clickable(
                    moreInteraction,
                    null,
                    role = Role.Button,
                    onClick = smartisanClick { onMoreClick(item) },
                ),
            contentScale = ContentScale.None,
        )
    }
}

internal fun libraryDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / 1000
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
