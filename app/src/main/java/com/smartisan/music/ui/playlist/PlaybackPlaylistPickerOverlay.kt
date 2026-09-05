package com.smartisan.music.ui.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisan.music.R
import com.smartisan.music.data.playlist.UserPlaylistSummary
import com.smartisan.music.ui.components.*

@Composable
internal fun PlaybackPlaylistPickerOverlay(
    visible: Boolean,
    playlists: List<UserPlaylistSummary>,
    onDismiss: () -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    createNewPlaylistVisible: Boolean = true,
    @StringRes titleRes: Int = R.string.playlist_picker_title,
    @StringRes createNewPlaylistTitleRes: Int = R.string.new_playlist,
) {
    SmartisanAnimatedSheet(
        visible,
        onDismiss,
        modifier,
        scrimEasing = androidx.compose.animation.core.FastOutSlowInEasing,
    ) {
        SmartisanMenuTitleBar(stringResource(titleRes), onDismiss)
        val count = playlists.size + if (createNewPlaylistVisible) 1 else 0
        LazyColumn(
            Modifier.fillMaxWidth()
                .height((60 * count.coerceAtLeast(1)).coerceAtMost(420).dp)
                .background(colorResource(R.color.surface_card))
        ) {
            if (createNewPlaylistVisible)
                item(key = "create") {
                    PlaylistChoice(
                        stringResource(createNewPlaylistTitleRes),
                        null,
                        onCreateNewPlaylist,
                    )
                }
            items(playlists, key = { it.id }) { playlist ->
                PlaylistChoice(
                    playlist.name,
                    pluralStringResource(
                        R.plurals.library_playlist_song_count,
                        playlist.songCount,
                        playlist.songCount,
                    ),
                    { onPlaylistSelected(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistChoice(title: String, subtitle: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier.fillMaxWidth()
            .height(60.dp)
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(R.drawable.listview_selector, pressed = pressed)
            )
            .clickable(interaction, null, onClick = smartisanClick(onClick))
    ) {
        Column(
            Modifier.align(Alignment.CenterStart)
                .fillMaxWidth()
                .absolutePadding(left = 18.dp, right = 50.dp)
        ) {
            BasicText(
                title,
                style =
                    TextStyle(
                        color = colorResource(R.color.list_item_first_line),
                        fontSize = 16.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null)
                BasicText(
                    subtitle,
                    style =
                        TextStyle(
                            color = colorResource(R.color.list_item_second_line),
                            fontSize = 13.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        Image(
            rememberSmartisanDrawablePainter(R.drawable.arrow3_selector),
            null,
            Modifier.align(AbsoluteAlignment.CenterRight).absolutePadding(right = 16.dp),
            contentScale = ContentScale.Inside,
        )
        Spacer(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(LocalDensity.current) { 1.toDp() })
                .background(colorResource(R.color.listview_divider_color))
        )
    }
}
