package com.smartisan.music.ui.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
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
import com.smartisan.music.R
import com.smartisan.music.ui.artwork.AlbumArtworkLoader
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.library.SmartisanMediaArtwork
import com.smartisan.music.ui.library.rememberAlbumArtworkLoader
import kotlin.math.roundToInt

internal data class PlaybackQueueSnapshot(
    val history: List<PlaybackQueueTrack> = emptyList(),
    val current: PlaybackQueueTrack? = null,
    val upcoming: List<PlaybackQueueTrack> = emptyList(),
    val isCurrentFavorite: Boolean = false,
    val reorderEnabled: Boolean = true,
)

internal data class PlaybackQueueTrack(
    val queueIndex: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val score: Int,
    val mediaItem: MediaItem,
)

private sealed interface PlaybackQueueRow {
    data class Header(val title: String, val clearable: Boolean = false) : PlaybackQueueRow

    data class Track(val track: PlaybackQueueTrack, val section: QueueSection) : PlaybackQueueRow
}

private enum class QueueSection {
    History,
    Current,
    Upcoming,
}

@Composable
internal fun PlaybackQueueLayer(
    snapshot: PlaybackQueueSnapshot,
    onItemClick: (Int) -> Unit,
    onFavoriteCurrentClick: () -> Unit,
    onCurrentRatingChanged: (String, Int) -> Unit,
    onClearUpcomingClick: () -> Unit,
    onMoveRequest: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val historyTitle = stringResource(R.string.history_title)
    val currentTitle = stringResource(R.string.playing_title)
    val upcomingTitle = stringResource(R.string.orginal_title)
    val rows =
        remember(snapshot, historyTitle, currentTitle, upcomingTitle) {
            buildList {
                if (snapshot.history.isNotEmpty()) {
                    add(PlaybackQueueRow.Header(historyTitle))
                    snapshot.history.forEach {
                        add(PlaybackQueueRow.Track(it, QueueSection.History))
                    }
                }
                add(PlaybackQueueRow.Header(currentTitle))
                snapshot.current?.let { add(PlaybackQueueRow.Track(it, QueueSection.Current)) }
                add(PlaybackQueueRow.Header(upcomingTitle, snapshot.upcoming.isNotEmpty()))
                snapshot.upcoming.forEach { add(PlaybackQueueRow.Track(it, QueueSection.Upcoming)) }
            }
        }
    var previewRows by remember(rows) { mutableStateOf(rows) }
    val dragState = rememberSmartisanListDragState(rows)
    val reorderable =
        remember(previewRows) {
            val first = previewRows.indexOfFirst {
                it is PlaybackQueueRow.Track && it.section == QueueSection.Upcoming
            }
            if (first < 0) IntRange.EMPTY
            else
                first..previewRows.indexOfLast {
                        it is PlaybackQueueRow.Track && it.section == QueueSection.Upcoming
                    }
        }
    val listState = rememberLazyListState()
    val itemEntranceDuration = LocalResources.current.getInteger(R.integer.item_flip)
    val entranceTime = remember { Animatable(0f) }
    var entranceLastIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(itemEntranceDuration) {
        withFrameNanos {}
        entranceLastIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = (itemEntranceDuration * (1f + .2f * entranceLastIndex)).roundToInt()
        entranceTime.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }
    val loader = rememberAlbumArtworkLoader()
    val latestMove = rememberUpdatedState(onMoveRequest)
    val latestRows = rememberUpdatedState(rows)
    val density = LocalDensity.current
    val configuration = LocalViewConfiguration.current
    val exactTargets =
        remember(configuration) {
            object : ViewConfiguration by configuration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
        }
    val handle = rememberSmartisanDrawablePainter(R.drawable.btn_drag_selector)
    val topShadow = rememberSmartisanDrawablePainter(R.drawable.shadow_top)
    val bottomShadow = rememberSmartisanDrawablePainter(R.drawable.shadow_bottom)
    val shadowTop = topShadow.intrinsicSize.height.roundToInt()
    val shadowBottom = bottomShadow.intrinsicSize.height.roundToInt()
    val handleRightMargin = with(density) { 2.dp.roundToPx() }
    val rowHeight = with(density) { dimensionResource(R.dimen.listview_item_height).roundToPx() }
    val divider = dimensionResource(R.dimen.playback_listview_dividerHeight)
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalViewConfiguration provides exactTargets,
    ) {
        Box(modifier.padding(top = dimensionResource(R.dimen.titlebar_height))) {
            Box(
                Modifier.fillMaxSize()
                    .smartisanPainterBackground(
                        rememberSmartisanDrawablePainter(R.drawable.account_background)
                    )
                    .pointerInput(
                        rows,
                        snapshot.reorderEnabled,
                        handle,
                        shadowTop,
                        shadowBottom,
                        rowHeight,
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
                            if (!snapshot.reorderEnabled) return@awaitEachGesture
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val item =
                                visible.firstOrNull {
                                    down.position.y >= it.offset &&
                                        down.position.y < it.offset + it.size
                                } ?: return@awaitEachGesture
                            val track =
                                previewRows.getOrNull(item.index) as? PlaybackQueueRow.Track
                                    ?: return@awaitEachGesture
                            if (track.section != QueueSection.Upcoming) return@awaitEachGesture
                            if (
                                !smartisanDragHandleHit(
                                    down.position,
                                    size.width,
                                    item.offset,
                                    rowHeight,
                                    handle.intrinsicSize,
                                    handleRightMargin,
                                    configuration.touchSlop,
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
                                    configuration.touchSlop,
                                ) { pointerY ->
                                    smartisanDragTargetAt(
                                        pointerY,
                                        listState.layoutInfo.visibleItemsInfo.map {
                                            SmartisanDragRowBounds(it.index, it.offset, it.size)
                                        },
                                        reorderable,
                                        size.height,
                                    )
                                }
                            }
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change =
                                        event.changes.firstOrNull { it.id == down.id } ?: break
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
                                    isCurrent = { latestRows.value == rows },
                                ) { sourceIndex, targetIndex ->
                                    val source =
                                        (previewRows[sourceIndex] as PlaybackQueueRow.Track).track
                                    val target =
                                        (previewRows[targetIndex] as PlaybackQueueRow.Track).track
                                    previewRows =
                                        previewRows.toMutableList().apply {
                                            add(targetIndex, removeAt(sourceIndex))
                                        }
                                    latestMove.value(source.queueIndex, target.queueIndex)
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    Modifier.fillMaxSize().smartisanVerticalScrollbar(listState),
                    state = listState,
                    userScrollEnabled = dragState.drag == null && !dragState.settling,
                ) {
                    itemsIndexed(
                        previewRows,
                        key = { _, row ->
                            when (row) {
                                is PlaybackQueueRow.Header -> "header:${row.title}"
                                is PlaybackQueueRow.Track ->
                                    "${row.section}:${row.track.queueIndex}:${row.track.mediaId}"
                            }
                        },
                    ) { index, row ->
                        val layer = rememberSmartisanDragLayer(dragState, index)
                        Column {
                            val recorded =
                                Modifier.smartisanDragItem(dragState, index, rowHeight) {
                                        val entered =
                                            if (entranceLastIndex >= 0 && index > entranceLastIndex)
                                                1f
                                            else {
                                                ((entranceTime.value -
                                                        index * itemEntranceDuration * .2f) /
                                                        itemEntranceDuration)
                                                    .coerceIn(0f, 1f)
                                            }
                                        entered * entered
                                    }
                                    .smartisanDragRecording(layer)
                            when (row) {
                                is PlaybackQueueRow.Header ->
                                    QueueHeader(row, onClearUpcomingClick, recorded)
                                is PlaybackQueueRow.Track ->
                                    if (row.section == QueueSection.Current) {
                                        QueueCurrentTrack(
                                            row.track,
                                            snapshot.isCurrentFavorite,
                                            loader,
                                            { onItemClick(row.track.queueIndex) },
                                            onFavoriteCurrentClick,
                                            { onCurrentRatingChanged(row.track.mediaId, it) },
                                            recorded,
                                        )
                                    } else
                                        QueueNormalTrack(
                                            row.track,
                                            row.section == QueueSection.Upcoming &&
                                                snapshot.reorderEnabled,
                                            loader,
                                            { onItemClick(row.track.queueIndex) },
                                            recorded,
                                        )
                            }
                            if (index < previewRows.lastIndex)
                                Spacer(
                                    Modifier.fillMaxWidth()
                                        .height(divider)
                                        .background(colorResource(R.color.listview_divider_color))
                                )
                        }
                    }
                }
                SmartisanDragOverlay(dragState, topShadow, bottomShadow)
            }
        }
    }
}

@Composable
private fun QueueHeader(row: PlaybackQueueRow.Header, onClear: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.now_playing_header_height))
            .smartisanPainterBackground(rememberSmartisanDrawablePainter(R.drawable.list_title_bg))
    ) {
        BasicText(
            row.title,
            Modifier.align(Alignment.CenterStart)
                .padding(start = dimensionResource(R.dimen.tabbar_padding_left)),
            style =
                TextStyle(
                    color = colorResource(R.color.title_text_color),
                    fontSize = smartisanTextSize(R.dimen.radio_category_header_text_size),
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
        )
        if (row.clearable)
            QueueIconButton(
                R.drawable.header_remove_selector,
                stringResource(R.string.str_clear_play_task),
                onClear,
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = dimensionResource(R.dimen.listview_items_margin_right)),
            )
    }
}

@Composable
private fun QueueNormalTrack(
    track: PlaybackQueueTrack,
    reorderable: Boolean,
    loader: AlbumArtworkLoader,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.listview_item_height))
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    R.drawable.playing_queue_item_selector,
                    pressed = pressed,
                )
            )
            .clickable(interaction, null, onClick = smartisanClick(onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverSize = dimensionResource(R.dimen.listview_item_image_width)
        Box(
            Modifier.width(coverSize)
                .height(dimensionResource(R.dimen.listview_item_image_height))
                .padding(
                    start = dimensionResource(R.dimen.common_padding_left),
                    top = dimensionResource(R.dimen.album_listview_item_padding),
                    bottom = dimensionResource(R.dimen.album_listview_item_padding),
                )
        ) {
            QueueArtwork(
                track,
                loader,
                R.drawable.noalbumcover_120,
                with(LocalDensity.current) { coverSize.roundToPx() },
            )
        }
        Box(
            Modifier.weight(1f)
                .fillMaxHeight()
                .padding(start = dimensionResource(R.dimen.common_padding_left))
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                    .width(dimensionResource(R.dimen.listview_items_textview_width))
                    .padding(end = dimensionResource(R.dimen.listview_items_padding_left_left1))
            ) {
                QueueTrackText(track.title, true, pressed)
                QueueTrackText(
                    track.artist,
                    false,
                    pressed,
                    Modifier.padding(top = dimensionResource(R.dimen.listview_items_margin_top1)),
                )
            }
        }
        Image(
            rememberSmartisanDrawablePainter(R.drawable.btn_drag_selector),
            null,
            Modifier.padding(end = 2.dp).graphicsLayer { alpha = if (reorderable) 1f else 0f },
        )
    }
}

@Composable
private fun QueueCurrentTrack(
    track: PlaybackQueueTrack,
    favorite: Boolean,
    loader: AlbumArtworkLoader,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onRating: (Int) -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cover = dimensionResource(R.dimen.album_cover_zone_width)
    val padding = dimensionResource(R.dimen.common_padding_left)
    val favoritePainter =
        rememberSmartisanDrawablePainter(R.drawable.btn_favorite_add_selector, checked = favorite)
    val favoriteWidth = with(LocalDensity.current) { favoritePainter.intrinsicSize.width.toDp() }
    Box(
        modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.fake_song_playing_zone_layout))
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    R.drawable.playing_queue_item_selector,
                    pressed = pressed,
                )
            )
            .clickable(interaction, null, onClick = smartisanClick(onClick))
            .padding(start = padding)
    ) {
        Box(Modifier.align(Alignment.CenterStart).size(cover)) {
            QueueArtwork(
                track,
                loader,
                R.drawable.playing_cover_lp,
                with(LocalDensity.current) { cover.roundToPx() },
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .padding(
                    start = cover + 2.dp + padding,
                    end =
                        favoriteWidth +
                            2.dp +
                            dimensionResource(R.dimen.listview_items_padding_left_left1),
                    top = 13.dp,
                )
        ) {
            QueueTrackText(track.title, true, pressed)
            QueueTrackText(track.artist, false, pressed)
        }
        SmartisanRatingBar(
            track.score,
            onRating,
            Modifier.align(Alignment.BottomStart)
                .padding(start = cover + 4.dp)
                .height(dimensionResource(R.dimen.queue_rating_bar_height)),
        )
        QueueIconButton(
            R.drawable.btn_favorite_add_selector,
            stringResource(R.string.action_collect_song),
            onFavorite,
            Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 2.dp),
            checked = favorite,
        )
    }
}

@Composable
private fun BoxScope.QueueArtwork(
    track: PlaybackQueueTrack,
    loader: AlbumArtworkLoader,
    fallback: Int,
    sizePx: Int,
) {
    SmartisanMediaArtwork(track.mediaItem, sizePx, fallback, Modifier.matchParentSize(), loader)
    Image(
        rememberSmartisanDrawablePainter(R.drawable.mask_albumcover_list),
        null,
        Modifier.matchParentSize(),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun QueueTrackText(
    text: String,
    primary: Boolean,
    pressed: Boolean,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier.fillMaxWidth(),
        style =
            TextStyle(
                color =
                    smartisanStateColor(
                        if (primary) R.drawable.text_color_white_and_black_selector
                        else R.drawable.text_color_white_and_gray1_selector,
                        pressed = pressed,
                    ),
                fontSize =
                    smartisanTextSize(
                        if (primary) R.dimen.text_size_medium else R.dimen.text_size_small
                    ),
                platformStyle = PlatformTextStyle(includeFontPadding = true),
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun QueueIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    checked: Boolean? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val click = smartisanClick(onClick)
    val input =
        if (checked == null)
            Modifier.clickable(interaction, null, role = Role.Button, onClick = click)
        else
            Modifier.toggleable(
                checked,
                interaction,
                null,
                role = Role.Checkbox,
                onValueChange = { click() },
            )
    Image(
        rememberSmartisanDrawablePainter(icon, pressed = pressed, checked = checked == true),
        label,
        modifier.then(input),
    )
}
