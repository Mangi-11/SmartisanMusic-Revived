package com.smartisan.music.ui.album

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.artist.artistNormalizedKey
import com.smartisan.music.ui.artist.toArtistDisplayNames
import com.smartisan.music.ui.artwork.AlbumArtworkBrowserOverlay
import com.smartisan.music.ui.artwork.AlbumArtworkBrowserState
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.components.smartisanTextSize
import com.smartisan.music.ui.library.LibraryDivider
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.LibraryIconButton
import com.smartisan.music.ui.library.SmartisanAlbumArtwork
import com.smartisan.music.ui.library.SmartisanAlbumTrackRow
import com.smartisan.music.ui.library.libraryTexture
import com.smartisan.music.ui.library.rememberAlbumArtworkLoader
import com.smartisan.music.ui.songs.rememberSongPlaybackState

@Composable
internal fun AlbumDetailPage(
    album: AlbumSummary,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    artistSettings: ArtistSettings = ArtistSettings(),
) {
    val browser = LocalPlaybackBrowser.current
    val playback = rememberSongPlaybackState()
    var artworkBrowserState by remember { mutableStateOf<AlbumArtworkBrowserState?>(null) }
    var coverVisible by remember { mutableStateOf(true) }
    val showArtists =
        remember(album.songs, artistSettings) { album.songs.hasMultipleArtists(artistSettings) }
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().libraryTexture()) {
            item(key = "album-header") {
                AlbumDetailHeader(
                    album,
                    coverVisible,
                    onCoverClick = { bounds ->
                        artworkBrowserState =
                            AlbumArtworkBrowserState(album, bounds) { coverVisible = it }
                    },
                    onPlay = { browser.replaceQueueAndPlay(album.songs) },
                    onShuffle = { browser.replaceQueueAndPlayShuffled(album.songs) },
                    onAddToPlaylist = { onRequestAddToPlaylist(album.songs) },
                    onAddToQueue = { onRequestAddToQueue(album.songs) },
                )
            }
            itemsIndexed(album.songs, key = { index, item -> "${item.mediaId}:$index" }) {
                index,
                item ->
                SmartisanAlbumTrackRow(
                    item = item,
                    playback = playback,
                    onClick = { browser.replaceQueueAndPlay(album.songs, index) },
                    onMoreClick = onTrackMoreClick,
                    trackNumber = item.displayTrackNumber(),
                    showArtist = showArtists,
                    artist = item.albumDetailArtistText(artistSettings),
                )
                LibraryDivider()
            }
            item(key = "album-track-footer") {
                LibraryFooter(R.plurals.track_count, album.songs.size)
            }
        }
        AlbumArtworkBrowserOverlay(
            artworkBrowserState,
            { artworkBrowserState = null },
            Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AlbumDetailHeader(
    album: AlbumSummary,
    coverVisible: Boolean,
    onCoverClick: (Rect?) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val loader = rememberAlbumArtworkLoader()
    val coverSize = dimensionResource(R.dimen.gridview_item_ccontainer_height)
    val coverPx = with(LocalDensity.current) { coverSize.roundToPx() }
    val left = dimensionResource(R.dimen.gridview_margin)
    val enabled = album.songs.isNotEmpty()
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }
    Column(Modifier.fillMaxWidth().libraryTexture(R.drawable.ablum_crosstexture_bg)) {
        Box(Modifier.fillMaxWidth().height(dimensionResource(R.dimen.album_header_height))) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .padding(start = left)
                    .size(coverSize)
                    .onGloballyPositioned { sourceBounds = it.boundsInWindow() }
                    .clickable(
                        remember { MutableInteractionSource() },
                        null,
                        onClick = smartisanClick { onCoverClick(sourceBounds) },
                    )
            ) {
                SmartisanAlbumArtwork(
                    album,
                    coverPx,
                    R.drawable.noalbumcover_220,
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = if (coverVisible) 1f else 0f }
                        .padding(dimensionResource(R.dimen.gridview_padding)),
                    loader,
                )
                Image(
                    rememberSmartisanDrawablePainter(R.drawable.mask_albumcover),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
            Column(
                Modifier.padding(
                    start = left + coverSize + dimensionResource(R.dimen.alum_tile_paddingleft),
                    top = dimensionResource(R.dimen.album_detail_text_margin_top),
                )
            ) {
                AlbumHeaderText(
                    album.title,
                    R.dimen.album_detail_album_name_size,
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                AlbumHeaderText(
                    album.artist,
                    R.dimen.album_detail_artist_size,
                    Modifier.padding(
                        top = dimensionResource(R.dimen.album_detail_text_margin_top1)
                    ),
                )
                AlbumHeaderText(
                    album.year?.toString().orEmpty(),
                    R.dimen.audio_normal_text_size,
                    Modifier.padding(
                        top = dimensionResource(R.dimen.album_detail_text_margin_top2)
                    ),
                )
            }
            Row(
                Modifier.align(Alignment.BottomStart)
                    .padding(
                        start = left + coverSize,
                        end = dimensionResource(R.dimen.album_opration_zone_padding_right),
                        bottom = dimensionResource(R.dimen.album_opration_zone_padding_bottom),
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(dimensionResource(R.dimen.btn_margin_right)),
            ) {
                LibraryIconButton(
                    R.drawable.album_btn_add_to_playlist_selector,
                    stringResource(R.string.add_to_playlist),
                    onAddToPlaylist,
                    enabled = enabled,
                )
                LibraryIconButton(
                    R.drawable.album_btn_add_to_queue_selector,
                    stringResource(R.string.add_to_queue),
                    onAddToQueue,
                    enabled = enabled,
                )
            }
        }
        LibraryDivider()
        Row(
            Modifier.fillMaxWidth()
                .height(45.dp)
                .background(colorResource(R.color.page_background)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumHeaderAction(
                stringResource(R.string.play_all),
                R.drawable.btn_play_all_selector,
                enabled,
                onPlay,
                Modifier.weight(1f),
            )
            Image(
                rememberSmartisanDrawablePainter(R.drawable.line_between),
                null,
                Modifier.width(dimensionResource(R.dimen.album_play_type_divider_size))
                    .fillMaxHeight(),
                contentScale = ContentScale.FillBounds,
            )
            AlbumHeaderAction(
                stringResource(R.string.play_shuffle),
                R.drawable.btn_shuffle3_selector,
                enabled,
                onShuffle,
                Modifier.weight(1f),
            )
        }
        LibraryDivider()
    }
}

@Composable
private fun AlbumHeaderText(text: String, size: Int, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier.fillMaxWidth(),
        style =
            TextStyle(
                color = colorResource(R.color.title_text_color),
                fontSize = smartisanTextSize(size),
                platformStyle = PlatformTextStyle(includeFontPadding = true),
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AlbumHeaderAction(
    text: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.fillMaxHeight().graphicsLayer { alpha = if (enabled) 1f else .3f },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clickable(
                interaction,
                null,
                enabled = enabled,
                role = Role.Button,
                onClick = smartisanClick(onClick),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                rememberSmartisanDrawablePainter(icon, enabled = enabled, pressed = pressed),
                null,
                Modifier.padding(end = 7.dp),
            )
            BasicText(
                text,
                style =
                    TextStyle(
                        color =
                            smartisanStateColor(
                                R.drawable.text_color_album_action_selector,
                                enabled = enabled,
                                pressed = pressed,
                            ),
                        fontSize = smartisanTextSize(R.dimen.button_text_size),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
            )
        }
    }
}

private fun List<MediaItem>.hasMultipleArtists(artistSettings: ArtistSettings): Boolean =
    flatMap { it.mediaMetadata.artist.toArtistDisplayNames(artistSettings, "") }
        .filter(String::isNotBlank)
        .distinctBy { it.artistNormalizedKey() }
        .size > 1

internal fun MediaItem.albumDetailArtistText(artistSettings: ArtistSettings): String =
    mediaMetadata.artist
        .toArtistDisplayNames(artistSettings, "")
        .filter(String::isNotBlank)
        .joinToString(" / ")
        .takeIf(String::isNotBlank)
        ?: mediaMetadata.albumArtist?.toString()?.trim()?.takeIf(String::isNotEmpty)
        ?: ""
