package com.smartisan.music.ui.playlist

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import com.smartisan.music.data.playlist.UserPlaylistSummary
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.library.LibraryBlank
import com.smartisan.music.ui.library.LibraryDivider
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.libraryListEntrance
import com.smartisan.music.ui.library.rememberLibraryListEntrance

@Composable
internal fun PlaylistRootPage(
    active: Boolean,
    playlists: List<UserPlaylistSummary>,
    editMode: Boolean,
    selectedPlaylistIds: Set<String>,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (UserPlaylistSummary) -> Unit,
    onPlaylistClick: (UserPlaylistSummary) -> Unit,
    onPlaylistSelectionChange: (UserPlaylistSummary, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val checkboxLeft =
        with(density) { dimensionResource(R.dimen.play_list_check_box_margin_left).toPx() }
    val checkboxWidth =
        rememberSmartisanDrawablePainter(R.drawable.check_box_selector).intrinsicSize.width
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val entrance =
        rememberLibraryListEntrance(playlists, active) {
            listState.layoutInfo.visibleItemsInfo.size
        }
    Box(
        modifier
            .fillMaxSize()
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(R.drawable.account_background)
            )
    ) {
        if (active)
            Column(Modifier.fillMaxSize()) {
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Row(
                    Modifier.fillMaxWidth()
                        .height(60.dp)
                        .graphicsLayer { alpha = if (editMode) 0.35f else 1f }
                        .smartisanPainterBackground(
                            rememberSmartisanDrawablePainter(
                                R.drawable.list_header_selector,
                                enabled = !editMode,
                                pressed = pressed,
                            )
                        )
                        .clickable(
                            source,
                            null,
                            enabled = !editMode,
                            onClick = smartisanClick(onCreatePlaylist),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        rememberSmartisanDrawablePainter(R.drawable.add_icon_selector),
                        null,
                        Modifier.width(60.dp).fillMaxHeight(),
                        contentScale = ContentScale.None,
                    )
                    BasicText(
                        stringResource(R.string.new_playlist),
                        style =
                            TextStyle(
                                color = colorResource(R.color.list_item_first_line),
                                fontSize = smartisanTextSize(R.dimen.text_size_yun),
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                    )
                }
                if (playlists.isEmpty())
                    LibraryBlank(
                        R.drawable.blank_playlist,
                        stringResource(R.string.no_playlist),
                        stringResource(R.string.create_playlist),
                        Modifier.weight(1f),
                    )
                else
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier.fillMaxWidth()
                                .weight(1f)
                                .smartisanSlideSelection(
                                    editMode,
                                    itemAt = { point ->
                                        listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull {
                                                point.y >= it.offset &&
                                                    point.y < it.offset + it.size
                                            }
                                            ?.index
                                    },
                                    edgeItemAt = { top ->
                                        listState.layoutInfo.visibleItemsInfo
                                            .filter { it.index < playlists.size }
                                            .let {
                                                if (top) it.firstOrNull()?.index
                                                else it.lastOrNull()?.index
                                            }
                                    },
                                    keyAt = { playlists.getOrNull(it)?.id },
                                    selectedKeys = selectedPlaylistIds,
                                    onSelectionChange = { id, selected ->
                                        playlists
                                            .firstOrNull { it.id == id }
                                            ?.let { onPlaylistSelectionChange(it, selected) }
                                    },
                                    canStart = {
                                        it.x in
                                            (checkboxLeft - touchSlop)..(checkboxLeft +
                                                    checkboxWidth +
                                                    touchSlop)
                                    },
                                    scrollBy = { listState.scrollBy(it) },
                                ),
                    ) {
                        itemsIndexed(playlists, key = { _, item -> item.id }) { index, playlist ->
                            PlaylistSummaryRow(
                                playlist,
                                editMode,
                                playlist.id in selectedPlaylistIds,
                                { onPlaylistClick(playlist) },
                                { onRenamePlaylist(playlist) },
                                Modifier.libraryListEntrance(entrance) {
                                    index - listState.firstVisibleItemIndex
                                },
                            )
                            LibraryDivider()
                        }
                        item(key = "footer") {
                            LibraryFooter(R.plurals.playlists_count, playlists.size)
                        }
                    }
            }
    }
}

@Composable
private fun PlaylistSummaryRow(
    item: UserPlaylistSummary,
    editMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by
        animateFloatAsState(
            if (editMode) 1f else 0f,
            tween(200, easing = PlaylistEditEasing),
            label = "playlistEdit",
        )
    val checkbox =
        rememberSmartisanDrawablePainter(R.drawable.check_box_selector, checked = checked)
    val density = LocalDensity.current
    val checkboxWidth = with(density) { checkbox.intrinsicSize.width.toDp() }
    val left = dimensionResource(R.dimen.play_list_check_box_margin_left)
    val right = dimensionResource(R.dimen.play_list_check_box_margin_right)
    val locale = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                .semantics { if (editMode) selected = checked }
                .clickable(
                    source,
                    null,
                    role = if (editMode) Role.Checkbox else Role.Button,
                    onClick = smartisanClick(onClick),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width((checkboxWidth + left) * progress).fillMaxHeight()) {
                if (progress > 0f)
                    Image(
                        checkbox,
                        null,
                        Modifier.align(Alignment.CenterEnd)
                            .requiredWidth(checkboxWidth)
                            .graphicsLayer { alpha = progress },
                    )
            }
            Spacer(Modifier.width(right))
            Column(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides locale) {
                    BasicText(
                        item.name,
                        Modifier.fillMaxWidth(),
                        style =
                            TextStyle(
                                color = colorResource(R.color.setting_item_text_color),
                                fontSize = smartisanTextSize(R.dimen.text_size_large),
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        pluralStringResource(
                            R.plurals.library_playlist_song_count,
                            item.songCount,
                            item.songCount,
                        ),
                        Modifier.fillMaxWidth().padding(top = 3.dp),
                        style =
                            TextStyle(
                                color = colorResource(R.color.setting_item_summary_text_color),
                                fontSize = smartisanTextSize(R.dimen.text_size_micro),
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier.padding(end = dimensionResource(R.dimen.listview_items_margin_right))
                    .width(dimensionResource(R.dimen.playlist_right_action_width))
                    .fillMaxHeight()
            ) {
                if (progress < 1f)
                    Image(
                        rememberSmartisanDrawablePainter(R.drawable.arrow3_selector),
                        null,
                        Modifier.align(AbsoluteAlignment.CenterRight).graphicsLayer {
                            alpha = 1f - progress
                            translationX = size.width * progress
                        },
                    )
                if (progress > 0f) {
                    val actionSource = remember { MutableInteractionSource() }
                    val actionPressed by actionSource.collectIsPressedAsState()
                    Image(
                        rememberSmartisanDrawablePainter(
                            R.drawable.rename_playlist_selector,
                            pressed = pressed || actionPressed,
                        ),
                        stringResource(R.string.edit),
                        Modifier.align(AbsoluteAlignment.CenterRight)
                            .graphicsLayer {
                                alpha = progress
                                translationX = size.width * (1f - progress)
                            }
                            .clickable(
                                actionSource,
                                null,
                                enabled = editMode,
                                onClick = smartisanClick(onRename),
                            ),
                    )
                }
            }
        }
    }
}

internal val PlaylistEditEasing = Easing {
    ((kotlin.math.cos((it + 1) * Math.PI) / 2.0) + 0.5).toFloat()
}
