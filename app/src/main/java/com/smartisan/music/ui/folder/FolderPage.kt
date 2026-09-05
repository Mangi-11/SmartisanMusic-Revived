package com.smartisan.music.ui.folder

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.library.LibraryExclusions
import com.smartisan.music.data.library.LibraryExclusionsStore
import com.smartisan.music.platform.text.HanLatinTransliterator
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.withSelection
import com.smartisan.music.ui.library.LibraryBlank
import com.smartisan.music.ui.library.LibraryDivider
import com.smartisan.music.ui.library.LibraryFooter
import com.smartisan.music.ui.library.LibraryIconButton
import com.smartisan.music.ui.library.LibraryPlayActions
import com.smartisan.music.ui.library.libraryDuration
import com.smartisan.music.ui.library.libraryListEntrance
import com.smartisan.music.ui.library.libraryTexture
import com.smartisan.music.ui.library.rememberLibraryListEntrance
import com.smartisan.music.ui.shell.PageStackTransition
import com.smartisan.music.ui.shell.PredictiveBackHandler
import com.smartisan.music.ui.shell.PredictiveBackState
import com.smartisan.music.ui.shell.rememberPredictiveBackState
import com.smartisan.music.ui.shell.titlebar.TitleBarShadow
import com.smartisan.music.ui.shell.titlebar.TitleBarTransition
import com.smartisan.music.ui.songs.SmartisanSongRow
import com.smartisan.music.ui.songs.rememberSongPlaybackState
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FolderStorageLabel = "Phone Storage"
private const val FolderVisibilityAnimationMillis = 300L

private data class FolderTarget(
    val key: String,
    val title: String,
)

@Composable
internal fun FolderPage(
    active: Boolean,
    libraryRefreshVersion: Int,
    libraryRefreshing: Boolean,
    onClose: (() -> Unit)?,
    closePredictiveBackState: PredictiveBackState?,
    onRefreshLibrary: () -> Unit,
    onMediaIdsHidden: (Set<String>) -> Unit,
    onRequestDeleteMediaIds: (Set<String>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val browser = LocalPlaybackBrowser.current
    val audioLibrary =
        remember(context.applicationContext) {
            LocalAudioLibrary(context.applicationContext)
        }
    val exclusionsStore =
        remember(context.applicationContext) {
            LibraryExclusionsStore(context.applicationContext)
        }
    val directoryTitle = stringResource(R.string.tab_directory)
    val exclusions by exclusionsStore.exclusions.collectAsState(initial = LibraryExclusions())
    val hasPermission =
        remember(context) {
            hasAudioPermission(context)
        }
    var mediaItems by remember(audioLibrary) { mutableStateOf(emptyList<MediaItem>()) }
    var target by remember { mutableStateOf<FolderTarget?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var selectedDirectoryKeys by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val detailPredictiveBackState = rememberPredictiveBackState()

    LaunchedEffect(active, hasPermission, libraryRefreshVersion, audioLibrary) {
        if (!active || !hasPermission) {
            mediaItems = emptyList()
            return@LaunchedEffect
        }
        mediaItems =
            withContext(Dispatchers.IO) {
                audioLibrary.getAudioItems(forceRefresh = libraryRefreshVersion > 0)
            }
    }

    val allDirectories =
        remember(mediaItems, exclusions) {
            buildDirectoryEntries(
                mediaItems = mediaItems,
                exclusions = exclusions,
                storageLabel = FolderStorageLabel,
            )
        }

    PredictiveBackHandler(
        enabled = active && target != null,
        state = detailPredictiveBackState,
    ) {
        target = null
    }
    BackHandler(enabled = active && target == null && editMode) {
        editMode = false
        selectedDirectoryKeys = emptySet()
    }
    if (closePredictiveBackState != null && onClose != null) {
        PredictiveBackHandler(
            enabled = active && target == null && !editMode,
            state = closePredictiveBackState,
            onBack = onClose,
        )
    } else if (onClose != null) {
        BackHandler(enabled = active && target == null && !editMode) {
            onClose()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colorResource(R.color.page_background))) {
        val titleAreaHeight =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                dimensionResource(R.dimen.title_bar_height)
        val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
        val handleBack: () -> Unit = {
            when {
                target != null -> target = null
                editMode -> {
                    editMode = false
                    selectedDirectoryKeys = emptySet()
                }
                else -> {
                    onClose?.invoke()
                }
            }
        }
        val enterEdit = {
            editMode = true
            selectedDirectoryKeys = emptySet()
        }
        val deleteSelected = {
            if (selectedDirectoryKeys.isNotEmpty()) {
                showDeleteConfirm = true
            }
        }
        Column(modifier = Modifier.fillMaxSize()) {
            TitleBarTransition(
                secondaryKey = target,
                modifier = Modifier.fillMaxWidth().height(titleAreaHeight),
                label = "folder title stack",
                predictiveBackProgress = detailPredictiveBackState.progress,
                predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                primaryContent = {
                    FolderTitleBar(
                        modifier = Modifier.fillMaxSize(),
                        title = directoryTitle,
                        editMode = editMode,
                        selectedCount = selectedDirectoryKeys.size,
                        libraryRefreshing = libraryRefreshing,
                        onBack = handleBack,
                        showRootBack = onClose != null,
                        onEnterEdit = enterEdit,
                        onDeleteSelected = deleteSelected,
                        onRefreshLibrary = onRefreshLibrary,
                    )
                },
                secondaryContent = { folderTarget ->
                    FolderTitleBar(
                        modifier = Modifier.fillMaxSize(),
                        title = folderTarget.title,
                        editMode = false,
                        selectedCount = 0,
                        libraryRefreshing = libraryRefreshing,
                        showRightActions = false,
                        onBack = handleBack,
                        showRootBack = true,
                        onEnterEdit = enterEdit,
                        onDeleteSelected = deleteSelected,
                        onRefreshLibrary = onRefreshLibrary,
                    )
                },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PageStackTransition(
                    secondaryKey = target,
                    modifier = Modifier.fillMaxSize(),
                    label = "folder detail stack",
                    predictiveBackProgress = detailPredictiveBackState.progress,
                    predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                    onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                    primaryContent = {
                        FolderRootPage(
                            active = active,
                            directories = allDirectories,
                            editMode = editMode,
                            selectedDirectoryKeys = selectedDirectoryKeys,
                            onDirectoryClick = { entry ->
                                if (editMode) {
                                    selectedDirectoryKeys = selectedDirectoryKeys.toggle(entry.key)
                                } else {
                                    target =
                                        FolderTarget(
                                            key = entry.key,
                                            title = entry.name,
                                        )
                                }
                            },
                            onDirectorySelectionChange = { directoryKey, selected ->
                                selectedDirectoryKeys =
                                    selectedDirectoryKeys.withSelection(directoryKey, selected)
                            },
                            onDirectoryVisibilityChange = { entry, hidden ->
                                val affectedMediaIds =
                                    if (hidden) {
                                        mediaIdsInDirectory(
                                            mediaItems = mediaItems,
                                            directoryKey = entry.key,
                                        )
                                    } else {
                                        emptySet()
                                    }
                                scope.launch {
                                    delay(FolderVisibilityAnimationMillis)
                                    exclusionsStore.setDirectoryKeysHidden(
                                        directoryKeys = setOf(entry.key),
                                        hidden = hidden,
                                    )
                                    if (affectedMediaIds.isNotEmpty()) {
                                        onMediaIdsHidden(affectedMediaIds)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    secondaryContent = { folderTarget ->
                        val songs =
                            remember(mediaItems, exclusions, folderTarget.key) {
                                filterMediaItemsForDirectory(
                                        mediaItems = mediaItems,
                                        directoryKey = folderTarget.key,
                                        exclusions = exclusions,
                                    )
                                    .sortedForFolder()
                            }
                        FolderDetailPage(
                            active = active && target == folderTarget,
                            tracks = songs,
                            browser = browser,
                            onTrackMoreClick = onTrackMoreClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
        TitleBarShadow(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .offset(y = titleAreaHeight)
                    .fillMaxWidth()
                    .height(titleShadowHeight)
                    .zIndex(1f)
        )
    }

    if (showDeleteConfirm)
        SmartisanDeleteConfirmation(
            title = stringResource(R.string.dialog_remove_song_by_dor),
            onDismiss = {
                showDeleteConfirm = false
            },
            onConfirm = {
                val keys = selectedDirectoryKeys
                if (keys.isEmpty()) {
                    showDeleteConfirm = false
                    return@SmartisanDeleteConfirmation
                }
                val affectedMediaIds =
                    keys
                        .flatMap { key ->
                            mediaIdsInDirectory(mediaItems = mediaItems, directoryKey = key)
                        }
                        .toSet()
                onRequestDeleteMediaIds(affectedMediaIds)
                selectedDirectoryKeys = emptySet()
                editMode = false
                showDeleteConfirm = false
            },
        )
}

private fun List<MediaItem>.sortedForFolder(): List<MediaItem> {
    return sortedWith(
        compareBy<MediaItem> { item ->
                item.folderSortBucket()
            }
            .thenBy { item ->
                item.folderSortKey()
            }
    )
}

private fun MediaItem.folderSortTitle(): String {
    return mediaMetadata.displayTitle?.toString() ?: mediaMetadata.title?.toString() ?: ""
}

private fun MediaItem.folderSortKey(): String {
    return FolderTitleNormalizer.normalize(folderSortTitle())
}

private fun MediaItem.folderSortBucket(): String {
    val letter = folderSectionLetter()
    return if (letter == "#") "ZZZ" else letter
}

private fun MediaItem.folderSectionLetter(): String {
    val firstLetter =
        folderSortKey().firstOrNull { char ->
            char.isLetterOrDigit()
        } ?: return "#"
    val upper = firstLetter.uppercaseChar()
    return if (upper in 'A'..'Z') upper.toString() else "#"
}

private object FolderTitleNormalizer {
    private val combiningMarks = "\\p{Mn}+".toRegex()

    fun normalize(title: String): String {
        val trimmed = title.trim()
        val transliterated = HanLatinTransliterator.transliterate(trimmed)
        return Normalizer.normalize(transliterated, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun FolderTitleBar(
    title: String,
    editMode: Boolean,
    selectedCount: Int,
    libraryRefreshing: Boolean,
    modifier: Modifier = Modifier,
    showRightActions: Boolean = true,
    onBack: () -> Unit,
    showRootBack: Boolean,
    onEnterEdit: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRefreshLibrary: () -> Unit,
) {
    SmartisanTitleBar(
        title,
        modifier,
        showShadow = false,
        navigationIcon =
            if (editMode || showRootBack)
                SmartisanTitleBarAction(
                    if (editMode) R.drawable.standard_icon_cancel_selector
                    else R.drawable.standard_icon_back_selector,
                    stringResource(if (editMode) R.string.cancel else R.string.back),
                    onBack,
                )
            else null,
        actions =
            when {
                editMode ->
                    listOf(
                        SmartisanTitleBarAction(
                            R.drawable.titlebar_btn_delete_selector,
                            stringResource(R.string.dialog_delete_conform),
                            onDeleteSelected,
                            enabled = selectedCount > 0,
                        )
                    )
                showRightActions ->
                    listOf(
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_multi_select_selector,
                            stringResource(R.string.edit),
                            onEnterEdit,
                        ),
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_refresh_selector,
                            stringResource(R.string.library_rescan_full),
                            onRefreshLibrary,
                            enabled = !libraryRefreshing,
                        ),
                    )
                else -> emptyList()
            },
    )
}

@Composable
private fun FolderRootPage(
    active: Boolean,
    directories: List<DirectoryEntry>,
    editMode: Boolean,
    selectedDirectoryKeys: Set<String>,
    onDirectoryClick: (DirectoryEntry) -> Unit,
    onDirectorySelectionChange: (String, Boolean) -> Unit,
    onDirectoryVisibilityChange: (DirectoryEntry, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val display =
        remember(directories) { filterDirectoryEntriesForDisplay(directories, editMode = true) }
    var expanded by remember { mutableStateOf(editMode) }
    LaunchedEffect(editMode) {
        delay(200)
        expanded = editMode
    }
    val visibleCount = if (editMode) display.size else display.count { !it.hidden }
    val checkboxBounds = remember { mutableMapOf<String, Rect>() }
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    Box(modifier.fillMaxSize().libraryTexture()) {
        if (!active) return@Box
        if (visibleCount == 0) {
            LibraryBlank(
                R.drawable.blank_folder,
                stringResource(R.string.no_folder),
                stringResource(R.string.show_folder),
            )
        } else {
            LazyColumn(
                state = list,
                modifier =
                    Modifier.fillMaxSize()
                        .onGloballyPositioned { listOrigin = it.positionInRoot() }
                        .smartisanSlideSelection(
                            enabled = editMode,
                            itemAt = { point ->
                                list.layoutInfo.visibleItemsInfo
                                    .firstOrNull {
                                        point.y >= it.offset && point.y < it.offset + it.size
                                    }
                                    ?.index
                            },
                            keyAt = { display.getOrNull(it)?.key },
                            selectedKeys = selectedDirectoryKeys,
                            onSelectionChange = onDirectorySelectionChange,
                            canStart = { point ->
                                val index =
                                    list.layoutInfo.visibleItemsInfo
                                        .firstOrNull {
                                            point.y >= it.offset && point.y < it.offset + it.size
                                        }
                                        ?.index
                                val key = index?.let { display.getOrNull(it)?.key }
                                smartisanCheckboxHit(
                                    point + listOrigin,
                                    key?.let { checkboxBounds[it] },
                                    touchSlop,
                                )
                            },
                            scrollBy = { list.scrollBy(it) },
                            edgeItemAt = { top ->
                                list.layoutInfo.visibleItemsInfo
                                    .filter { it.index in display.indices && it.size > 0 }
                                    .let {
                                        if (top) it.firstOrNull()?.index else it.lastOrNull()?.index
                                    }
                            },
                        ),
            ) {
                itemsIndexed(display, key = { _, entry -> entry.key }) { index, entry ->
                    FolderDirectoryRow(
                        entry,
                        editMode,
                        expanded,
                        entry.key in selectedDirectoryKeys,
                        showDivider = display.drop(index + 1).any { expanded || !it.hidden },
                        onClick = { onDirectoryClick(entry) },
                        onCheckboxBounds = { bounds ->
                            if (bounds == null) checkboxBounds.remove(entry.key)
                            else checkboxBounds[entry.key] = bounds
                        },
                        onVisibilityChange = { hidden ->
                            onDirectoryVisibilityChange(entry, hidden)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderDirectoryRow(
    entry: DirectoryEntry,
    editMode: Boolean,
    expanded: Boolean,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onCheckboxBounds: (Rect?) -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val rowHeight = dimensionResource(R.dimen.listview_item_height)
    val height by
        animateDpAsState(
            if (entry.hidden && !expanded) 0.dp else rowHeight,
            tween(200),
            label = "Hidden directory height",
        )
    val editFraction by
        animateFloatAsState(if (editMode) 1f else 0f, tween(200), label = "Directory edit controls")
    var pendingHidden by remember(entry.key) { mutableStateOf<Boolean?>(null) }
    val eyeTime = remember(entry.key) { Animatable(300f) }
    LaunchedEffect(entry.hidden) { if (pendingHidden == entry.hidden) pendingHidden = null }
    val displayedHidden = pendingHidden ?: entry.hidden
    val brightAlpha by
        animateFloatAsState(
            if ((editMode || expanded) && displayedHidden) .3f else 1f,
            tween(300),
            label = "Directory visibility",
        )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val checkbox =
        rememberSmartisanDrawablePainter(
            R.drawable.selector_check_box_red,
            checked = selected,
            pressed = pressed,
        )
    val checkboxWidth = with(LocalDensity.current) { checkbox.intrinsicSize.width.toDp() }
    val count = if (editMode || expanded) entry.totalCount else entry.visibleCount
    val countText = pluralStringResource(R.plurals.album_track_count, count, count)
    Box(
        Modifier.fillMaxWidth()
            .height(height)
            .clipToBounds()
            .then(if (height == 0.dp) Modifier.clearAndSetSemantics {} else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .requiredHeight(rowHeight)
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
            Box(
                Modifier.width(
                        (checkboxWidth + dimensionResource(R.dimen.check_box_margin_left)) *
                            editFraction
                    )
                    .clipToBounds(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (editFraction > 0) {
                    Image(
                        checkbox,
                        null,
                        Modifier.requiredWidth(checkboxWidth)
                            .smartisanCheckboxBounds(onCheckboxBounds)
                            .graphicsLayer { alpha = editFraction },
                    )
                }
            }
            Column(
                Modifier.weight(1f)
                    .padding(start = dimensionResource(R.dimen.listview_items_margin_left))
                    .graphicsLayer { alpha = brightAlpha }
            ) {
                BasicText(
                    entry.name,
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
                    "$countText  ${entry.displayPath}",
                    Modifier.padding(top = 3.dp),
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
            Box(
                Modifier.padding(end = dimensionResource(R.dimen.listview_items_margin_right)),
                contentAlignment = Alignment.Center,
            ) {
                if (editFraction < 1)
                    Image(
                        rememberSmartisanDrawablePainter(
                            R.drawable.arrow3_selector,
                            pressed = pressed,
                        ),
                        null,
                        Modifier.graphicsLayer {
                            alpha = 1 - editFraction
                            translationX = size.width * editFraction
                        },
                    )
                if (editFraction > 0) {
                    val frame =
                        if (pendingHidden != null && eyeTime.value < 160f) {
                            val index = (eyeTime.value / 10).toInt().coerceIn(0, 15)
                            FolderEyeFrames[if (pendingHidden == true) index else 15 - index]
                        } else if (displayedHidden) R.drawable.eye_icon_0016
                        else R.drawable.eye_icon_0001
                    LibraryIconButton(
                        frame,
                        stringResource(R.string.hide_dir_content_description),
                        onClick = {
                            val hidden = !entry.hidden
                            pendingHidden = hidden
                            Toast.makeText(
                                    context.applicationContext,
                                    resources.getString(
                                        if (hidden) R.string.hiden_dir else R.string.shown_dir
                                    ),
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                            onVisibilityChange(hidden)
                            scope.launch {
                                eyeTime.snapTo(0f)
                                eyeTime.animateTo(300f, tween(300, easing = LinearEasing))
                            }
                        },
                        modifier =
                            Modifier.graphicsLayer {
                                alpha = editFraction
                                translationX = size.width * (1 - editFraction)
                            },
                        enabled = pendingHidden == null,
                    )
                }
            }
        }
        if (showDivider) LibraryDivider(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun FolderDetailPage(
    active: Boolean,
    tracks: List<MediaItem>,
    browser: Player?,
    onTrackMoreClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val playback = rememberSongPlaybackState(browser)
    val entrance =
        rememberLibraryListEntrance(tracks, active) { list.layoutInfo.visibleItemsInfo.size }
    Column(modifier.fillMaxSize().libraryTexture()) {
        if (!active) return@Column
        LibraryPlayActions(
            tracks.isNotEmpty(),
            { browser.replaceQueueAndPlay(tracks) },
            { browser.replaceQueueAndPlayShuffled(tracks) },
        )
        if (tracks.isEmpty()) {
            LibraryBlank(
                R.drawable.blank_song,
                stringResource(R.string.no_song),
                stringResource(R.string.show_song),
                Modifier.weight(1f),
            )
        } else {
            LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(tracks, key = { index, item -> "${item.mediaId}:$index" }) {
                    index,
                    item ->
                    SmartisanSongRow(
                        item,
                        modifier =
                            Modifier.libraryListEntrance(entrance) {
                                index - list.firstVisibleItemIndex
                            },
                        onClick = { browser.replaceQueueAndPlay(tracks, index) },
                        onMoreClick = onTrackMoreClick,
                        subtitle =
                            item.mediaMetadata.artist?.toString()
                                ?: item.mediaMetadata.subtitle?.toString()
                                ?: stringResource(R.string.unknown_artist),
                        durationText =
                            item.mediaMetadata.durationMs?.let(::libraryDuration).orEmpty(),
                        playback = playback,
                    )
                    LibraryDivider()
                }
                item(key = "folder-track-footer") {
                    LibraryFooter(R.plurals.track_count, tracks.size)
                }
            }
        }
    }
}

private val FolderEyeFrames =
    intArrayOf(
        R.drawable.eye_icon_0001,
        R.drawable.eye_icon_0002,
        R.drawable.eye_icon_0003,
        R.drawable.eye_icon_0004,
        R.drawable.eye_icon_0005,
        R.drawable.eye_icon_0006,
        R.drawable.eye_icon_0007,
        R.drawable.eye_icon_0008,
        R.drawable.eye_icon_0009,
        R.drawable.eye_icon_0010,
        R.drawable.eye_icon_0011,
        R.drawable.eye_icon_0012,
        R.drawable.eye_icon_0013,
        R.drawable.eye_icon_0014,
        R.drawable.eye_icon_0015,
        R.drawable.eye_icon_0016,
    )
