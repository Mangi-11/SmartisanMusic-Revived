package com.smartisan.music.ui.shell

import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.SessionResult
import com.smartisan.music.ExternalAudioLaunchRequest
import com.smartisan.music.R
import com.smartisan.music.data.favorite.FavoriteSongsRepository
import com.smartisan.music.data.library.LibraryExclusions
import com.smartisan.music.data.library.LibraryExclusionsStore
import com.smartisan.music.data.playlist.PlaylistCreateResult
import com.smartisan.music.data.playlist.PlaylistRepository
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.data.settings.ArtistSettingsStore
import com.smartisan.music.data.settings.LibraryDisplaySettings
import com.smartisan.music.data.settings.LibraryDisplaySettingsStore
import com.smartisan.music.data.settings.NavigationSettings
import com.smartisan.music.data.settings.NavigationSettingsStore
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.data.settings.PlaybackSettingsStore
import com.smartisan.music.isExternalAudioLaunchItem
import com.smartisan.music.platform.media.audioMediaItemUri
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.playback.LocalPlaybackController
import com.smartisan.music.playback.ProvidePlaybackController
import com.smartisan.music.playback.artworkRequestKey
import com.smartisan.music.playback.await
import com.smartisan.music.playback.deduplicateQueueCandidates
import com.smartisan.music.playback.invalidateLibrary
import com.smartisan.music.playback.refreshLibrary
import com.smartisan.music.playback.removeMediaItemsByMediaIds
import com.smartisan.music.playback.withPlaybackRating
import com.smartisan.music.resolveExternalAudioMediaStoreIds
import com.smartisan.music.resolveExternalAudioArtist
import com.smartisan.music.ui.components.LegacyTrackActionItem
import com.smartisan.music.ui.components.LegacyTrackActionsOverlay
import com.smartisan.music.ui.components.MediaStoreDeleteItem
import com.smartisan.music.ui.components.rememberMediaStoreDeleteCoordinator
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.dialogs.LegacySongDeleteConfirmOverlay
import com.smartisan.music.ui.shell.library.rememberLegacyLibraryMediaState
import com.smartisan.music.ui.shell.playback.LegacyPlaybackBarSnapshot
import com.smartisan.music.ui.shell.playback.LegacyPortPlaybackBar
import com.smartisan.music.ui.shell.playback.legacyPlaybackBarSnapshot
import com.smartisan.music.ui.shell.playback.loadLegacyArtworkBitmap
import com.smartisan.music.ui.shell.playback.peekLegacyArtworkBitmap
import com.smartisan.music.ui.shell.playback.toExternalAudioMediaItem
import com.smartisan.music.ui.shell.search.LegacyPortSearchOverlay
import com.smartisan.music.ui.shell.search.LegacySearchDrilldownTarget
import com.smartisan.music.ui.shell.tabs.LegacyPortBottomBar
import com.smartisan.music.ui.shell.tabs.LegacyNavigationEditorOverlay
import com.smartisan.music.ui.shell.tabs.LegacyPortTabContent
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBar
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LegacyTrackActionSource {
    Library,
    Loved,
    Playlist,
}

@Composable
fun LegacyPortMainShell(
    playbackLaunchRequest: Int = 0,
    externalAudioLaunchRequest: ExternalAudioLaunchRequest? = null,
    onExternalAudioLaunchConsumed: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ProvidePlaybackController {
        LegacyPortMainShellContent(
            playbackLaunchRequest = playbackLaunchRequest,
            externalAudioLaunchRequest = externalAudioLaunchRequest,
            onExternalAudioLaunchConsumed = onExternalAudioLaunchConsumed,
            modifier = modifier,
        )
    }
}

@Composable
private fun LegacyPortMainShellContent(
    playbackLaunchRequest: Int,
    externalAudioLaunchRequest: ExternalAudioLaunchRequest?,
    onExternalAudioLaunchConsumed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val favoriteRepository = remember(context.applicationContext) {
        FavoriteSongsRepository.getInstance(context.applicationContext)
    }
    val playlistRepository = remember(context.applicationContext) {
        PlaylistRepository.getInstance(context.applicationContext)
    }
    val libraryExclusionsStore = remember(context.applicationContext) {
        LibraryExclusionsStore(context.applicationContext)
    }
    val playbackSettingsStore = remember(context.applicationContext) {
        PlaybackSettingsStore(context.applicationContext)
    }
    val artistSettingsStore = remember(context.applicationContext) {
        ArtistSettingsStore(context.applicationContext)
    }
    val libraryDisplaySettingsStore = remember(context.applicationContext) {
        LibraryDisplaySettingsStore(context.applicationContext)
    }
    val navigationSettingsStore = remember(context.applicationContext) {
        NavigationSettingsStore(context.applicationContext)
    }
    val favoriteIds by favoriteRepository.observeFavoriteIds().collectAsState(initial = emptySet())
    val libraryExclusions by libraryExclusionsStore.exclusions.collectAsState(initial = LibraryExclusions())
    val playbackSettings by playbackSettingsStore.settings.collectAsState(initial = PlaybackSettings())
    val artistSettings by artistSettingsStore.settings.collectAsState(initial = ArtistSettings())
    val libraryDisplaySettings by libraryDisplaySettingsStore.settings.collectAsState(initial = LibraryDisplaySettings())
    val persistedNavigationSettings: NavigationSettings? by navigationSettingsStore.settings.collectAsState(
        initial = null,
    )
    val navigationSettings = persistedNavigationSettings ?: NavigationSettings()
    val albumViewMode = libraryDisplaySettings.albumViewMode
    val artistAlbumViewMode = libraryDisplaySettings.artistAlbumViewMode
    val unknownSongTitle = stringResource(R.string.unknown_song_title)
    val favoriteRecords by favoriteRepository.observeFavorites().collectAsState(initial = emptyList())
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    var playbackVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDrilldownTarget by remember { mutableStateOf<LegacySearchDrilldownTarget?>(null) }
    var currentDestination by remember { mutableStateOf(MusicDestination.Playlist) }
    var presentedFromMore by remember { mutableStateOf(false) }
    var playlistAddModeActive by remember { mutableStateOf(false) }
    var moreSettingsPageActive by remember { mutableStateOf(false) }
    var navigationEditorVisible by remember { mutableStateOf(false) }
    var navigationLayoutInitialized by remember { mutableStateOf(false) }

    val navigationLayout = navigationSettings.layout
    // 加歌模式只临时替换底栏末位，不污染用户保存的导航布局。
    val bottomDestinations = remember(navigationLayout, playlistAddModeActive) {
        navigationLayout.bottomDestinationsEnsuring(
            MusicDestination.Songs.takeIf { playlistAddModeActive },
        )
    }
    val overflowDestinations = navigationLayout.overflowDestinations

    // 冷启动先选择真实布局中的首个固定项，避免 DataStore 载入后把默认播放列表伪装成
    // “从更多进入”。运行中若用户把当前入口移入更多，则保留页面并补上返回来源语义。
    LaunchedEffect(persistedNavigationSettings, currentDestination) {
        val persistedLayout = persistedNavigationSettings?.layout ?: return@LaunchedEffect
        if (!navigationLayoutInitialized) {
            navigationLayoutInitialized = true
            if (currentDestination != MusicDestination.More && !persistedLayout.isPinned(currentDestination)) {
                currentDestination = persistedLayout.bottomDestinations.first()
                presentedFromMore = false
            }
        } else if (currentDestination != MusicDestination.More && !persistedLayout.isPinned(currentDestination)) {
            presentedFromMore = true
        }
    }
    var songsEditMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(emptySet<String>()) }
    var albumEditMode by remember { mutableStateOf(false) }
    var selectedAlbumIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedAlbumTitle by remember { mutableStateOf<String?>(null) }
    var selectedArtistTarget by remember { mutableStateOf<LegacyArtistTarget?>(null) }
    var libraryRefreshVersion by remember { mutableStateOf(0) }
    var libraryRefreshing by remember { mutableStateOf(false) }
    var libraryLoadRequested by remember { mutableStateOf(false) }
    var showSongDeleteConfirm by remember { mutableStateOf(false) }
    var pendingSongDeleteMediaIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingSongDeleteDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPlaylistPickerMediaItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pendingTrackActionItem by remember { mutableStateOf<MediaItem?>(null) }
    var pendingTrackActionSource by remember { mutableStateOf(LegacyTrackActionSource.Library) }
    var playbackPlaylistCreateRequest by remember { mutableStateOf<LegacyPlaylistNameDialogRequest.Create?>(null) }
    var ratingOverrides by remember { mutableStateOf(emptyMap<String, Int>()) }
    var snapshot by remember(controller) {
        mutableStateOf(controller.legacyPlaybackBarSnapshot())
    }
    var playbackBarContentSnapshot by remember(controller) {
        mutableStateOf(snapshot)
    }
    val legacyLibrary = rememberLegacyLibraryMediaState(
        loadRequested = libraryLoadRequested,
        libraryRefreshVersion = libraryRefreshVersion,
    )
    val legacyLibraryItems = remember(legacyLibrary.items, ratingOverrides) {
        legacyLibrary.items.withRatingOverrides(ratingOverrides)
    }
    val playbackBarMediaItem = playbackBarContentSnapshot.mediaItem
    val artworkRequestKey = playbackBarMediaItem?.artworkRequestKey()
    val artworkBitmap by produceState<Bitmap?>(
        initialValue = playbackBarMediaItem?.let(::peekLegacyArtworkBitmap),
        artworkRequestKey,
    ) {
        val mediaItem = playbackBarContentSnapshot.mediaItem
        if (mediaItem == null) {
            value = null
            return@produceState
        }
        value = peekLegacyArtworkBitmap(mediaItem) ?: value
        value = loadLegacyArtworkBitmap(context.applicationContext, mediaItem)
    }
    val albumPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val artistRootPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val artistNestedPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val moreDestinationPredictiveBackState = rememberLegacyPortPredictiveBackState()
    val playbackBarRequestedVisible = snapshot.mediaItem != null
    val playbackBarHeight = 67.dp
    var playbackBarComposed by remember { mutableStateOf(false) }
    val openSearchOverlay = {
        libraryLoadRequested = true
        searchQuery = ""
        searchDrilldownTarget = null
        searchVisible = true
    }
    fun openCurrentSearch() {
        openSearchOverlay()
    }
    val closeSearchOverlay = {
        searchVisible = false
        searchDrilldownTarget = null
    }

    fun closeAlbumDetail() {
        selectedAlbumId = null
        selectedAlbumTitle = null
    }

    fun closeArtistDetail() {
        selectedArtistTarget = selectedArtistTarget?.parentTarget()
    }

    fun returnToMore() {
        presentedFromMore = false
        currentDestination = MusicDestination.More
    }

    DisposableEffect(controller) {
        if (controller == null) {
            snapshot = LegacyPlaybackBarSnapshot()
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                val nextSnapshot = player.legacyPlaybackBarSnapshot()
                snapshot = nextSnapshot
                if (nextSnapshot.mediaItem != null) {
                    playbackBarContentSnapshot = nextSnapshot
                }
            }

        }
        controller.addListener(listener)
        val initialSnapshot = controller.legacyPlaybackBarSnapshot()
        snapshot = initialSnapshot
        if (initialSnapshot.mediaItem != null) {
            playbackBarContentSnapshot = initialSnapshot
        }
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(playbackBarRequestedVisible) {
        if (playbackBarRequestedVisible) {
            playbackBarComposed = true
        }
    }

    fun cleanupDeletedSongs(mediaIds: Set<String>, hideFromLibrary: Boolean) {
        if (mediaIds.isEmpty()) {
            return
        }
        controller.removeMediaItemsByMediaIds(mediaIds)
        scope.launch {
            if (hideFromLibrary) {
                libraryExclusionsStore.hideMediaIds(mediaIds)
            }
            favoriteRepository.removeAll(mediaIds)
            playlistRepository.removeMediaIdsFromAll(mediaIds)
            runCatching {
                controller?.invalidateLibrary()?.await(context)
            }
            libraryRefreshVersion += 1
        }
    }

    fun reclaimHiddenMediaIds(mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) {
            return
        }
        controller.removeMediaItemsByMediaIds(mediaIds)
    }

    fun enqueueResolvedMediaItems(items: List<MediaItem>) {
        if (items.isEmpty()) {
            return
        }
        if (controller?.repeatMode == Player.REPEAT_MODE_ONE) {
            Toast.makeText(context, R.string.can_not_add_to_queue_single_repeat, Toast.LENGTH_SHORT).show()
        } else {
            val deduplicatedItems = controller?.deduplicateQueueCandidates(items).orEmpty()
            if (deduplicatedItems.isNotEmpty()) {
                controller?.addMediaItems(deduplicatedItems)
                Toast.makeText(context, R.string.add_to_queue_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun enqueueMediaItems(items: List<MediaItem>) {
        enqueueResolvedMediaItems(items)
    }

    fun requestAddToPlaylist(items: List<MediaItem>) {
        val candidates = items.filter { item ->
            item.mediaId.isNotBlank() && !item.isExternalAudioLaunchItem()
        }
        if (candidates.isNotEmpty()) {
            pendingPlaylistPickerMediaItems = candidates
        }
    }

    fun toggleFavorite(mediaItem: MediaItem) {
        if (mediaItem.isExternalAudioLaunchItem()) {
            return
        }
        val mediaId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: return
        scope.launch {
            favoriteRepository.toggle(mediaId)
        }
    }

    fun showTrackActions(
        item: MediaItem,
        source: LegacyTrackActionSource,
    ) {
        if (item.mediaId.isBlank()) {
            return
        }
        pendingTrackActionItem = item
        pendingTrackActionSource = source
    }

    fun dismissTrackActions() {
        pendingTrackActionItem = null
    }

    fun dismissSongDeleteConfirmation() {
        val dismissAction = pendingSongDeleteDismissAction
        showSongDeleteConfirm = false
        pendingSongDeleteMediaIds = emptySet()
        pendingSongDeleteDismissAction = null
        dismissAction?.invoke()
    }

    fun requestSongDeleteConfirmation(
        mediaIds: Set<String>,
        onDismiss: (() -> Unit)? = null,
    ) {
        if (mediaIds.isEmpty()) {
            return
        }
        pendingSongDeleteMediaIds = mediaIds
        pendingSongDeleteDismissAction = onDismiss
        showSongDeleteConfirm = true
    }

    fun removeFavoriteMediaIds(mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) {
            return
        }
        scope.launch {
            if (mediaIds.size == 1) {
                favoriteRepository.remove(mediaIds.first())
            } else {
                favoriteRepository.removeAll(mediaIds)
            }
        }
    }

    fun refreshLegacyLibrary() {
        if (libraryRefreshing) {
            return
        }
        val playbackController = controller
        if (playbackController == null) {
            Toast.makeText(context, R.string.library_refresh_failed, Toast.LENGTH_SHORT).show()
            return
        }
        libraryRefreshing = true
        scope.launch {
            val result = runCatching {
                playbackController.refreshLibrary().await(context)
            }.getOrNull()
            libraryRefreshing = false
            if (result?.resultCode == SessionResult.RESULT_SUCCESS) {
                libraryRefreshVersion += 1
                Toast.makeText(context, R.string.library_refresh_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.library_refresh_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val deleteCoordinator = rememberMediaStoreDeleteCoordinator(
        onDeleted = { mediaIds ->
            cleanupDeletedSongs(mediaIds, hideFromLibrary = false)
        },
        onNotDeleted = {
            Toast.makeText(context, R.string.playback_delete_failed, Toast.LENGTH_SHORT).show()
        },
    )

    fun requestSystemDeleteMediaIds(mediaIds: Set<String>) {
        if (mediaIds.isEmpty()) {
            return
        }
        val deleteItems = mediaIds.mapNotNull { mediaId ->
            val mediaStoreId = mediaId.trim().toLongOrNull() ?: return@mapNotNull null
            MediaStoreDeleteItem(
                mediaId = mediaId,
                uri = audioMediaItemUri(mediaStoreId),
            )
        }
        val deleteItemIds = deleteItems.mapTo(linkedSetOf(), MediaStoreDeleteItem::mediaId)
        val invalidMediaIds = mediaIds - deleteItemIds
        if (invalidMediaIds.isNotEmpty()) {
            cleanupDeletedSongs(invalidMediaIds, hideFromLibrary = true)
        }
        if (deleteItems.isEmpty()) {
            return
        }
        deleteCoordinator.delete(deleteItems)
    }

    LaunchedEffect(playbackLaunchRequest) {
        if (playbackLaunchRequest > 0) {
            playbackVisible = true
        }
    }

    LaunchedEffect(currentDestination) {
        if (currentDestination.requiresFullLibraryItems()) {
            libraryLoadRequested = true
        }
        if (currentDestination != MusicDestination.Songs) {
            songsEditMode = false
            selectedSongIds = emptySet()
            showSongDeleteConfirm = false
            pendingSongDeleteMediaIds = emptySet()
        }
        if (currentDestination != MusicDestination.Album) {
            albumEditMode = false
            selectedAlbumIds = emptySet()
            selectedAlbumId = null
            selectedAlbumTitle = null
        }
        if (currentDestination != MusicDestination.Artist) {
            selectedArtistTarget = null
        }
        if (currentDestination != MusicDestination.Playlist) {
            playlistAddModeActive = false
        }
        dismissTrackActions()
    }

    LegacyPortPredictiveBackHandler(
        enabled = currentDestination == MusicDestination.Album && selectedAlbumId != null,
        state = albumPredictiveBackState,
    ) {
        closeAlbumDetail()
    }

    val selectedArtistParentTarget = selectedArtistTarget?.parentTarget()
    LegacyPortPredictiveBackHandler(
        enabled = currentDestination == MusicDestination.Artist &&
            selectedArtistTarget != null &&
            selectedArtistParentTarget == null,
        state = artistRootPredictiveBackState,
    ) {
        closeArtistDetail()
    }

    LegacyPortPredictiveBackHandler(
        enabled = presentedFromMore && when (currentDestination) {
            MusicDestination.Songs -> !songsEditMode
            MusicDestination.Album -> selectedAlbumId == null && !albumEditMode
            MusicDestination.Artist -> selectedArtistTarget == null
            else -> false
        },
        state = moreDestinationPredictiveBackState,
    ) {
        returnToMore()
    }
    LegacyPortPredictiveBackHandler(
        enabled = currentDestination == MusicDestination.Artist &&
            selectedArtistTarget != null &&
            selectedArtistParentTarget != null,
        state = artistNestedPredictiveBackState,
    ) {
        closeArtistDetail()
    }

    LaunchedEffect(externalAudioLaunchRequest, controller) {
        val request = externalAudioLaunchRequest ?: return@LaunchedEffect
        playbackVisible = true
        val playbackController = controller ?: return@LaunchedEffect
        val (artist, mediaStoreIds) = withContext(Dispatchers.IO) {
            request.resolveExternalAudioArtist(context.applicationContext) to
                request.resolveExternalAudioMediaStoreIds(context.applicationContext)
        }
        val mediaItem = request.toExternalAudioMediaItem(
            fallbackTitle = unknownSongTitle,
            artist = artist,
            mediaStoreId = mediaStoreIds.mediaStoreId,
            albumId = mediaStoreIds.albumId,
        )
        playbackController.setMediaItem(mediaItem)
        playbackController.prepare()
        playbackController.play()
        onExternalAudioLaunchConsumed(request.requestId)
    }

    val bottomNavigationHeight = dimensionResource(R.dimen.realtabcontent_margin_bottom) +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val realTabContentBottomMargin = if (playbackBarComposed) {
        bottomNavigationHeight - 6.dp
    } else {
        bottomNavigationHeight
    }
    val playbackBarOverlayHeight = if (playbackBarComposed) playbackBarHeight else 0.dp
    val hideBottomChrome = currentDestination == MusicDestination.More && moreSettingsPageActive

    LaunchedEffect(currentDestination) {
        if (currentDestination != MusicDestination.More) {
            moreSettingsPageActive = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                View(viewContext).apply {
                    setBackgroundResource(R.drawable.account_background)
                }
            },
        )
        val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
        val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
        val titleAreaHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + titleContentHeight
        val destinationSurface: @Composable (MusicDestination, Boolean) -> Unit = { destination, fromMore ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (hideBottomChrome) 0.dp else realTabContentBottomMargin),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val titleBarContent: @Composable (String?, LegacyArtistTarget?, Modifier) -> Unit = { albumDetailTitle, artistTarget, titleModifier ->
                        LegacyPortTitleBar(
                            destination = destination,
                            songsEditMode = destination == MusicDestination.Songs && songsEditMode,
                            selectedSongCount = selectedSongIds.size,
                            albumEditMode = destination == MusicDestination.Album && albumEditMode,
                            selectedAlbumCount = selectedAlbumIds.size,
                            albumDetailTitle = albumDetailTitle,
                            albumViewMode = albumViewMode,
                            artistTarget = artistTarget,
                            artistAlbumViewMode = artistAlbumViewMode,
                            onEnterSongsEditMode = {
                                songsEditMode = true
                                selectedSongIds = emptySet()
                            },
                            onExitSongsEditMode = {
                                songsEditMode = false
                                selectedSongIds = emptySet()
                                showSongDeleteConfirm = false
                            },
                            onRequestDeleteSelected = {
                                if (selectedSongIds.isNotEmpty()) {
                                    requestSongDeleteConfirmation(selectedSongIds)
                                }
                            },
                            onEnterAlbumEditMode = {
                                albumEditMode = true
                                selectedAlbumIds = emptySet()
                            },
                            onExitAlbumEditMode = {
                                albumEditMode = false
                                selectedAlbumIds = emptySet()
                            },
                            onToggleAlbumViewMode = {
                                val nextMode = if (albumViewMode == AlbumViewMode.List) {
                                    AlbumViewMode.Tile
                                } else {
                                    AlbumViewMode.List
                                }
                                scope.launch {
                                    libraryDisplaySettingsStore.setAlbumViewMode(nextMode)
                                }
                            },
                            onAlbumDetailBack = {
                                closeAlbumDetail()
                            },
                            onArtistBack = {
                                closeArtistDetail()
                            },
                            onToggleArtistAlbumViewMode = {
                                val nextMode = if (artistAlbumViewMode == AlbumViewMode.List) {
                                    AlbumViewMode.Tile
                                } else {
                                    AlbumViewMode.List
                                }
                                scope.launch {
                                    libraryDisplaySettingsStore.setArtistAlbumViewMode(nextMode)
                                }
                            },
                            onRootBack = ::returnToMore.takeIf { fromMore },
                            onSearchClick = ::openCurrentSearch,
                            modifier = titleModifier,
                        )
                    }
                    if (destination !in DestinationsWithOwnedTitleBar) {
                        when (destination) {
                            MusicDestination.Album -> LegacyPortTitleBarTransition(
                                secondaryKey = selectedAlbumTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(titleAreaHeight),
                                label = "legacy album title transition",
                                predictiveBackProgress = albumPredictiveBackState.progress,
                                predictiveBackExitConsumed = albumPredictiveBackState.exitConsumed,
                                onPredictiveBackExitConsumedReset = albumPredictiveBackState::reset,
                                primaryContent = {
                                    titleBarContent(null, null, Modifier.fillMaxSize())
                                },
                                secondaryContent = { detailTitle ->
                                    titleBarContent(detailTitle, null, Modifier.fillMaxSize())
                                },
                            )
                            MusicDestination.Artist -> LegacyPortArtistTitleStack(
                                selectedTarget = selectedArtistTarget,
                                rootPredictiveBackProgress = artistRootPredictiveBackState.progress,
                                rootPredictiveBackExitConsumed = artistRootPredictiveBackState.exitConsumed,
                                onRootPredictiveBackExitConsumedReset = artistRootPredictiveBackState::reset,
                                nestedPredictiveBackProgress = artistNestedPredictiveBackState.progress,
                                nestedPredictiveBackExitConsumed = artistNestedPredictiveBackState.exitConsumed,
                                onNestedPredictiveBackExitConsumedReset = artistNestedPredictiveBackState::reset,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(titleAreaHeight),
                            ) { artistTarget, titleModifier ->
                                titleBarContent(null, artistTarget, titleModifier)
                            }
                            else -> titleBarContent(null, null, Modifier.fillMaxWidth())
                        }
                    }
                    LegacyPortTabContent(
                        destination = destination,
                        presentedFromMore = fromMore,
                        overflowDestinations = overflowDestinations,
                        mediaItems = legacyLibraryItems,
                        favoriteRecords = favoriteRecords,
                        libraryLoaded = legacyLibrary.loaded,
                        songsEditMode = destination == MusicDestination.Songs && songsEditMode,
                        selectedSongIds = selectedSongIds,
                        albumViewMode = albumViewMode,
                        albumEditMode = destination == MusicDestination.Album && albumEditMode,
                        selectedAlbumId = selectedAlbumId,
                        selectedAlbumIds = selectedAlbumIds,
                        albumPredictiveBackProgress = albumPredictiveBackState.progress,
                        albumPredictiveBackExitConsumed = albumPredictiveBackState.exitConsumed,
                        onAlbumPredictiveBackExitConsumedReset = albumPredictiveBackState::reset,
                        artistAlbumViewMode = artistAlbumViewMode,
                        selectedArtistTarget = selectedArtistTarget,
                        artistRootPredictiveBackProgress = artistRootPredictiveBackState.progress,
                        artistRootPredictiveBackExitConsumed = artistRootPredictiveBackState.exitConsumed,
                        onArtistRootPredictiveBackExitConsumedReset = artistRootPredictiveBackState::reset,
                        artistNestedPredictiveBackProgress = artistNestedPredictiveBackState.progress,
                        artistNestedPredictiveBackExitConsumed = artistNestedPredictiveBackState.exitConsumed,
                        onArtistNestedPredictiveBackExitConsumedReset = artistNestedPredictiveBackState::reset,
                        moreDestinationPredictiveBackState = moreDestinationPredictiveBackState,
                        playbackBarOverlayHeight = if (hideBottomChrome) 0.dp else playbackBarOverlayHeight,
                        hiddenMediaIds = libraryExclusions.hiddenMediaIds,
                        libraryRefreshVersion = libraryRefreshVersion,
                        libraryRefreshing = libraryRefreshing,
                        playbackSettings = playbackSettings,
                        artistSettings = artistSettings,
                        onRefreshLibrary = ::refreshLegacyLibrary,
                        onRequestAddToPlaylist = ::requestAddToPlaylist,
                        onRequestAddToQueue = ::enqueueMediaItems,
                        onScratchEnabledChange = { enabled ->
                            scope.launch {
                                playbackSettingsStore.setScratchEnabled(enabled)
                            }
                        },
                        onHidePlayerAxisEnabledChange = { enabled ->
                            scope.launch {
                                playbackSettingsStore.setHidePlayerAxisEnabled(enabled)
                            }
                        },
                        onPopcornSoundEnabledChange = { enabled ->
                            scope.launch {
                                playbackSettingsStore.setPopcornSoundEnabled(enabled)
                            }
                        },
                        onAudioFxEnabledChange = { enabled ->
                            scope.launch {
                                playbackSettingsStore.setAudioFxEnabled(enabled)
                            }
                        },
                        onAudioFxPresetChange = { preset ->
                            scope.launch {
                                playbackSettingsStore.setAudioFxPreset(preset)
                            }
                        },
                        onAudioFxCustomGainDbPointsChange = { gains ->
                            scope.launch {
                                playbackSettingsStore.setAudioFxCustomGainDbPoints(gains)
                            }
                        },
                        onArtistSeparatorsChange = { separators ->
                            scope.launch {
                                artistSettingsStore.setSeparators(separators)
                            }
                            selectedArtistTarget = null
                            searchDrilldownTarget = null
                        },
                        navigationSettings = navigationSettings,
                        onTabPinnedChange = { route, pinned ->
                            scope.launch {
                                navigationSettingsStore.setTabPinned(route, pinned)
                            }
                        },
                        onOverflowDestinationSelected = { destination ->
                            presentedFromMore = true
                            currentDestination = destination
                        },
                        onReturnToMore = ::returnToMore,
                        onMediaIdsHidden = ::reclaimHiddenMediaIds,
                        onRequestDeleteMediaIds = ::requestSystemDeleteMediaIds,
                        onRequestSongDeleteConfirmation = { mediaIds, onDismiss ->
                            requestSongDeleteConfirmation(mediaIds, onDismiss)
                        },
                        onLibraryTrackMoreClick = { item ->
                            showTrackActions(item, LegacyTrackActionSource.Library)
                        },
                        onLovedSongsTrackMoreClick = { item ->
                            showTrackActions(item, LegacyTrackActionSource.Loved)
                        },
                        onPlaylistTrackMoreClick = { item ->
                            showTrackActions(item, LegacyTrackActionSource.Playlist)
                        },
                        onRemoveFavoriteMediaIds = ::removeFavoriteMediaIds,
                        onMoreSettingsPageActiveChanged = { active ->
                            moreSettingsPageActive = active
                        },
                        onSongSelectionChange = { mediaId, selected ->
                            selectedSongIds = selectedSongIds.withSelection(mediaId, selected)
                        },
                        onAlbumSelectionChange = { albumId, selected ->
                            selectedAlbumIds = selectedAlbumIds.withSelection(albumId, selected)
                        },
                        onAlbumSelected = { albumId, albumTitle ->
                            albumEditMode = false
                            selectedAlbumIds = emptySet()
                            selectedAlbumId = albumId
                            selectedAlbumTitle = albumTitle
                        },
                        onArtistTargetChanged = { target ->
                            selectedArtistTarget = target
                        },
                        onPlaylistAddModeActiveChanged = { active ->
                            playlistAddModeActive = active
                        },
                        onLibraryNeeded = {
                            libraryLoadRequested = true
                        },
                        onSearchClick = ::openCurrentSearch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                if (destination == MusicDestination.Artist || destination == MusicDestination.Album) {
                    LegacyPortTitleBarShadow(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = titleAreaHeight)
                            .fillMaxWidth()
                            .height(titleShadowHeight)
                            .zIndex(1f),
                    )
                }
            }
        }
        LegacyPortPageStackTransition(
            secondaryKey = currentDestination.takeIf { presentedFromMore },
            modifier = Modifier.fillMaxSize(),
            label = "legacy more destination stack",
            predictiveBackProgress = moreDestinationPredictiveBackState.progress,
            predictiveBackExitConsumed = moreDestinationPredictiveBackState.exitConsumed,
            onPredictiveBackExitConsumedReset = moreDestinationPredictiveBackState::reset,
            primaryContent = {
                destinationSurface(
                    if (presentedFromMore) MusicDestination.More else currentDestination,
                    false,
                )
            },
            secondaryContent = { destination ->
                destinationSurface(destination, true)
            },
        )
        if (!hideBottomChrome) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                if (playbackBarComposed) {
                    LegacyPortPlaybackBar(
                        snapshot = playbackBarContentSnapshot,
                        shown = playbackBarRequestedVisible,
                        favoriteIds = favoriteIds,
                        artworkBitmap = artworkBitmap,
                        onHidden = {
                            if (!playbackBarRequestedVisible) {
                                playbackBarComposed = false
                            }
                        },
                        onOpenPlayback = {
                            playbackVisible = true
                        },
                        onToggleFavorite = { mediaItem ->
                            toggleFavorite(mediaItem)
                        },
                        onPrevious = {
                            controller?.seekToPrevious()
                        },
                        onPlayPause = {
                            if (snapshot.isPlaybackActive) {
                                controller?.pause()
                            } else {
                                controller?.play()
                            }
                        },
                        onNext = {
                            controller?.seekToNext()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(playbackBarHeight),
                        bottomDividerVisible = false,
                    )
                }
                LegacyPortBottomBar(
                    currentDestination = when {
                        playlistAddModeActive -> MusicDestination.Songs
                        presentedFromMore -> MusicDestination.More
                        else -> currentDestination
                    },
                    destinations = bottomDestinations,
                    onDestinationSelected = { destination ->
                        presentedFromMore = false
                        currentDestination = destination
                    },
                    onEditRequested = {
                        if (!playlistAddModeActive) {
                            navigationEditorVisible = true
                        }
                    },
                    topChromeVisible = !playbackBarComposed,
                )
            }
        }
        val trackActionItems = pendingTrackActionItem?.let { actionItem ->
            val mediaId = actionItem.mediaId
            val isFavorite = mediaId in favoriteIds
            val canAddToPlaylist = mediaId.isNotBlank() &&
                !actionItem.isExternalAudioLaunchItem()
            val canFavorite = mediaId.isNotBlank() &&
                !actionItem.isExternalAudioLaunchItem()
            val actions = mutableListOf(
                LegacyTrackActionItem(
                    labelRes = R.string.add_to_playlist,
                    iconRes = R.drawable.more_select_icon_addlist,
                    pressedIconRes = R.drawable.more_select_icon_addlist_down,
                    enabled = canAddToPlaylist,
                    onClick = {
                        dismissTrackActions()
                        requestAddToPlaylist(listOf(actionItem))
                    },
                ),
                LegacyTrackActionItem(
                    labelRes = R.string.add_to_queue,
                    iconRes = R.drawable.more_select_icon_addplay,
                    pressedIconRes = R.drawable.more_select_icon_addplay_down,
                    onClick = {
                        dismissTrackActions()
                        enqueueMediaItems(listOf(actionItem))
                    },
                ),
                LegacyTrackActionItem(
                    labelRes = if (isFavorite) R.string.cancel_love else R.string.love,
                    iconRes = if (isFavorite) {
                        R.drawable.more_select_icon_favorite_cancel
                    } else {
                        R.drawable.more_select_icon_favorite_add
                    },
                    pressedIconRes = if (isFavorite) {
                        R.drawable.more_select_icon_favorite_cancel_down
                    } else {
                        R.drawable.more_select_icon_favorite_add_down
                    },
                    enabled = canFavorite,
                    selected = isFavorite,
                    onClick = {
                        dismissTrackActions()
                        if (canFavorite) {
                            toggleFavorite(actionItem)
                        }
                    },
                ),
            )
            if (pendingTrackActionSource == LegacyTrackActionSource.Library) {
                actions += LegacyTrackActionItem(
                    labelRes = R.string.delete,
                    iconRes = R.drawable.more_select_icon_delete,
                    onClick = {
                        dismissTrackActions()
                        requestSongDeleteConfirmation(setOf(mediaId))
                    },
                )
            }
            actions
        }.orEmpty()
        LegacyTrackActionsOverlay(
            visible = pendingTrackActionItem != null,
            actions = trackActionItems,
            onDismissRequest = ::dismissTrackActions,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2.4f),
        )
        LegacyPortPlaybackOverlay(
            visible = playbackVisible,
            playbackSettings = playbackSettings,
            ratingOverrides = ratingOverrides,
            onRequestAddToPlaylist = ::requestAddToPlaylist,
            onRequestAddToQueue = ::enqueueMediaItems,
            onScratchEnabledChange = { enabled ->
                scope.launch {
                    playbackSettingsStore.setScratchEnabled(enabled)
                }
            },
            onTrackRatingChanged = { mediaId, score ->
                ratingOverrides = ratingOverrides + (mediaId to score.coerceIn(0, 5))
            },
            onFavoriteToggle = ::toggleFavorite,
            onCollapse = {
                playbackVisible = false
            },
            modifier = Modifier.zIndex(3f),
        )
        LegacyPortSearchOverlay(
            visible = searchVisible,
            query = searchQuery,
            mediaItems = legacyLibraryItems,
            hiddenMediaIds = libraryExclusions.hiddenMediaIds,
            drilldownTarget = searchDrilldownTarget,
            libraryRefreshVersion = libraryRefreshVersion,
            artistAlbumViewMode = artistAlbumViewMode,
            artistSettings = artistSettings,
            onQueryChange = { value ->
                searchQuery = value
            },
            onDismiss = closeSearchOverlay,
            onOpenPlayback = {
                playbackVisible = true
            },
            onRequestAddToPlaylist = ::requestAddToPlaylist,
            onRequestAddToQueue = ::enqueueMediaItems,
            onTrackMoreClick = { item ->
                showTrackActions(item, LegacyTrackActionSource.Library)
            },
            onDrilldownTargetChanged = { target ->
                searchDrilldownTarget = target
            },
            onAlbumClick = { albumId, albumTitle ->
                searchDrilldownTarget = LegacySearchDrilldownTarget.Album(
                    albumId = albumId,
                    albumTitle = albumTitle,
                )
            },
            onArtistClick = { artistId, artistName ->
                searchDrilldownTarget = LegacySearchDrilldownTarget.Artist(
                    target = LegacyArtistTarget.Albums(
                        artistId = artistId,
                        artistName = artistName,
                    ),
                )
            },
            onToggleArtistAlbumViewMode = {
                val nextMode = if (artistAlbumViewMode == AlbumViewMode.List) {
                    AlbumViewMode.Tile
                } else {
                    AlbumViewMode.List
                }
                scope.launch {
                    libraryDisplaySettingsStore.setArtistAlbumViewMode(nextMode)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
        )
        LegacyPlaybackPlaylistPickerOverlay(
            visible = pendingPlaylistPickerMediaItems != null && playbackPlaylistCreateRequest == null,
            playlists = playlists,
            onDismiss = {
                pendingPlaylistPickerMediaItems = null
            },
            onCreateNewPlaylist = {
                scope.launch {
                    playbackPlaylistCreateRequest = LegacyPlaylistNameDialogRequest.Create(
                        initialName = playlistRepository.suggestNextUntitledName(),
                    )
                }
            },
            onPlaylistSelected = { playlistId ->
                val mediaIds = pendingPlaylistPickerMediaItems?.map(MediaItem::mediaId).orEmpty()
                scope.launch {
                    val result = playlistRepository.addMediaIds(playlistId, mediaIds)
                    when {
                        result.addedCount > 0 -> {
                            Toast.makeText(context, R.string.playlist_added, Toast.LENGTH_SHORT).show()
                        }
                        result.duplicateCount > 0 -> {
                            Toast.makeText(context, R.string.playlist_song_exists, Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingPlaylistPickerMediaItems = null
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f),
        )
        LegacyPlaylistNameDialogOverlay(
            request = playbackPlaylistCreateRequest,
            onDismiss = {
                playbackPlaylistCreateRequest = null
            },
            onConfirm = { _, input ->
                val mediaIds = pendingPlaylistPickerMediaItems?.map(MediaItem::mediaId).orEmpty()
                scope.launch {
                    when (playlistRepository.createPlaylist(input, mediaIds)) {
                        PlaylistCreateResult.EmptyName -> {
                            Toast.makeText(context, R.string.playlist_create_failed, Toast.LENGTH_SHORT).show()
                        }
                        PlaylistCreateResult.DuplicateName -> {
                            Toast.makeText(context, R.string.playlist_duplicate_name, Toast.LENGTH_SHORT).show()
                        }
                        is PlaylistCreateResult.Success -> {
                            playbackPlaylistCreateRequest = null
                            pendingPlaylistPickerMediaItems = null
                            Toast.makeText(context, R.string.playlist_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )
        if (showSongDeleteConfirm) {
            LegacySongDeleteConfirmOverlay(
                onDismiss = {
                    dismissSongDeleteConfirmation()
                },
                onConfirm = {
                    val mediaIds = pendingSongDeleteMediaIds
                    val dismissAction = pendingSongDeleteDismissAction
                    if (mediaIds.isEmpty()) {
                        dismissSongDeleteConfirmation()
                        return@LegacySongDeleteConfirmOverlay
                    }
                    showSongDeleteConfirm = false
                    pendingSongDeleteMediaIds = emptySet()
                    pendingSongDeleteDismissAction = null
                    songsEditMode = false
                    selectedSongIds = emptySet()
                    requestSystemDeleteMediaIds(mediaIds)
                    dismissAction?.invoke()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
            )
        }
        LegacyNavigationEditorOverlay(
            visible = navigationEditorVisible,
            layout = navigationLayout,
            selectedDestination = if (presentedFromMore) MusicDestination.More else currentDestination,
            onDismissRequest = {
                navigationEditorVisible = false
            },
            onCommit = { layout ->
                navigationEditorVisible = false
                scope.launch {
                    navigationSettingsStore.commitLayout(layout)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(5f),
        )
    }
}

private fun MusicDestination.requiresFullLibraryItems(): Boolean {
    return when (this) {
        MusicDestination.Songs,
        MusicDestination.Album,
        MusicDestination.Artist,
        MusicDestination.Genre,
        MusicDestination.LovedSongs,
        -> true
        MusicDestination.Playlist,
        MusicDestination.Folder,
        MusicDestination.More,
        -> false
    }
}

private val DestinationsWithOwnedTitleBar = setOf(
    MusicDestination.Playlist,
    MusicDestination.More,
    MusicDestination.Genre,
    MusicDestination.LovedSongs,
    MusicDestination.Folder,
)

private fun List<MediaItem>.withRatingOverrides(ratingOverrides: Map<String, Int>): List<MediaItem> {
    if (isEmpty() || ratingOverrides.isEmpty()) {
        return this
    }
    return map { item ->
        val score = ratingOverrides[item.mediaId] ?: return@map item
        item.withPlaybackRating(score)
    }
}
