package com.smartisan.music.ui.search

import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.library.LibraryExclusionsStore
import com.smartisan.music.data.search.SearchHistoryStore
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.artworkRequestKey
import com.smartisan.music.playback.await
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.artist.ArtistSummary
import com.smartisan.music.ui.components.GlobalPlaybackBar
import com.smartisan.music.ui.components.SmartisanDrawableBackground
import com.smartisan.music.ui.components.hasAudioPermission
import com.smartisan.music.ui.components.loadArtworkThumbnail
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import kotlinx.coroutines.launch

private val SearchPageBackground = Color.White
private val SearchFieldTextColor = Color(0xCC000000)
private val SearchSectionTitleColor = Color(0x66000000)
private val SearchDividerColor = Color(0xFFE9E9E9)
private val SearchSongTitleColor = Color(0xCC000000)
private val SearchSongPlayingColor = Color(0xFFE64040)
private val SearchResultHighlightColor = Color(0xFFC14352)
private val SearchSubtitleColor = Color(0x66000000)
private val SearchEmptyTextColor = Color(0xFFDBDBDB)

private val SearchFieldTextStyle = TextStyle(
    fontSize = 15.sp,
    color = SearchFieldTextColor,
)
private val SearchSectionTitleStyle = TextStyle(
    fontSize = 15.sp,
    color = SearchSongTitleColor,
)
private val SearchPrimaryTextStyle = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium,
    color = SearchSongTitleColor,
)
private val SearchSecondaryTextStyle = TextStyle(
    fontSize = 13.sp,
    color = SearchSubtitleColor,
)

private val SearchTopBarHeight = 50.dp
private val SearchFieldHeight = 32.dp
private val SearchTopBarItemSpacing = 12.dp
private val SearchCancelButtonSize = 36.dp
private val SearchCancelIconSize = 36.dp
private val SearchClearButtonSize = 30.dp
private val SearchClearIconSize = 30.dp
private val SearchFieldInnerEdgePadding = 6.dp
private val SearchLeftIconWidth = 24.dp
private val SearchLeftIconHeight = 30.dp
private val SearchTextStartPadding = 36.dp
private val SearchHistoryTopPadding = 19.dp
private val SearchSectionHorizontalPadding = 21.dp
private val SearchHistoryRowSpacing = 10.dp
private val SearchHistoryChipHeight = 30.dp
private val SearchSectionHeaderHeight = 45.dp
private val SearchSectionHeaderStartPadding = 11.dp
private val SearchResultRowHeight = 60.dp
private val SearchResultArtworkFrameWidth = 48.dp
private val SearchResultArtworkSize = 38.dp
private val SearchResultActionWidth = 34.dp
private val SearchResultMoreIconSize = 30.dp
private val SearchResultSourceIconSize = 14.dp
private val SearchPlaybackBarReservedHeight = 67.dp
private val SearchTopHorizontalPadding = 6.dp
private val SearchNoResultTopPadding = 85.dp
private val SearchNoResultArtworkSize = 140.dp
private val SearchArtworkDecodeSize = Size(128, 128)

private enum class SearchEntityAction {
    More,
    Source,
}

@Composable
fun GlobalSearchScreen(
    query: String,
    libraryRefreshVersion: Int = 0,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenPlayback: () -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String, String) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackBrowser = LocalPlaybackBrowser.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val historyStore = remember(context.applicationContext) {
        SearchHistoryStore(context.applicationContext)
    }
    val exclusionsStore = remember(context.applicationContext) {
        LibraryExclusionsStore(context.applicationContext)
    }
    val history by historyStore.history.collectAsState(initial = emptyList())
    val libraryRevision by exclusionsStore.revision.collectAsState(initial = 0)
    val hasPermission = hasAudioPermission(context)
    var permissionVersion by remember { mutableIntStateOf(0) }
    var songs by remember(playbackBrowser) { mutableStateOf(emptyList<MediaItem>()) }
    var currentMediaId by remember(playbackBrowser) {
        mutableStateOf(playbackBrowser?.currentMediaItem?.mediaId)
    }
    val dismissSearch by rememberUpdatedState(
        newValue = {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onDismiss()
        },
    )
    val clearSearchInputFocus by rememberUpdatedState(
        newValue = {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionVersion += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(playbackBrowser) {
        val browser = playbackBrowser ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                currentMediaId = player.currentMediaItem?.mediaId
            }
        }
        browser.addListener(listener)
        currentMediaId = browser.currentMediaItem?.mediaId
        onDispose {
            browser.removeListener(listener)
        }
    }

    LaunchedEffect(playbackBrowser, permissionVersion, libraryRevision, libraryRefreshVersion, hasPermission) {
        val browser = playbackBrowser ?: run {
            songs = emptyList()
            return@LaunchedEffect
        }
        if (!hasPermission) {
            songs = emptyList()
            return@LaunchedEffect
        }
        val rootResult = browser.getLibraryRoot(null).await(context)
        val rootItem = rootResult.value ?: run {
            songs = emptyList()
            return@LaunchedEffect
        }
        val childrenResult = browser.getChildren(rootItem.mediaId, 0, Int.MAX_VALUE, null).await(context)
        songs = childrenResult.value?.toList().orEmpty()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val unknownAlbumTitle = stringResource(R.string.unknown_album)
    val unknownArtistTitle = stringResource(R.string.unknown_artist)
    val multipleArtistsTitle = stringResource(R.string.many_artist)
    val results = remember(query, songs, unknownAlbumTitle, unknownArtistTitle, multipleArtistsTitle, artistSettings) {
        buildSearchResults(
            query = query,
            songs = songs,
            unknownAlbumTitle = unknownAlbumTitle,
            unknownArtistTitle = unknownArtistTitle,
            multipleArtistsTitle = multipleArtistsTitle,
            artistSettings = artistSettings,
        )
    }
    val showPlaybackBar = currentMediaId != null

    BackHandler(onBack = dismissSearch)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SearchPageBackground)
            .imePadding(),
    ) {
        SmartisanDrawableBackground(
            drawableRes = R.drawable.account_background,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            SearchTopBar(
                query = query,
                focusRequester = focusRequester,
                onQueryChange = onQueryChange,
                onSearch = {
                    scope.launch {
                        historyStore.record(query)
                    }
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                onDismiss = dismissSearch,
            )
            if (query.isBlank()) {
                SearchHistoryPage(
                    history = history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onHistoryClick = { entry ->
                        onQueryChange(entry)
                        scope.launch {
                            historyStore.record(entry)
                        }
                    },
                    onClearHistory = {
                        scope.launch {
                            historyStore.clear()
                        }
                    },
                )
            } else {
                SearchResultsPage(
                    results = results,
                    currentMediaId = currentMediaId,
                    showPlaybackBar = showPlaybackBar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onSongClick = { item ->
                        val targetIndex = results.songs.indexOfFirst { song -> song.mediaId == item.mediaId }
                        if (targetIndex >= 0) {
                            playbackBrowser.replaceQueueAndPlay(results.songs, targetIndex)
                            scope.launch {
                                historyStore.record(query)
                            }
                        }
                    },
                    onAlbumClick = { album ->
                        scope.launch {
                            historyStore.record(query)
                        }
                        clearSearchInputFocus()
                        onAlbumClick(album.id, album.title)
                    },
                    onArtistClick = { artist ->
                        scope.launch {
                            historyStore.record(query)
                        }
                        clearSearchInputFocus()
                        onArtistClick(artist.id, artist.name)
                    },
                )
            }
        }
        if (showPlaybackBar) {
            GlobalPlaybackBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                onOpenPlayback = onOpenPlayback,
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val topInset = with(density) {
        WindowInsets.safeDrawing.getTop(this).toDp()
    }
    val shadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchTopBarHeight + topInset)
            .background(Color.White)
            .zIndex(1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset)
                .padding(horizontal = SearchTopHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SearchTopBarItemSpacing),
        ) {
            SearchField(
                value = query,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f),
                onValueChange = onQueryChange,
                onSearch = onSearch,
            )
            SearchCancelButton(onDismiss = onDismiss)
        }
        LegacyPortTitleBarShadow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = shadowHeight)
                .fillMaxWidth()
                .height(shadowHeight),
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = SearchFieldTextStyle,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSearch() }),
        modifier = modifier
            .height(SearchFieldHeight)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            val clearInteractionSource = remember { MutableInteractionSource() }
            val clearPressed by clearInteractionSource.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                SmartisanDrawableBackground(
                    drawableRes = R.drawable.search_field,
                    modifier = Modifier.matchParentSize(),
                )
                Image(
                    painter = painterResource(R.drawable.search_bar_left_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = SearchFieldInnerEdgePadding)
                        .width(SearchLeftIconWidth)
                        .height(SearchLeftIconHeight),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(
                            start = SearchTextStartPadding,
                            end = if (value.isNotEmpty()) SearchClearButtonSize else 12.dp,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(SearchClearButtonSize)
                            .align(Alignment.CenterEnd)
                            .clickable(
                                interactionSource = clearInteractionSource,
                                indication = null,
                                onClick = { onValueChange("") }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(
                                if (clearPressed) {
                                    R.drawable.text_clear_btn_pressed
                                } else {
                                    R.drawable.text_clear_btn
                                },
                            ),
                            contentDescription = stringResource(R.string.clear_search_text),
                            modifier = Modifier.size(SearchClearIconSize),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SearchCancelButton(onDismiss: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(SearchCancelButtonSize)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                if (pressed) {
                    R.drawable.standard_icon_cancel_pressed
                } else {
                    R.drawable.standard_icon_cancel
                },
            ),
            contentDescription = stringResource(R.string.cancel),
            modifier = Modifier.size(SearchCancelIconSize),
        )
    }
}

@Composable
private fun SearchHistoryPage(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
        ) {
            SmartisanDrawableBackground(
                drawableRes = R.drawable.account_background,
                modifier = Modifier.matchParentSize(),
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        SmartisanDrawableBackground(
            drawableRes = R.drawable.account_background,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = SearchSectionHorizontalPadding,
                    top = SearchHistoryTopPadding,
                    end = 20.dp,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.search_history),
                    style = SearchSectionTitleStyle,
                    modifier = Modifier.padding(start = 7.dp),
                )
                Image(
                    painter = painterResource(R.drawable.search_clear),
                    contentDescription = stringResource(R.string.clear_history),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClearHistory,
                        ),
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(SearchHistoryRowSpacing),
                verticalArrangement = Arrangement.spacedBy(SearchHistoryRowSpacing),
            ) {
                history.forEach { entry ->
                    SearchHistoryChip(
                        text = entry,
                        onClick = { onHistoryClick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsPage(
    results: SearchResults,
    currentMediaId: String?,
    showPlaybackBar: Boolean,
    onSongClick: (MediaItem) -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
    onArtistClick: (ArtistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        SmartisanDrawableBackground(
            drawableRes = R.drawable.account_background,
            modifier = Modifier.matchParentSize(),
        )
        if (!results.hasResults) {
            SearchNoResultState(
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = if (showPlaybackBar) SearchPlaybackBarReservedHeight + 16.dp else 16.dp,
                ),
            ) {
                appendSuggestedResults(
                    songs = results.songs.take(2),
                    currentMediaId = currentMediaId,
                    onSongClick = onSongClick,
                )
                appendAlbumResults(
                    albums = results.albums,
                    onAlbumClick = onAlbumClick,
                )
                appendArtistResults(
                    artists = results.artists,
                    onArtistClick = onArtistClick,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appendSuggestedResults(
    songs: List<MediaItem>,
    currentMediaId: String?,
    onSongClick: (MediaItem) -> Unit,
) {
    if (songs.isEmpty()) return
    item(key = "suggested-header") { SearchSectionHeader(title = R.string.suggestion) }
    items(
        items = songs,
        key = { item -> "suggested-${item.mediaId}" },
    ) { item ->
        SearchSongRow(
            mediaItem = item,
            selected = item.mediaId == currentMediaId,
            onClick = { onSongClick(item) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appendAlbumResults(
    albums: List<AlbumSummary>,
    onAlbumClick: (AlbumSummary) -> Unit,
) {
    if (albums.isEmpty()) return
    item(key = "albums-header") { SearchSectionHeader(title = R.string.search_tab_albums) }
    items(
        items = albums,
        key = { album -> "album-${album.id}" },
    ) { album ->
        SearchEntityRow(
            title = album.title,
            subtitle = album.artist,
            representative = album.representative,
            titleColor = SearchResultHighlightColor,
            subtitleColor = SearchResultHighlightColor,
            action = SearchEntityAction.Source,
            onClick = { onAlbumClick(album) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appendArtistResults(
    artists: List<ArtistSummary>,
    onArtistClick: (ArtistSummary) -> Unit,
) {
    if (artists.isEmpty()) return
    item(key = "artists-header") { SearchSectionHeader(title = R.string.search_tab_artists) }
    items(
        items = artists,
        key = { artist -> "artist-${artist.id}" },
    ) { artist ->
        SearchEntityRow(
            title = artist.name,
            subtitle = null,
            representative = artist.representative,
            titleColor = SearchResultHighlightColor,
            action = SearchEntityAction.Source,
            onClick = { onArtistClick(artist) },
        )
    }
}

@Composable
private fun SearchSectionHeader(title: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchSectionHeaderHeight),
    ) {
        SmartisanDrawableBackground(
            drawableRes = R.drawable.home_recommend_title_noline_bg,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = stringResource(title),
            style = TextStyle(fontSize = 15.sp, color = SearchSectionTitleColor),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = SearchSectionHeaderStartPadding),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(SearchDividerColor),
        )
    }
}

@Composable
private fun SearchSongRow(
    mediaItem: MediaItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SearchEntityRow(
        title = mediaItem.mediaMetadata.title?.toString()
            ?: mediaItem.mediaMetadata.displayTitle?.toString()
            ?: stringResource(R.string.unknown_song_title),
        subtitle = mediaItem.mediaMetadata.artist?.toString()
            ?: stringResource(R.string.unknown_artist),
        representative = mediaItem,
        titleColor = if (selected) SearchSongPlayingColor else SearchSongTitleColor,
        onClick = onClick,
    )
}

@Composable
private fun SearchEntityRow(
    title: String,
    subtitle: String?,
    representative: MediaItem,
    onClick: () -> Unit,
    titleColor: Color = SearchSongTitleColor,
    subtitleColor: Color = SearchSubtitleColor,
    action: SearchEntityAction = SearchEntityAction.More,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchResultRowHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        SmartisanDrawableBackground(
            drawableRes = if (pressed) R.drawable.list_item_bgwithoutphoto_down else android.R.color.white,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(SearchResultArtworkFrameWidth)
                    .height(SearchResultRowHeight)
                    .padding(start = 12.dp, top = 5.dp, bottom = 5.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                SearchArtwork(
                    mediaItem = representative,
                    modifier = Modifier.size(SearchResultArtworkSize),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = SearchPrimaryTextStyle.copy(color = titleColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = SearchSecondaryTextStyle.copy(color = subtitleColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(SearchResultActionWidth)
                    .height(SearchResultRowHeight),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val iconRes = when (action) {
                    SearchEntityAction.More -> if (pressed) R.drawable.btn_more_white else R.drawable.btn_more
                    SearchEntityAction.Source -> if (pressed) {
                        R.drawable.local_phone_icon_white
                    } else {
                        R.drawable.local_phone_icon
                    }
                }
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(
                        if (action == SearchEntityAction.More) {
                            SearchResultMoreIconSize
                        } else {
                            SearchResultSourceIconSize
                        },
                    ),
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SearchDividerColor),
    )
}

@Composable
private fun SearchHistoryChip(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .height(SearchHistoryChipHeight)
            .defaultMinSize(minWidth = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SmartisanDrawableBackground(
            drawableRes = if (pressed) R.drawable.search_badge_grey_p else R.drawable.search_badge_grey,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = text,
            style = TextStyle(
                color = Color(0x66000000),
                fontSize = 13.5.sp,
            ),
            modifier = Modifier.padding(horizontal = 14.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchNoResultState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(top = SearchNoResultTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.blank_search),
            contentDescription = null,
            modifier = Modifier.size(SearchNoResultArtworkSize),
        )
        Text(
            text = stringResource(R.string.search_no_result),
            style = TextStyle(fontSize = 23.sp, color = SearchEmptyTextColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 25.dp),
        )
    }
}

@Composable
private fun SearchArtwork(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val artworkRequestKey = mediaItem.artworkRequestKey()
    val artwork by produceState<ImageBitmap?>(
        initialValue = null,
        artworkRequestKey,
    ) {
        value = loadArtworkThumbnail(context, mediaItem, SearchArtworkDecodeSize)
    }

    if (artwork != null) {
        Box(modifier = modifier) {
            Image(
                bitmap = artwork!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Image(
                painter = painterResource(R.drawable.mask_albumcover_list),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.noalbumcover_120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Image(
                painter = painterResource(R.drawable.mask_albumcover_list),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
