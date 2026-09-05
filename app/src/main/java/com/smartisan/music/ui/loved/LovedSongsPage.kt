package com.smartisan.music.ui.loved

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.data.favorite.FavoriteSongRecord
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.artwork.AlbumArtworkLoader
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.components.smartisanCheckboxBounds
import com.smartisan.music.ui.components.smartisanCheckboxHit
import com.smartisan.music.ui.components.smartisanVerticalScrollbar
import com.smartisan.music.ui.library.LibraryDivider
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.LibraryPlayActions
import com.smartisan.music.ui.library.SmartisanMediaArtwork
import com.smartisan.music.ui.library.libraryListEntrance
import com.smartisan.music.ui.library.libraryTexture
import com.smartisan.music.ui.library.rememberAlbumArtworkLoader
import com.smartisan.music.ui.library.rememberLibraryListEntrance
import com.smartisan.music.ui.shell.PredictiveBackHandler
import com.smartisan.music.ui.shell.PredictiveBackState
import com.smartisan.music.ui.songs.SmartisanPlayingTitle
import com.smartisan.music.ui.songs.SongPlaybackState
import com.smartisan.music.ui.songs.SongTitleNormalizer
import com.smartisan.music.ui.songs.qualityBadge
import com.smartisan.music.ui.songs.rememberSongPlaybackState
import kotlin.math.roundToInt

@Composable
internal fun LovedSongsPage(
    active: Boolean,
    mediaItems: List<MediaItem>,
    favoriteRecords: List<FavoriteSongRecord>,
    hiddenMediaIds: Set<String>,
    libraryLoaded: Boolean,
    onClose: (() -> Unit)?,
    closePredictiveBackState: PredictiveBackState?,
    onTrackMoreClick: (MediaItem) -> Unit,
    onRemoveFavoriteMediaIds: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val browser = LocalPlaybackBrowser.current
    val artwork = rememberAlbumArtworkLoader()
    val playback = rememberSongPlaybackState()
    var sortMode by remember { mutableStateOf(LovedSongsSortMode.Time) }
    var editMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var removeConfirm by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var sortBounds by remember { mutableStateOf(IntRect.Zero) }
    val entries =
        remember(favoriteRecords, mediaItems, hiddenMediaIds, sortMode) {
            sortLovedSongEntries(
                buildLovedSongEntries(
                    favoriteRecords,
                    mediaItems.filterNot { it.mediaId in hiddenMediaIds },
                ),
                sortMode,
                Comparator { left, right ->
                    SongTitleNormalizer.normalize(left)
                        .compareTo(SongTitleNormalizer.normalize(right))
                },
            )
        }
    val list = rememberLazyListState()
    val entrance =
        rememberLibraryListEntrance(Unit, active && entries.isNotEmpty()) {
            list.layoutInfo.visibleItemsInfo.size
        }
    LaunchedEffect(entries) {
        selectedIds = selectedIds.intersect(entries.map { it.mediaItem.mediaId }.toSet())
        if (entries.isEmpty()) editMode = false
        list.scrollToItem(0)
    }
    fun exitEdit() {
        editMode = false
        selectedIds = emptySet()
    }
    fun select(key: String, selected: Boolean) {
        selectedIds = if (selected) selectedIds + key else selectedIds - key
    }
    BackHandler(active && editMode) { exitEdit() }
    if (closePredictiveBackState != null && onClose != null) {
        PredictiveBackHandler(active && !editMode, closePredictiveBackState, onClose)
    } else if (onClose != null) BackHandler(active && !editMode) { onClose() }
    if (!active) return
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val checkboxBounds = remember { mutableMapOf<String, Rect>() }
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    Column(modifier.fillMaxSize().background(colorResource(R.color.page_background))) {
        val actions =
            listOf(
                SmartisanTitleBarAction(
                    if (editMode) R.drawable.titlebar_btn_delete_selector
                    else R.drawable.standard_icon_multi_select_selector,
                    stringResource(if (editMode) R.string.delete else R.string.edit),
                    onClick = {
                        if (editMode) removeConfirm = true
                        else {
                            editMode = true
                            selectedIds = emptySet()
                        }
                    },
                    enabled = if (editMode) selectedIds.isNotEmpty() else entries.isNotEmpty(),
                ),
                SmartisanTitleBarAction(
                    R.drawable.saved_songs_sort_btn_selector,
                    stringResource(R.string.sort_by_song_name),
                    onClick = { showSort = true },
                    enabled = entries.isNotEmpty(),
                    modifier =
                        Modifier.onGloballyPositioned {
                            val rect = it.boundsInWindow()
                            sortBounds =
                                IntRect(
                                    rect.left.roundToInt(),
                                    rect.top.roundToInt(),
                                    rect.right.roundToInt(),
                                    rect.bottom.roundToInt(),
                                )
                        },
                ),
            )
        SmartisanTitleBar(
            stringResource(R.string.collect_music),
            navigationIcon =
                if (editMode || onClose != null)
                    SmartisanTitleBarAction(
                        if (editMode) R.drawable.standard_icon_cancel_selector
                        else R.drawable.standard_icon_back_selector,
                        stringResource(if (editMode) R.string.cancel else R.string.back),
                        onClick = { if (editMode) exitEdit() else onClose?.invoke() },
                    )
                else null,
            actions = actions,
        )
        Column(Modifier.weight(1f).libraryTexture()) {
            if (entries.isNotEmpty()) {
                Column(Modifier.graphicsLayer { alpha = if (editMode) .22f else 1f }) {
                    LibraryPlayActions(
                        !editMode,
                        {
                            buildLovedSongsPlayRequest(entries)?.let {
                                browser.replaceQueueAndPlay(it.mediaItems, it.startIndex)
                            }
                        },
                        {
                            buildLovedSongsShuffleRequest(entries)?.let {
                                browser.replaceQueueAndPlayShuffled(it.mediaItems)
                            }
                        },
                    )
                }
                LazyColumn(
                    Modifier.fillMaxSize()
                        .onGloballyPositioned { listOrigin = it.positionInRoot() }
                        .smartisanVerticalScrollbar(list)
                        .smartisanSlideSelection(
                            editMode,
                            itemAt = { point ->
                                list.layoutInfo.visibleItemsInfo
                                    .firstOrNull {
                                        point.y >= it.offset && point.y < it.offset + it.size
                                    }
                                    ?.index
                            },
                            keyAt = { entries.getOrNull(it)?.mediaItem?.mediaId },
                            selectedKeys = selectedIds,
                            onSelectionChange = ::select,
                            canStart = { point ->
                                val row =
                                    list.layoutInfo.visibleItemsInfo.firstOrNull {
                                        point.y >= it.offset && point.y < it.offset + it.size
                                    }
                                val mediaId =
                                    entries.getOrNull(row?.index ?: -1)?.mediaItem?.mediaId
                                smartisanCheckboxHit(
                                    point + listOrigin,
                                    checkboxBounds[mediaId],
                                    touchSlop,
                                )
                            },
                            scrollBy = { list.scrollBy(it) },
                            edgeItemAt = { top ->
                                val visible =
                                    list.layoutInfo.visibleItemsInfo.filter {
                                        it.index in entries.indices
                                    }
                                (if (top) visible.firstOrNull() else visible.lastOrNull())?.index
                            },
                        ),
                    state = list,
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.mediaItem.mediaId }) {
                        index,
                        entry ->
                        val item = entry.mediaItem
                        LovedSongRow(
                            item,
                            editMode,
                            item.mediaId in selectedIds,
                            artwork,
                            playback,
                            onClick = {
                                if (editMode) select(item.mediaId, item.mediaId !in selectedIds)
                                else
                                    buildLovedSongsPlayRequest(entries, item.mediaId)?.let {
                                        browser.replaceQueueAndPlay(it.mediaItems, it.startIndex)
                                    }
                            },
                            onMore = { onTrackMoreClick(item) },
                            onCheckboxBoundsChanged = { bounds ->
                                if (bounds == null) checkboxBounds.remove(item.mediaId)
                                else checkboxBounds[item.mediaId] = bounds
                            },
                            modifier =
                                Modifier.libraryListEntrance(entrance) {
                                    index - list.firstVisibleItemIndex
                                },
                        )
                        LibraryDivider()
                    }
                    item("footer") { LibraryFooter(R.plurals.track_count, entries.size) }
                }
            } else if (libraryLoaded)
                SmartisanEmptyHint(
                    R.drawable.blank_playlist,
                    stringResource(R.string.no_saved_song),
                )
        }
    }
    if (showSort) LovedSongsSortPopup(sortMode, { sortMode = it }, { showSort = false }, sortBounds)
    if (removeConfirm)
        SmartisanModal({ removeConfirm = false }, Modifier.fillMaxWidth(), bottom = true) {
            SmartisanMenuTitleBar(
                stringResource(R.string.uncollect_song_dialog_title),
                { removeConfirm = false },
            )
            Column(
                Modifier.fillMaxWidth()
                    .smartisanPainterBackground(
                        rememberSmartisanDrawablePainter(R.drawable.menu_dialog_background)
                    )
                    .padding(
                        horizontal = dimensionResource(R.dimen.menu_dialog_horizontal_distance)
                    )
                    .padding(
                        top = dimensionResource(R.dimen.menu_dialog_btn_margin_view),
                        bottom = 24.dp,
                    )
            ) {
                SmartisanDialogButton(
                    stringResource(R.string.uncollect_song_confirm),
                    {
                        val ids = selectedIds
                        removeConfirm = false
                        exitEdit()
                        onRemoveFavoriteMediaIds(ids)
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
}

@Composable
private fun LovedSongRow(
    item: MediaItem,
    editMode: Boolean,
    selected: Boolean,
    artwork: AlbumArtworkLoader,
    playback: SongPlaybackState,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onCheckboxBoundsChanged: (Rect?) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val edit by
        animateFloatAsState(
            if (editMode) 1f else 0f,
            tween(200, easing = SmartisanEaseInOut),
            label = "lovedEdit",
        )
    val checkbox =
        rememberSmartisanDrawablePainter(R.drawable.check_box_selector, checked = selected)
    val density = LocalDensity.current
    val checkWidth = with(density) { checkbox.intrinsicSize.width.toDp() }
    val checkMargin = dimensionResource(R.dimen.check_box_margin_left)
    val imageSize = dimensionResource(R.dimen.listview_item_image_width)
    val title =
        item.mediaMetadata.displayTitle?.toString()
            ?: item.mediaMetadata.title?.toString()
            ?: stringResource(R.string.unknown_song_title)
    val artist =
        item.mediaMetadata.artist?.toString()
            ?: item.mediaMetadata.subtitle?.toString()
            ?: stringResource(R.string.unknown_artist)
    val album = item.mediaMetadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
    val textDirection =
        if (
            androidx.compose.ui.platform.LocalLayoutDirection.current ==
                androidx.compose.ui.unit.LayoutDirection.Rtl
        )
            androidx.compose.ui.text.style.TextDirection.ContentOrRtl
        else androidx.compose.ui.text.style.TextDirection.ContentOrLtr
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
                    onClick = smartisanClick(onClick),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width((checkWidth + checkMargin) * edit).fillMaxHeight()) {
                if (edit > 0f)
                    Image(
                        checkbox,
                        null,
                        Modifier.align(Alignment.CenterEnd)
                            .requiredWidth(checkWidth)
                            .graphicsLayer { alpha = edit }
                            .smartisanCheckboxBounds(onCheckboxBoundsChanged),
                    )
            }
            Spacer(Modifier.width(dimensionResource(R.dimen.saved_songs_items_margin_left)))
            Box(Modifier.size(imageSize)) {
                SmartisanMediaArtwork(
                    item,
                    with(density) { imageSize.roundToPx() },
                    R.drawable.noalbumcover_120,
                    Modifier.fillMaxSize(),
                    artwork,
                )
                Image(
                    rememberSmartisanDrawablePainter(R.drawable.mask_albumcover_list),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
            Column(
                Modifier.weight(1f).padding(start = dimensionResource(R.dimen.common_padding_left))
            ) {
                SmartisanPlayingTitle(
                    title,
                    !editMode && item.mediaId == playback.mediaId,
                    playback.playing,
                    smartisanTextSize(R.dimen.text_size_medium),
                    Modifier.padding(top = dimensionResource(R.dimen.collect_text_padding_top)),
                    textDirection = textDirection,
                )
                Row(
                    Modifier.padding(top = dimensionResource(R.dimen.listview_items_margin_top1)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.qualityBadge()?.let {
                        Image(
                            rememberSmartisanDrawablePainter(it),
                            null,
                            Modifier.padding(
                                end = dimensionResource(R.dimen.album_song_index_margin_left)
                            ),
                        )
                    }
                    BasicText(
                        if (album == null) artist else "$artist - $album",
                        style =
                            TextStyle(
                                textDirection = textDirection,
                                color = colorResource(R.color.list_text_color_small),
                                fontSize = smartisanTextSize(R.dimen.text_size_small),
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!editMode) {
                val moreInteraction = remember { MutableInteractionSource() }
                val morePressed by moreInteraction.collectIsPressedAsState()
                Image(
                    rememberSmartisanDrawablePainter(
                        R.drawable.btn_more_selector,
                        pressed = morePressed || pressed,
                    ),
                    stringResource(R.string.tab_more),
                    Modifier.padding(end = dimensionResource(R.dimen.listview_items_margin_right))
                        .size(dimensionResource(R.dimen.listview_item_height))
                        .clickable(
                            moreInteraction,
                            null,
                            role = Role.Button,
                            onClick = smartisanClick(onMore),
                        ),
                    contentScale = ContentScale.None,
                )
            }
        }
    }
}
