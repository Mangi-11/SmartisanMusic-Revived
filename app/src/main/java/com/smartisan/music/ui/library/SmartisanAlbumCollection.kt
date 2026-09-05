package com.smartisan.music.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.artwork.AlbumArtworkLoader
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanCheckboxBounds
import com.smartisan.music.ui.components.smartisanCheckboxHit
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanSlideSelection
import com.smartisan.music.ui.components.smartisanVerticalScrollbar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

internal data class LibraryAlbumEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val album: AlbumSummary?,
    val songs: List<MediaItem>,
)

/** Album and artist pages share their calibrated list/tile geometry and switch animation. */
@Composable
internal fun SmartisanAlbumCollection(
    entries: List<LibraryAlbumEntry>,
    active: Boolean,
    viewMode: AlbumViewMode,
    currentMediaId: String?,
    onClick: (LibraryAlbumEntry) -> Unit,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onSelectionChange: (String, Boolean) -> Unit = { _, _ -> },
    artistAlbums: Boolean = false,
    revealInitialGrid: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    val loader = rememberAlbumArtworkLoader()
    val list = rememberLazyListState()
    val grid = rememberLazyGridState()
    val elapsed = remember { Animatable(10000f) }
    var previousMode by remember { mutableStateOf(viewMode) }
    var firstReveal by remember { mutableStateOf(revealInitialGrid) }
    var flyingTiles by remember { mutableStateOf(emptyList<FlyingAlbumTile>()) }
    var gridFirstIndex by remember { mutableIntStateOf(0) }
    var gridRevealing by remember { mutableStateOf(false) }
    val gridCoverBounds = remember { mutableMapOf<String, Rect>() }
    val checkboxBounds = remember { mutableMapOf<String, Rect>() }
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var collectionOrigin by remember { mutableStateOf(Offset.Zero) }
    var listTargets by remember { mutableStateOf(emptyMap<Int, Int>()) }
    val entrance =
        rememberLibraryListEntrance(entries, active && viewMode == AlbumViewMode.List) {
            list.layoutInfo.visibleItemsInfo.size
        }
    val density = LocalDensity.current
    val tilePadding = dimensionResource(R.dimen.gridview_padding)
    val rowSize = dimensionResource(R.dimen.listview_item_height)
    val listCoverSize = dimensionResource(R.dimen.album_list_item_image_width)
    val listCoverLeft = dimensionResource(R.dimen.listview_items_margin_left)
    val checkbox = rememberSmartisanDrawablePainter(R.drawable.check_box_selector)
    val checkboxStartArea =
        with(density) { dimensionResource(R.dimen.check_box_margin_left).toPx() } +
            checkbox.intrinsicSize.width
    LaunchedEffect(entries) {
        gridCoverBounds.keys.retainAll(entries.mapTo(mutableSetOf()) { it.id })
    }

    LaunchedEffect(viewMode, active) {
        gridRevealing = false
        if (!active) {
            flyingTiles = emptyList()
            previousMode = viewMode
            return@LaunchedEffect
        }
        val changed = previousMode != viewMode
        val oldMode = previousMode
        previousMode = viewMode
        if (viewMode == AlbumViewMode.Tile && (changed || firstReveal)) {
            firstReveal = false
            if (changed)
                grid.scrollToItem(
                    list.firstVisibleItemIndex.coerceAtMost((entries.size - 1).coerceAtLeast(0))
                )
            withFrameNanos {}
            gridFirstIndex = grid.firstVisibleItemIndex
            flyingTiles = emptyList()
            elapsed.snapTo(0f)
            gridRevealing = true
            val duration = 300 + (grid.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(0) * 40
            elapsed.animateTo(duration.toFloat(), tween(duration, easing = LinearEasing))
            gridRevealing = false
        } else if (changed && oldMode == AlbumViewMode.Tile) {
            val snapshots =
                grid.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    val id = entries.getOrNull(item.index)?.id ?: return@mapNotNull null
                    gridCoverBounds[id]?.let { bounds ->
                        FlyingAlbumTile(
                            item.index,
                            item.offset,
                            bounds.translate(-collectionOrigin),
                        )
                    }
                }
            elapsed.snapTo(0f)
            flyingTiles = snapshots
            list.scrollToItem(
                grid.firstVisibleItemIndex.coerceAtMost((entries.size - 1).coerceAtLeast(0))
            )
            withFrameNanos {}
            listTargets = list.layoutInfo.visibleItemsInfo.associate { it.index to it.offset }
            val duration = 150 + (snapshots.size - 1).coerceAtLeast(0) * 10
            elapsed.animateTo(duration.toFloat(), tween(duration, easing = LinearEasing))
            flyingTiles = emptyList()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned { collectionOrigin = it.positionInRoot() }
            .background(colorResource(R.color.page_background))
    ) {
        if (!active) return@Box
        if (viewMode == AlbumViewMode.List) {
            LazyColumn(
                state = list,
                modifier =
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
                            keyAt = { entries.getOrNull(it)?.id },
                            selectedKeys = selectedIds,
                            onSelectionChange = onSelectionChange,
                            canStart = { point ->
                                val index =
                                    list.layoutInfo.visibleItemsInfo
                                        .firstOrNull {
                                            point.y >= it.offset && point.y < it.offset + it.size
                                        }
                                        ?.index
                                val key = index?.let { entries.getOrNull(it)?.id }
                                smartisanCheckboxHit(
                                    point + listOrigin,
                                    key?.let { checkboxBounds[it] },
                                    touchSlop,
                                )
                            },
                            scrollBy = { list.scrollBy(it) },
                            edgeItemAt = { top ->
                                list.layoutInfo.visibleItemsInfo
                                    .filter { it.index in entries.indices }
                                    .let {
                                        if (top) it.firstOrNull()?.index else it.lastOrNull()?.index
                                    }
                            },
                        )
                        .graphicsLayer {
                            alpha = if (flyingTiles.isEmpty()) 1f else 0f
                        },
            ) {
                itemsIndexed(entries, key = { _, item -> item.id }) { index, item ->
                    LibraryAlbumListRow(
                        item,
                        loader,
                        editMode,
                        item.id in selectedIds,
                        item.songs.any { it.mediaId == currentMediaId },
                        artistAlbums,
                        modifier =
                            Modifier.libraryListEntrance(entrance) {
                                index - list.firstVisibleItemIndex
                            },
                        onCheckboxBounds = { bounds ->
                            if (bounds == null) checkboxBounds.remove(item.id)
                            else checkboxBounds[item.id] = bounds
                        },
                        onClick = {
                            if (editMode) onSelectionChange(item.id, item.id !in selectedIds)
                            else onClick(item)
                        },
                    )
                    LibraryDivider()
                }
                if (footer != null) item(key = "album-footer") { footer() }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(integerResource(R.integer.gridview_columns)),
                state = grid,
                modifier =
                    Modifier.fillMaxSize()
                        .smartisanVerticalScrollbar(grid)
                        .smartisanSlideSelection(
                            enabled = editMode,
                            itemAt = { point ->
                                grid.layoutInfo.visibleItemsInfo
                                    .firstOrNull {
                                        point.x >= it.offset.x &&
                                            point.x < it.offset.x + it.size.width &&
                                            point.y >= it.offset.y &&
                                            point.y < it.offset.y + it.size.height
                                    }
                                    ?.index
                            },
                            keyAt = { entries.getOrNull(it)?.id },
                            selectedKeys = selectedIds,
                            onSelectionChange = onSelectionChange,
                            canStart = { true },
                            scrollBy = { grid.scrollBy(it) },
                            horizontalActivation = true,
                            edgeItemAt = { top ->
                                grid.layoutInfo.visibleItemsInfo
                                    .filter { it.index in entries.indices }
                                    .let {
                                        if (top) it.firstOrNull()?.index else it.lastOrNull()?.index
                                    }
                            },
                            activationThreshold = { point ->
                                grid.layoutInfo.visibleItemsInfo
                                    .firstOrNull {
                                        point.x >= it.offset.x &&
                                            point.x < it.offset.x + it.size.width &&
                                            point.y >= it.offset.y &&
                                            point.y < it.offset.y + it.size.height
                                    }
                                    ?.let { Offset(it.size.width / 2f, it.size.height / 2f) }
                                    ?: Offset.Zero
                            },
                        ),
                contentPadding =
                    PaddingValues(
                        start = dimensionResource(R.dimen.gridview_margin),
                        end = dimensionResource(R.dimen.gridview_margin),
                        bottom =
                            if (artistAlbums) 0.dp else dimensionResource(R.dimen.gridview_margin),
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(dimensionResource(R.dimen.gridview_horizontalSpacing)),
                verticalArrangement =
                    Arrangement.spacedBy(dimensionResource(R.dimen.gridview_verticalSpacing)),
            ) {
                itemsIndexed(entries, key = { _, item -> item.id }) { index, item ->
                    LibraryAlbumTile(
                        item,
                        loader,
                        editMode,
                        item.id in selectedIds,
                        item.songs.any { it.mediaId == currentMediaId },
                        coverModifier =
                            Modifier.onGloballyPositioned {
                                gridCoverBounds[item.id] =
                                    Rect(
                                        it.positionInRoot(),
                                        Size(it.size.width.toFloat(), it.size.height.toFloat()),
                                    )
                            },
                        onClick = {
                            if (editMode) onSelectionChange(item.id, item.id !in selectedIds)
                            else onClick(item)
                        },
                        modifier =
                            Modifier.graphicsLayer {
                                val fraction =
                                    if (!gridRevealing) 1f
                                    else
                                        ((elapsed.value -
                                                (index - gridFirstIndex).coerceAtLeast(0) * 40) /
                                                300f)
                                            .coerceIn(0f, 1f)
                                alpha = ((cos((fraction + 1f) * PI) / 2.0) + .5).toFloat()
                            },
                    )
                }
            }
        }
        flyingTiles.forEachIndexed { order, tile ->
            val item = entries.getOrNull(tile.index) ?: return@forEachIndexed
            val paddingPx = with(density) { tilePadding.toPx() }
            val scale =
                with(density) { listCoverSize.toPx() } /
                    (tile.cover.width - paddingPx * 2).coerceAtLeast(1f)
            val rowTop = listTargets[tile.index]
            val target =
                if (rowTop != null)
                    Offset(
                        with(density) { listCoverLeft.toPx() } +
                            if (editMode) checkboxStartArea else 0f,
                        rowTop + with(density) { (rowSize - listCoverSize).toPx() / 2 },
                    ) - Offset(paddingPx * scale, paddingPx * scale)
                else {
                    val lastTop = listTargets.values.maxOrNull()?.toFloat() ?: 0f
                    val translationY =
                        when {
                            tile.index > (listTargets.keys.maxOrNull() ?: -1) -> lastTop
                            tile.index < (listTargets.keys.minOrNull() ?: 0) -> -lastTop / 2f
                            else -> -tile.offset.y.toFloat()
                        }
                    Offset(
                        (tile.cover.left - tile.offset.x) * scale,
                        tile.offset.y + (tile.cover.top - tile.offset.y) * scale + translationY,
                    )
                }
            LibraryAlbumCover(
                item,
                loader,
                tile = true,
                modifier =
                    Modifier.absoluteOffset {
                            IntOffset(tile.cover.left.roundToInt(), tile.cover.top.roundToInt())
                        }
                        .size(
                            with(density) { tile.cover.width.toDp() },
                            with(density) { tile.cover.height.toDp() },
                        )
                        .graphicsLayer {
                            val fraction = ((elapsed.value - order * 10) / 150f).coerceIn(0f, 1f)
                            val eased = 1f - (1f - fraction) * (1f - fraction)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = 1 + (scale - 1) * eased
                            scaleY = scaleX
                            translationX = (target.x - tile.cover.left) * eased
                            translationY = (target.y - tile.cover.top) * eased
                        },
            )
        }
    }
}

private data class FlyingAlbumTile(val index: Int, val offset: IntOffset, val cover: Rect)

@Composable
private fun LibraryAlbumListRow(
    item: LibraryAlbumEntry,
    loader: AlbumArtworkLoader,
    editMode: Boolean,
    selected: Boolean,
    playing: Boolean,
    artistAlbums: Boolean,
    onClick: () -> Unit,
    onCheckboxBounds: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkbox =
        rememberSmartisanDrawablePainter(R.drawable.check_box_selector, checked = selected)
    val checkboxWidth = with(LocalDensity.current) { checkbox.intrinsicSize.width.toDp() }
    val editWidth by
        animateDpAsState(
            if (editMode) checkboxWidth + dimensionResource(R.dimen.check_box_margin_left)
            else 0.dp,
            tween(200),
            label = "Album selection inset",
        )
    LibrarySummaryRow(
        item.title,
        item.subtitle,
        onClick,
        modifier = modifier.semantics { if (editMode) this.selected = selected },
        titleColor =
            colorResource(if (playing) R.color.playing_red else R.color.setting_item_text_color),
        leadingContent = {
            Box(Modifier.width(editWidth).clipToBounds(), contentAlignment = Alignment.CenterEnd) {
                if (editWidth > 0.dp) {
                    Image(
                        checkbox,
                        null,
                        Modifier.width(checkboxWidth).smartisanCheckboxBounds(onCheckboxBounds),
                    )
                }
            }
            LibraryAlbumCover(
                item,
                loader,
                tile = false,
                modifier =
                    Modifier.padding(start = dimensionResource(R.dimen.listview_items_margin_left))
                        .size(dimensionResource(R.dimen.album_list_item_image_width)),
            )
        },
        textInset =
            dimensionResource(
                if (artistAlbums) R.dimen.listview_items_margin_left
                else R.dimen.common_padding_left
            ),
    )
}

@Composable
private fun LibraryAlbumTile(
    item: LibraryAlbumEntry,
    loader: AlbumArtworkLoader,
    editMode: Boolean,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tileSize = dimensionResource(R.dimen.gridview_item_ccontainer_height)
    Column(
        modifier
            .padding(top = dimensionResource(R.dimen.gridview_padding_top2))
            .clickable(
                interaction,
                null,
                role = if (editMode) Role.Checkbox else Role.Button,
                onClick = smartisanClick(onClick),
            )
            .semantics { if (editMode) this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(tileSize).then(coverModifier)) {
            LibraryAlbumCover(
                item,
                loader,
                tile = true,
                pressed = pressed,
                modifier = Modifier.fillMaxSize(),
            )
            if (editMode) {
                Image(
                    rememberSmartisanDrawablePainter(R.drawable.albums_selected_large_empty),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                if (selected)
                    Image(
                        rememberSmartisanDrawablePainter(R.drawable.albums_selected_large),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
            }
        }
        BasicText(
            item.title,
            Modifier.width(tileSize),
            style =
                TextStyle(
                    color =
                        colorResource(if (playing) R.color.playing_red else R.color.text_emphasis),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LibraryAlbumCover(
    item: LibraryAlbumEntry,
    loader: AlbumArtworkLoader,
    tile: Boolean,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
) {
    val padding = if (tile) dimensionResource(R.dimen.gridview_padding) else 0.dp
    val size =
        dimensionResource(
            if (tile) R.dimen.gridview_item_ccontainer_height
            else R.dimen.album_list_item_image_width
        )
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    Box(modifier) {
        SmartisanAlbumArtwork(
            item.album,
            sizePx,
            if (item.album == null) R.drawable.noalbumcover_all_songs2
            else if (tile) R.drawable.noalbumcover_220 else R.drawable.noalbumcover_120,
            Modifier.fillMaxSize().padding(padding),
            loader,
        )
        val mask =
            if (tile) {
                if (item.album == null) R.drawable.no_mask_albumcover_tile_selector
                else R.drawable.mask_albumcover_tile_selector
            } else if (item.album == null) null else R.drawable.mask_albumcover_list
        if (mask != null)
            Image(
                rememberSmartisanDrawablePainter(mask, pressed = pressed),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
    }
}
