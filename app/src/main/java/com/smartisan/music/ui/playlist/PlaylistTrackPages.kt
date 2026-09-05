package com.smartisan.music.ui.playlist

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.playlist.UserPlaylistDetail
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.library.LibraryBlank
import com.smartisan.music.ui.library.LibraryDivider
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.libraryListEntrance
import com.smartisan.music.ui.library.rememberLibraryListEntrance
import com.smartisan.music.ui.songs.SmartisanPlayingTitle
import com.smartisan.music.ui.songs.SongPlaybackState
import com.smartisan.music.ui.songs.qualityBadge
import com.smartisan.music.ui.songs.rememberSongPlaybackState
import kotlin.math.roundToInt

@Composable
internal fun PlaylistDetailPage(
    active: Boolean,
    playlist: UserPlaylistDetail?,
    title: String,
    tracks: List<MediaItem>,
    libraryLoading: Boolean,
    editMode: Boolean,
    selectedTrackIds: Set<String>,
    browser: Player?,
    onShuffle: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onEditModeChange: (Boolean) -> Unit,
    onAddOrRemoveClick: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onReorderTracks: (List<String>) -> Unit,
    onTrackSelectionChange: (String, Boolean) -> Unit,
    onTrackClick: (MediaItem, Int) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val playback = rememberSongPlaybackState(browser)
    var preview by remember(tracks) { mutableStateOf(tracks) }
    val dragState = rememberSmartisanListDragState(tracks)
    val latestTracks by rememberUpdatedState(tracks)
    val latestReorder by rememberUpdatedState(onReorderTracks)
    val density = LocalDensity.current
    val config = LocalViewConfiguration.current
    val handle = rememberSmartisanDrawablePainter(R.drawable.btn_drag_selector)
    val shadowTopPainter = rememberSmartisanDrawablePainter(R.drawable.shadow_top)
    val shadowBottomPainter = rememberSmartisanDrawablePainter(R.drawable.shadow_bottom)
    val shadowTop = shadowTopPainter.intrinsicSize.height.roundToInt()
    val shadowBottom = shadowBottomPainter.intrinsicSize.height.roundToInt()
    val rightMargin =
        with(density) { dimensionResource(R.dimen.listview_items_margin_right).roundToPx() }
    val rowHeight = with(density) { dimensionResource(R.dimen.listview_item_height).roundToPx() }
    val checkboxLeft = with(density) { dimensionResource(R.dimen.check_box_margin_left).toPx() }
    val checkboxWidth =
        rememberSmartisanDrawablePainter(R.drawable.check_box_selector).intrinsicSize.width
    val entrance =
        rememberLibraryListEntrance(tracks, active) { listState.layoutInfo.visibleItemsInfo.size }
    DisposableEffect(active, editMode, dragState) {
        onDispose { dragState.reset() }
    }
    Column(
        modifier
            .fillMaxSize()
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(R.drawable.account_background)
            )
    ) {
        if (active) {
            PlaylistDetailHeader(
                tracks.size,
                selectedTrackIds.size,
                editMode,
                onShuffle,
                onDeletePlaylist,
                { onEditModeChange(true) },
                onAddOrRemoveClick,
                onToggleAll,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (!libraryLoading && tracks.isEmpty())
                    LibraryBlank(
                        R.drawable.blank_song,
                        stringResource(R.string.no_song),
                        stringResource(R.string.addsong_playlist),
                    )
                if (!libraryLoading && tracks.isNotEmpty())
                    Box(
                        Modifier.fillMaxSize().pointerInput(
                            tracks,
                            editMode,
                            rowHeight,
                            shadowTop,
                            shadowBottom,
                            rightMargin,
                        ) {
                            awaitEachGesture {
                                val down =
                                    awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                if (dragState.settling) {
                                    down.consume()
                                    return@awaitEachGesture
                                }
                                if (!editMode) return@awaitEachGesture
                                val item =
                                    listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        down.position.y >= it.offset &&
                                            down.position.y < it.offset + it.size &&
                                            it.index < preview.size
                                    } ?: return@awaitEachGesture
                                if (
                                    !smartisanDragHandleHit(
                                        down.position,
                                        size.width,
                                        item.offset,
                                        rowHeight,
                                        handle.intrinsicSize,
                                        rightMargin,
                                        config.touchSlop,
                                    )
                                )
                                    return@awaitEachGesture
                                if (
                                    !dragState.start(
                                        item.index,
                                        item.offset,
                                        rowHeight,
                                        down.position.y,
                                        shadowTop,
                                    )
                                )
                                    return@awaitEachGesture
                                down.consume()
                                var released = false
                                fun update(y: Float) {
                                    dragState.move(
                                        y,
                                        size.height,
                                        shadowTop,
                                        shadowBottom,
                                        config.touchSlop,
                                    ) { pointerY ->
                                        smartisanDragTargetAt(
                                            pointerY,
                                            listState.layoutInfo.visibleItemsInfo.map {
                                                SmartisanDragRowBounds(it.index, it.offset, it.size)
                                            },
                                            preview.indices,
                                            size.height,
                                        )
                                    }
                                }
                                try {
                                    while (true) {
                                        val change =
                                            awaitPointerEvent(PointerEventPass.Initial)
                                                .changes
                                                .firstOrNull { it.id == down.id } ?: break
                                        if (change.isConsumed) break
                                        update(change.position.y)
                                        change.consume()
                                        if (!change.pressed) {
                                            released = true
                                            break
                                        }
                                    }
                                } finally {
                                    dragState.settle(
                                        released,
                                        shadowTop,
                                        rowTop = { target ->
                                            listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == target }
                                                ?.offset
                                        },
                                        isCurrent = { latestTracks == tracks },
                                    ) { source, target ->
                                        preview =
                                            preview.toMutableList().apply {
                                                add(target, removeAt(source))
                                            }
                                        latestReorder(preview.map { it.mediaId })
                                    }
                                }
                            }
                        }
                    ) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = dragState.drag == null && !dragState.settling,
                            modifier =
                                Modifier.fillMaxSize()
                                    .smartisanSlideSelection(
                                        editMode && dragState.drag == null && !dragState.settling,
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
                                                .filter { it.index < preview.size }
                                                .let {
                                                    if (top) it.firstOrNull()?.index
                                                    else it.lastOrNull()?.index
                                                }
                                        },
                                        keyAt = { preview.getOrNull(it)?.mediaId },
                                        selectedKeys = selectedTrackIds,
                                        onSelectionChange = onTrackSelectionChange,
                                        canStart = {
                                            it.x in
                                                (checkboxLeft - config.touchSlop)..(checkboxLeft +
                                                        checkboxWidth +
                                                        config.touchSlop)
                                        },
                                        scrollBy = { listState.scrollBy(it) },
                                    ),
                        ) {
                            itemsIndexed(preview, key = { _, item -> item.mediaId }) { index, item
                                ->
                                val layer = rememberSmartisanDragLayer(dragState, index)
                                Column(
                                    Modifier.libraryListEntrance(entrance) {
                                            index - listState.firstVisibleItemIndex
                                        }
                                        .smartisanDragItem(dragState, index, rowHeight)
                                ) {
                                    PlaylistTrackRow(
                                        item,
                                        editMode,
                                        item.mediaId in selectedTrackIds,
                                        playback,
                                        { onTrackClick(item, index) },
                                        { onTrackMoreClick(item) },
                                        Modifier.smartisanDragRecording(layer),
                                    )
                                    LibraryDivider()
                                }
                            }
                            item(key = "footer") {
                                LibraryFooter(R.plurals.track_count, preview.size)
                            }
                        }
                        SmartisanDragOverlay(dragState, shadowTopPainter, shadowBottomPainter)
                    }
                Image(
                    rememberSmartisanDrawablePainter(R.drawable.title_bar_shadow_standard),
                    null,
                    Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    item: MediaItem,
    editMode: Boolean,
    checked: Boolean,
    playback: SongPlaybackState,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by
        animateFloatAsState(
            if (editMode) 1f else 0f,
            tween(200, easing = PlaylistEditEasing),
            label = "playlistTrackEdit",
        )
    val checkbox =
        rememberSmartisanDrawablePainter(R.drawable.check_box_selector, checked = checked)
    val handle = rememberSmartisanDrawablePainter(R.drawable.btn_drag_selector, pressed = pressed)
    val density = LocalDensity.current
    val checkboxWidth = with(density) { checkbox.intrinsicSize.width.toDp() }
    val handleWidth = with(density) { handle.intrinsicSize.width.toDp() }
    val checkLeft = dimensionResource(R.dimen.check_box_margin_left)
    val handleRight = dimensionResource(R.dimen.listview_items_margin_right)
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
            Box(Modifier.width((checkboxWidth + checkLeft) * progress).fillMaxHeight()) {
                if (progress > 0f)
                    Image(
                        checkbox,
                        null,
                        Modifier.align(Alignment.CenterEnd).requiredWidth(checkboxWidth),
                    )
            }
            Spacer(Modifier.width(dimensionResource(R.dimen.listview_items_margin_left)))
            Column(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalLayoutDirection provides locale) {
                    val title =
                        item.mediaMetadata.displayTitle?.toString()
                            ?: item.mediaMetadata.title?.toString()
                            ?: stringResource(R.string.unknown_song_title)
                    SmartisanPlayingTitle(
                        title,
                        !editMode && item.mediaId == playback.mediaId,
                        playback.playing,
                        smartisanTextSize(R.dimen.text_size_large),
                        Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        qualityBadge(item)?.let { badge ->
                            Image(
                                rememberSmartisanDrawablePainter(badge),
                                null,
                                Modifier.padding(
                                    top =
                                        dimensionResource(
                                            R.dimen.listview_item_line_two_paddingtop
                                        ),
                                    end = dimensionResource(R.dimen.album_song_index_margin_left),
                                ),
                            )
                        }
                        BasicText(
                            item.mediaMetadata.artist?.toString()
                                ?: item.mediaMetadata.subtitle?.toString()
                                ?: stringResource(R.string.unknown_artist),
                            Modifier.padding(
                                top = dimensionResource(R.dimen.listview_item_line_two_paddingtop)
                            ),
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
            }
            val duration =
                item.mediaMetadata.durationMs
                    ?.takeIf { it > 0 }
                    ?.let { "%d:%02d".format(it / 60000L, it / 1000L % 60L) }
                    .orEmpty()
            BasicText(
                duration,
                Modifier.padding(dimensionResource(R.dimen.album_listview_item_margin_top))
                    .padding(start = dimensionResource(R.dimen.alum_line_width)),
                style =
                    TextStyle(
                        color = colorResource(R.color.setting_item_summary_text_color),
                        fontSize = smartisanTextSize(R.dimen.text_size_micro),
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
            )
            if (!editMode) {
                val moreSource = remember { MutableInteractionSource() }
                val morePressed by moreSource.collectIsPressedAsState()
                Image(
                    rememberSmartisanDrawablePainter(
                        R.drawable.btn_more_selector,
                        pressed = pressed || morePressed,
                    ),
                    stringResource(R.string.tab_more),
                    Modifier.padding(end = handleRight)
                        .size(dimensionResource(R.dimen.listview_item_height))
                        .clickable(moreSource, null, onClick = smartisanClick(onMore)),
                    contentScale = ContentScale.None,
                )
            }
            Box(Modifier.width((handleWidth + handleRight) * progress).fillMaxHeight()) {
                if (progress > 0f)
                    Image(
                        handle,
                        null,
                        Modifier.align(Alignment.CenterStart).requiredWidth(handleWidth),
                    )
            }
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    count: Int,
    selected: Int,
    editing: Boolean,
    onShuffle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAddRemove: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(48.dp).background(colorResource(R.color.surface_card))) {
        AnimatedVisibility(
            !editing,
            enter = fadeIn(tween(200, easing = PlaylistEditEasing)),
            exit = fadeOut(tween(140, easing = PlaylistEditEasing)),
        ) {
            Layout(
                content = {
                    PlaylistActionButton(
                        R.drawable.btn_shuffle2_selector,
                        R.string.s_random_play,
                        onShuffle,
                    )
                    PlaylistActionButton(
                        R.drawable.btn_deletelist2_selector,
                        R.string.s_remove_track_list,
                        onDelete,
                    )
                    PlaylistActionButton(
                        R.drawable.btn_editlist2_selector,
                        R.string.s_edit_track_list,
                        onEdit,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) { children, constraints ->
                val overlap = 6.dp.roundToPx()
                val width = (constraints.maxWidth + overlap * 2) / 3
                val placeables = children.map {
                    it.measure(Constraints.fixed(width, constraints.maxHeight))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEachIndexed { i, child -> child.place(i * (width - overlap), 0) }
                }
            }
        }
        AnimatedVisibility(
            editing,
            enter = fadeIn(tween(200, easing = PlaylistEditEasing)),
            exit = fadeOut(tween(140, easing = PlaylistEditEasing)),
        ) {
            val checked = count > 0 && count == selected
            val source = remember { MutableInteractionSource() }
            val localeDirection = LocalLayoutDirection.current
            val textDirection =
                if (localeDirection == LayoutDirection.Rtl) TextDirection.ContentOrRtl
                else TextDirection.ContentOrLtr
            val textAlign =
                if (localeDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        rememberSmartisanDrawablePainter(
                            R.drawable.check_box_selector,
                            enabled = count > 0,
                            checked = checked,
                        ),
                        null,
                        Modifier.padding(start = dimensionResource(R.dimen.check_box_margin_left))
                            .toggleable(
                                checked,
                                source,
                                null,
                                enabled = count > 0,
                                role = Role.Checkbox,
                                onValueChange = onToggleAll,
                            ),
                    )
                    BasicText(
                        stringResource(R.string.selected_item_format, selected, count),
                        Modifier.weight(1f).padding(start = 18.dp),
                        style =
                            TextStyle(
                                color = colorResource(R.color.setting_item_summary_text_color),
                                fontSize = smartisanTextSize(R.dimen.text_size_better),
                                textDirection = textDirection,
                                textAlign = textAlign,
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                            ),
                        maxLines = 1,
                    )
                    val removing = selected > 0
                    val buttonSource = remember { MutableInteractionSource() }
                    val pressed by buttonSource.collectIsPressedAsState()
                    Row(
                        Modifier.padding(end = 6.dp)
                            .height(30.dp)
                            .smartisanPainterBackground(
                                rememberSmartisanDrawablePainter(
                                    if (removing) R.drawable.btn_red_bg_selector
                                    else R.drawable.btn_add_song_selector,
                                    pressed = pressed,
                                )
                            )
                            .clickable(buttonSource, null, onClick = smartisanClick(onAddRemove)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            rememberSmartisanDrawablePainter(
                                if (removing) R.drawable.btn_delete_song2_selector
                                else R.drawable.btn_add_song2_selector
                            ),
                            null,
                            Modifier.padding(horizontal = 10.dp).size(14.dp),
                        )
                        BasicText(
                            stringResource(
                                if (removing) R.string.delete_track else R.string.add_track
                            ),
                            Modifier.padding(end = 10.dp),
                            style =
                                TextStyle(
                                    color =
                                        colorResource(
                                            if (removing) R.color.btn_text_color_red
                                            else R.color.btn_text_color_blue
                                        ),
                                    fontSize = smartisanTextSize(R.dimen.button_text_size),
                                    fontWeight = FontWeight.Bold,
                                    textDirection = textDirection,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistActionButton(icon: Int, title: Int, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier.fillMaxSize()
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    R.drawable.title_button_bg_selector,
                    pressed = pressed,
                )
            )
            .clickable(source, null, onClick = smartisanClick(onClick)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            rememberSmartisanDrawablePainter(icon, pressed = pressed),
            null,
            Modifier.padding(end = 10.dp),
        )
        BasicText(
            stringResource(title),
            style =
                TextStyle(
                    color = colorResource(R.color.transparent_black),
                    fontSize = smartisanTextSize(R.dimen.button_text_size),
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
        )
    }
}

private fun qualityBadge(item: MediaItem): Int? =
    when (item.mediaMetadata.extras?.getString(LocalAudioLibrary.AudioQualityBadgeExtraKey)) {
        "flac" -> R.drawable.audio_quality_flac
        "ape" -> R.drawable.audio_quality_ape
        "wav" -> R.drawable.audio_quality_wav
        "aiff" -> R.drawable.audio_quality_aiff
        "alac" -> R.drawable.audio_quality_alac
        "cue" -> R.drawable.audio_quality_cue
        else -> null
    }

internal fun <T> Set<T>.togglePlaylistSelection(value: T): Set<T> =
    if (value in this) this - value else this + value
