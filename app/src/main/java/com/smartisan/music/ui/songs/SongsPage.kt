package com.smartisan.music.ui.songs

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanCheckboxHit
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanSlideSelection
import com.smartisan.music.ui.components.smartisanVerticalScrollbar
import com.smartisan.music.ui.library.LibraryDivider
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.LibraryPlayActions
import com.smartisan.music.ui.library.libraryTexture
import kotlinx.coroutines.launch

@Composable
internal fun SongsPage(
    mediaItems: List<MediaItem>,
    libraryLoaded: Boolean,
    active: Boolean,
    editMode: Boolean,
    selectedSongIds: Set<String>,
    hiddenMediaIds: Set<String>,
    onSongSelectionChange: (String, Boolean) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    onRequestSongDeleteConfirmation: (Set<String>, (() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
    playbackBarOverlayHeight: Dp = 0.dp,
) {
    val browser = LocalPlaybackBrowser.current
    val playback = rememberSongPlaybackState()
    var selectedSortIndex by remember { mutableIntStateOf(0) }
    val sortedSongs =
        remember(mediaItems, hiddenMediaIds, selectedSortIndex) {
            mediaItems
                .filterNot { it.mediaId in hiddenMediaIds }
                .sortedForSongSort(selectedSortIndex)
        }
    val displayMode = selectedSortIndex.toSongSortDisplayMode()
    val rows =
        remember(sortedSongs, displayMode) {
            buildSongRows(sortedSongs, displayMode.toSectionMode())
        }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var openKey by remember { mutableStateOf<String?>(null) }
    var swipeActive by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSortIndex) {
        openKey = null
        swipeActive = false
        list.scrollToItem(0)
    }
    LaunchedEffect(editMode, active) {
        if (editMode || !active) {
            openKey = null
            swipeActive = false
        }
    }
    if (!active) return
    val quickBarWidth =
        rememberSmartisanDrawablePainter(R.drawable.letters_bar_background_shadow)
            .intrinsicSize
            .width +
            rememberSmartisanDrawablePainter(R.drawable.letters_bar_background).intrinsicSize.width
    val moreWidth =
        rememberSmartisanDrawablePainter(R.drawable.btn_more_selector).intrinsicSize.width
    val context = LocalContext.current
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val checkboxBounds = remember { mutableMapOf<String, Rect>() }
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    Column(modifier.fillMaxSize().libraryTexture()) {
        if (sortedSongs.isNotEmpty()) {
            SmartisanSongsSortHeader(selectedSortIndex, { selectedSortIndex = it })
            Column(Modifier.graphicsLayer { alpha = if (editMode) .22f else 1f }) {
                LibraryPlayActions(
                    !editMode,
                    { browser.replaceQueueAndPlay(sortedSongs) },
                    { browser.replaceQueueAndPlayShuffled(sortedSongs) },
                )
            }
            Box(Modifier.weight(1f).padding(bottom = playbackBarOverlayHeight)) {
                LazyColumn(
                    Modifier.fillMaxSize()
                        .onGloballyPositioned { listOrigin = it.positionInRoot() }
                        .smartisanVerticalScrollbar(list)
                        .smartisanSlideSelection(
                            enabled = editMode,
                            itemAt = { point ->
                                list.layoutInfo.visibleItemsInfo
                                    .firstOrNull {
                                        point.y >= it.offset && point.y < it.offset + it.size
                                    }
                                    ?.index
                            },
                            keyAt = {
                                (rows.getOrNull(it) as? SongListRow.Song)?.mediaItem?.mediaId
                            },
                            selectedKeys = selectedSongIds,
                            onSelectionChange = onSongSelectionChange,
                            canStart = { point ->
                                val row =
                                    list.layoutInfo.visibleItemsInfo.firstOrNull {
                                        point.y >= it.offset && point.y < it.offset + it.size
                                    }
                                val mediaId =
                                    (rows.getOrNull(row?.index ?: -1) as? SongListRow.Song)
                                        ?.mediaItem
                                        ?.mediaId
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
                                        rows.getOrNull(it.index) is SongListRow.Song
                                    }
                                (if (top) visible.firstOrNull() else visible.lastOrNull())?.index
                            },
                        ),
                    state = list,
                ) {
                    itemsIndexed(
                        rows,
                        key = { _, row ->
                            when (row) {
                                is SongListRow.Header -> "header:${row.key}"
                                is SongListRow.Song -> "song:${row.mediaItem.mediaId}"
                            }
                        },
                        contentType = { _, row ->
                            if (row is SongListRow.Header) "header" else "song"
                        },
                    ) { _, row ->
                        when (row) {
                            is SongListRow.Header ->
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(26.dp)
                                        .smartisanPainterBackground(
                                            rememberSmartisanDrawablePainter(
                                                R.drawable.smartlist_header_bg
                                            )
                                        )
                                        .padding(
                                            start =
                                                dimensionResource(
                                                    R.dimen.listview_items_margin_left
                                                )
                                        ),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    BasicText(
                                        row.key.title(context),
                                        style =
                                            TextStyle(
                                                color = colorResource(R.color.title_text_color),
                                                fontSize = 13.5.sp,
                                                platformStyle =
                                                    PlatformTextStyle(includeFontPadding = true),
                                            ),
                                    )
                                }
                            is SongListRow.Song -> {
                                SmartisanSwipeDeleteRow(
                                    row.mediaItem.mediaId,
                                    !editMode,
                                    openKey,
                                    { openKey = it },
                                    onDelete = { close ->
                                        onRequestSongDeleteConfirmation(
                                            setOf(row.mediaItem.mediaId),
                                            close,
                                        )
                                    },
                                    onSwipeActivity = { swipeActive = it },
                                ) {
                                    SmartisanSongRow(
                                        row.mediaItem,
                                        { browser.replaceQueueAndPlay(sortedSongs, row.songIndex) },
                                        onTrackMoreClick,
                                        editMode = editMode,
                                        selected = row.mediaItem.mediaId in selectedSongIds,
                                        onSelectionChange = {
                                            onSongSelectionChange(row.mediaItem.mediaId, it)
                                        },
                                        displayMode = displayMode,
                                        playback = playback,
                                        onCheckboxBoundsChanged = { bounds ->
                                            val id = row.mediaItem.mediaId
                                            if (bounds == null) checkboxBounds.remove(id)
                                            else checkboxBounds[id] = bounds
                                        },
                                        contentEndInset =
                                            if (selectedSortIndex == 0 && sortedSongs.size > 30)
                                                with(density) {
                                                    (quickBarWidth - moreWidth / 3)
                                                        .coerceAtLeast(0f)
                                                        .toDp()
                                                }
                                            else 0.dp,
                                    )
                                }
                                LibraryDivider()
                            }
                        }
                    }
                    item("footer") { LibraryFooter(R.plurals.track_count, sortedSongs.size) }
                }
                if (selectedSortIndex == 0 && sortedSongs.size > 30 && !swipeActive) {
                    SmartisanQuickBar(
                        onLetter = { letter ->
                            val index = rows.indexOfFirst {
                                it is SongListRow.Header && it.key == SongHeaderKey.Name(letter)
                            }
                            if (index >= 0) scope.launch { list.scrollToItem(index) }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        } else if (libraryLoaded) {
            com.smartisan.music.ui.components.SmartisanEmptyHint(
                R.drawable.blank_song,
                stringResource(R.string.no_song),
                subtitle = stringResource(R.string.show_song),
            )
        }
    }
}
