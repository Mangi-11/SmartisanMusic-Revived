package com.smartisan.music.ui.shell

import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.smartisan.music.data.settings.ThemeMode
import com.smartisan.music.data.settings.ThemeSettingsStore
import com.smartisan.music.data.settings.restoredDestination
import com.smartisan.music.isExternalAudioLaunchItem
import com.smartisan.music.platform.media.audioMediaItemUri
import com.smartisan.music.playback.LocalPlaybackController
import com.smartisan.music.playback.ProvidePlaybackController
import com.smartisan.music.playback.artworkRequestKey
import com.smartisan.music.playback.await
import com.smartisan.music.playback.deduplicateQueueCandidates
import com.smartisan.music.playback.invalidateLibrary
import com.smartisan.music.playback.refreshLibrary
import com.smartisan.music.playback.removeMediaItemsByMediaIds
import com.smartisan.music.playback.withPlaybackRating
import com.smartisan.music.resolveExternalAudioArtist
import com.smartisan.music.resolveExternalAudioMediaStoreIds
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.artist.ArtistTarget
import com.smartisan.music.ui.artist.ArtistTitleStack
import com.smartisan.music.ui.artist.parentTarget
import com.smartisan.music.ui.components.MediaStoreDeleteItem
import com.smartisan.music.ui.components.TrackActionItem
import com.smartisan.music.ui.components.TrackActionsOverlay
import com.smartisan.music.ui.components.rememberMediaStoreDeleteCoordinator
import com.smartisan.music.ui.components.withSelection
import com.smartisan.music.ui.library.rememberLibraryMediaState
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.playlist.PlaybackPlaylistPickerOverlay
import com.smartisan.music.ui.playlist.PlaylistNameDialogOverlay
import com.smartisan.music.ui.playlist.PlaylistNameDialogRequest
import com.smartisan.music.ui.search.SearchDrilldownTarget
import com.smartisan.music.ui.search.SearchOverlay
import com.smartisan.music.ui.shell.dialogs.SongDeleteConfirmOverlay
import com.smartisan.music.ui.shell.playback.PlaybackBar
import com.smartisan.music.ui.shell.playback.PlaybackBarSnapshot
import com.smartisan.music.ui.shell.playback.loadArtworkBitmap
import com.smartisan.music.ui.shell.playback.peekArtworkBitmap
import com.smartisan.music.ui.shell.playback.playbackBarSnapshot
import com.smartisan.music.ui.shell.playback.toExternalAudioMediaItem
import com.smartisan.music.ui.shell.tabs.MusicBottomBar
import com.smartisan.music.ui.shell.tabs.MusicTabContent
import com.smartisan.music.ui.shell.tabs.NavigationEditorOverlay
import com.smartisan.music.ui.shell.titlebar.MainTitleBar
import com.smartisan.music.ui.shell.titlebar.TitleBarShadow
import com.smartisan.music.ui.shell.titlebar.TitleBarTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TrackActionSource {
    Library,
    Loved,
    Playlist,
}

@Composable
fun MusicAppShell(
    modifier: Modifier = Modifier,
    playbackLaunchRequest: Int = 0,
    externalAudioLaunchRequest: ExternalAudioLaunchRequest? = null,
    onExternalAudioLaunchConsumed: (Int) -> Unit = {},
    onStartupReady: () -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    ProvidePlaybackController {
        MusicAppShellContent(
            playbackLaunchRequest = playbackLaunchRequest,
            externalAudioLaunchRequest = externalAudioLaunchRequest,
            onExternalAudioLaunchConsumed = onExternalAudioLaunchConsumed,
            onStartupReady = onStartupReady,
            onThemeModeChange = onThemeModeChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun MusicAppShellContent(
    playbackLaunchRequest: Int,
    externalAudioLaunchRequest: ExternalAudioLaunchRequest?,
    onExternalAudioLaunchConsumed: (Int) -> Unit,
    onStartupReady: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current
    val scope = rememberCoroutineScope()
    val favoriteRepository =
        remember(context.applicationContext) {
            FavoriteSongsRepository.getInstance(context.applicationContext)
        }
    val playlistRepository =
        remember(context.applicationContext) {
            PlaylistRepository.getInstance(context.applicationContext)
        }
    val libraryExclusionsStore =
        remember(context.applicationContext) {
            LibraryExclusionsStore(context.applicationContext)
        }
    val playbackSettingsStore =
        remember(context.applicationContext) {
            PlaybackSettingsStore(context.applicationContext)
        }
    val artistSettingsStore =
        remember(context.applicationContext) {
            ArtistSettingsStore(context.applicationContext)
        }
    val libraryDisplaySettingsStore =
        remember(context.applicationContext) {
            LibraryDisplaySettingsStore(context.applicationContext)
        }
    val navigationSettingsStore =
        remember(context.applicationContext) {
            NavigationSettingsStore(context.applicationContext)
        }
    val themeSettingsStore =
        remember(context.applicationContext) {
            ThemeSettingsStore(context.applicationContext)
        }
    val favoriteIds by favoriteRepository.observeFavoriteIds().collectAsState(initial = emptySet())
    val libraryExclusions by
        libraryExclusionsStore.exclusions.collectAsState(initial = LibraryExclusions())
    val playbackSettings by
        playbackSettingsStore.settings.collectAsState(initial = PlaybackSettings())
    val artistSettings by artistSettingsStore.settings.collectAsState(initial = ArtistSettings())
    val libraryDisplaySettings by
        libraryDisplaySettingsStore.settings.collectAsState(initial = LibraryDisplaySettings())
    val persistedNavigationSettings: NavigationSettings? by
        navigationSettingsStore.settings.collectAsState(initial = null)
    val navigationSettings = persistedNavigationSettings ?: NavigationSettings()
    val themeMode = remember(themeSettingsStore) { themeSettingsStore.currentMode() }
    val albumViewMode = libraryDisplaySettings.albumViewMode
    val artistAlbumViewMode = libraryDisplaySettings.artistAlbumViewMode
    val unknownSongTitle = stringResource(R.string.unknown_song_title)
    val favoriteRecords by
        favoriteRepository.observeFavorites().collectAsState(initial = emptyList())
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    var playbackVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDrilldownTarget by remember { mutableStateOf<SearchDrilldownTarget?>(null) }
    var currentDestination by remember { mutableStateOf(MusicDestination.Playlist) }
    var presentedFromMore by remember { mutableStateOf(false) }
    var playlistAddModeActive by remember { mutableStateOf(false) }
    var moreSettingsPageActive by remember { mutableStateOf(false) }
    var navigationEditorVisible by remember { mutableStateOf(false) }
    var navigationLayoutInitialized by remember { mutableStateOf(false) }
    var navigationStateRestored by remember { mutableStateOf(false) }

    val navigationLayout = navigationSettings.layout
    // 加歌模式只临时替换底栏末位，不污染用户保存的导航布局。
    val bottomDestinations =
        remember(navigationLayout, playlistAddModeActive) {
            navigationLayout.bottomDestinationsEnsuring(
                MusicDestination.Songs.takeIf { playlistAddModeActive }
            )
        }
    val overflowDestinations = navigationLayout.overflowDestinations

    // 冷启动恢复上次一级板块；详情页和弹窗状态不进入持久化恢复范围。
    LaunchedEffect(persistedNavigationSettings, currentDestination) {
        val persistedLayout = persistedNavigationSettings?.layout ?: return@LaunchedEffect
        if (!navigationLayoutInitialized) {
            navigationLayoutInitialized = true
            val (restoredDestination, restoredFromMore) =
                persistedNavigationSettings?.restoredDestination()
                    ?: (persistedLayout.bottomDestinations.first() to false)
            currentDestination = restoredDestination
            presentedFromMore = restoredFromMore
            navigationStateRestored = true
        } else if (
            currentDestination != MusicDestination.More &&
                !persistedLayout.isPinned(currentDestination)
        ) {
            presentedFromMore = true
        }
    }

    LaunchedEffect(currentDestination, presentedFromMore, navigationStateRestored) {
        if (navigationStateRestored) {
            navigationSettingsStore.setLastDestination(currentDestination, presentedFromMore)
        }
    }
    var songsEditMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(emptySet<String>()) }
    var albumEditMode by remember { mutableStateOf(false) }
    var selectedAlbumIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedAlbumTitle by remember { mutableStateOf<String?>(null) }
    var selectedArtistTarget by remember { mutableStateOf<ArtistTarget?>(null) }
    var libraryRefreshVersion by remember { mutableStateOf(0) }
    var libraryRefreshing by remember { mutableStateOf(false) }
    var showSongDeleteConfirm by remember { mutableStateOf(false) }
    var pendingSongDeleteMediaIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingSongDeleteDismissAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPlaylistPickerMediaItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var pendingTrackActionItem by remember { mutableStateOf<MediaItem?>(null) }
    var pendingTrackActionSource by remember { mutableStateOf(TrackActionSource.Library) }
    var playbackPlaylistCreateRequest by remember {
        mutableStateOf<PlaylistNameDialogRequest.Create?>(null)
    }
    var ratingOverrides by remember { mutableStateOf(emptyMap<String, Int>()) }
    var snapshot by
        remember(controller) {
            mutableStateOf(controller.playbackBarSnapshot())
        }
    var playbackBarContentSnapshot by
        remember(controller) {
            mutableStateOf(snapshot)
        }
    val library = rememberLibraryMediaState(libraryRefreshVersion = libraryRefreshVersion)
    val libraryItems =
        remember(library.items, ratingOverrides) {
            library.items.withRatingOverrides(ratingOverrides)
        }
    val currentOnStartupReady by rememberUpdatedState(onStartupReady)
    LaunchedEffect(navigationStateRestored, library.loaded, controller) {
        if (!navigationStateRestored || !library.loaded || controller == null) {
            return@LaunchedEffect
        }
        // 资料库状态发布后再隐藏两个布局帧，让标题栏、专辑网格和首屏封面开始绑定。
        withFrameNanos {}
        withFrameNanos {}
        currentOnStartupReady()
    }
    val playbackBarMediaItem = playbackBarContentSnapshot.mediaItem
    val artworkRequestKey = playbackBarMediaItem?.artworkRequestKey()
    val artworkBitmap by
        produceState<Bitmap?>(
            initialValue = playbackBarMediaItem?.let(::peekArtworkBitmap),
            artworkRequestKey,
        ) {
            val mediaItem = playbackBarContentSnapshot.mediaItem
            if (mediaItem == null) {
                value = null
                return@produceState
            }
            value = peekArtworkBitmap(mediaItem) ?: value
            value = loadArtworkBitmap(context.applicationContext, mediaItem)
        }
    val albumPredictiveBackState = rememberPredictiveBackState()
    val artistRootPredictiveBackState = rememberPredictiveBackState()
    val artistNestedPredictiveBackState = rememberPredictiveBackState()
    val moreDestinationPredictiveBackState = rememberPredictiveBackState()
    val playbackBarRequestedVisible = snapshot.mediaItem != null
    val playbackBarHeight = 67.dp
    var playbackBarComposed by remember { mutableStateOf(false) }
    val openSearchOverlay = {
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
            snapshot = PlaybackBarSnapshot()
            return@DisposableEffect onDispose {}
        }
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    val nextSnapshot = player.playbackBarSnapshot()
                    snapshot = nextSnapshot
                    if (nextSnapshot.mediaItem != null) {
                        playbackBarContentSnapshot = nextSnapshot
                    }
                }
            }
        controller.addListener(listener)
        val initialSnapshot = controller.playbackBarSnapshot()
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
            Toast.makeText(context, R.string.can_not_add_to_queue_single_repeat, Toast.LENGTH_SHORT)
                .show()
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
        source: TrackActionSource,
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

    fun refreshLibrary() {
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
            }
                .getOrNull()
            libraryRefreshing = false
            if (result?.resultCode == SessionResult.RESULT_SUCCESS) {
                libraryRefreshVersion += 1
                Toast.makeText(context, R.string.library_refresh_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.library_refresh_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val deleteCoordinator =
        rememberMediaStoreDeleteCoordinator(
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

    PredictiveBackHandler(
        enabled = currentDestination == MusicDestination.Album && selectedAlbumId != null,
        state = albumPredictiveBackState,
    ) {
        closeAlbumDetail()
    }

    val selectedArtistParentTarget = selectedArtistTarget?.parentTarget()
    PredictiveBackHandler(
        enabled =
            currentDestination == MusicDestination.Artist &&
                selectedArtistTarget != null &&
                selectedArtistParentTarget == null,
        state = artistRootPredictiveBackState,
    ) {
        closeArtistDetail()
    }

    PredictiveBackHandler(
        enabled =
            presentedFromMore &&
                when (currentDestination) {
                    MusicDestination.Songs -> !songsEditMode
                    MusicDestination.Album -> selectedAlbumId == null && !albumEditMode
                    MusicDestination.Artist -> selectedArtistTarget == null
                    else -> false
                },
        state = moreDestinationPredictiveBackState,
    ) {
        returnToMore()
    }
    PredictiveBackHandler(
        enabled =
            currentDestination == MusicDestination.Artist &&
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
        val (artist, mediaStoreIds) =
            withContext(Dispatchers.IO) {
                request.resolveExternalAudioArtist(context.applicationContext) to
                    request.resolveExternalAudioMediaStoreIds(context.applicationContext)
            }
        val mediaItem =
            request.toExternalAudioMediaItem(
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

    val bottomNavigationHeight =
        dimensionResource(R.dimen.realtabcontent_margin_bottom) +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val realTabContentBottomMargin =
        if (playbackBarComposed) {
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
        com.smartisan.music.ui.components.SmartisanDrawableBackground(
            R.drawable.account_background,
            Modifier.fillMaxSize(),
        )
        val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
        val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
        val titleAreaHeight =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + titleContentHeight
        val destinationSurface: @Composable (MusicDestination, Boolean) -> Unit =
            { destination, fromMore ->
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(
                                bottom = if (hideBottomChrome) 0.dp else realTabContentBottomMargin
                            )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val titleBarContent:
                            @Composable
                            (String?, ArtistTarget?, Modifier) -> Unit =
                            { albumDetailTitle, artistTarget, titleModifier ->
                                MainTitleBar(
                                    destination = destination,
                                    songsEditMode =
                                        destination == MusicDestination.Songs && songsEditMode,
                                    selectedSongCount = selectedSongIds.size,
                                    albumEditMode =
                                        destination == MusicDestination.Album && albumEditMode,
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
                                        val nextMode =
                                            if (albumViewMode == AlbumViewMode.List) {
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
                                        val nextMode =
                                            if (artistAlbumViewMode == AlbumViewMode.List) {
                                                AlbumViewMode.Tile
                                            } else {
                                                AlbumViewMode.List
                                            }
                                        scope.launch {
                                            libraryDisplaySettingsStore.setArtistAlbumViewMode(
                                                nextMode
                                            )
                                        }
                                    },
                                    onRootBack = ::returnToMore.takeIf { fromMore },
                                    onSearchClick = ::openCurrentSearch,
                                    modifier = titleModifier,
                                )
                            }
                        if (destination !in DestinationsWithOwnedTitleBar) {
                            when (destination) {
                                MusicDestination.Album ->
                                    TitleBarTransition(
                                        secondaryKey = selectedAlbumTitle,
                                        modifier = Modifier.fillMaxWidth().height(titleAreaHeight),
                                        label = "album title transition",
                                        predictiveBackProgress = albumPredictiveBackState.progress,
                                        predictiveBackExitConsumed =
                                            albumPredictiveBackState.exitConsumed,
                                        onPredictiveBackExitConsumedReset =
                                            albumPredictiveBackState::reset,
                                        primaryContent = {
                                            titleBarContent(null, null, Modifier.fillMaxSize())
                                        },
                                        secondaryContent = { detailTitle ->
                                            titleBarContent(
                                                detailTitle,
                                                null,
                                                Modifier.fillMaxSize(),
                                            )
                                        },
                                    )
                                MusicDestination.Artist ->
                                    ArtistTitleStack(
                                        selectedTarget = selectedArtistTarget,
                                        rootPredictiveBackProgress =
                                            artistRootPredictiveBackState.progress,
                                        rootPredictiveBackExitConsumed =
                                            artistRootPredictiveBackState.exitConsumed,
                                        onRootPredictiveBackExitConsumedReset =
                                            artistRootPredictiveBackState::reset,
                                        nestedPredictiveBackProgress =
                                            artistNestedPredictiveBackState.progress,
                                        nestedPredictiveBackExitConsumed =
                                            artistNestedPredictiveBackState.exitConsumed,
                                        onNestedPredictiveBackExitConsumedReset =
                                            artistNestedPredictiveBackState::reset,
                                        modifier = Modifier.fillMaxWidth().height(titleAreaHeight),
                                    ) { artistTarget, titleModifier ->
                                        titleBarContent(null, artistTarget, titleModifier)
                                    }
                                else -> titleBarContent(null, null, Modifier.fillMaxWidth())
                            }
                        }
                        MusicTabContent(
                            destination = destination,
                            presentedFromMore = fromMore,
                            overflowDestinations = overflowDestinations,
                            mediaItems = libraryItems,
                            favoriteRecords = favoriteRecords,
                            libraryLoaded = library.loaded,
                            songsEditMode = destination == MusicDestination.Songs && songsEditMode,
                            selectedSongIds = selectedSongIds,
                            albumViewMode = albumViewMode,
                            albumEditMode = destination == MusicDestination.Album && albumEditMode,
                            selectedAlbumId = selectedAlbumId,
                            selectedAlbumIds = selectedAlbumIds,
                            albumPredictiveBackProgress = albumPredictiveBackState.progress,
                            albumPredictiveBackExitConsumed = albumPredictiveBackState.exitConsumed,
                            onAlbumPredictiveBackExitConsumedReset =
                                albumPredictiveBackState::reset,
                            artistAlbumViewMode = artistAlbumViewMode,
                            selectedArtistTarget = selectedArtistTarget,
                            artistRootPredictiveBackProgress =
                                artistRootPredictiveBackState.progress,
                            artistRootPredictiveBackExitConsumed =
                                artistRootPredictiveBackState.exitConsumed,
                            onArtistRootPredictiveBackExitConsumedReset =
                                artistRootPredictiveBackState::reset,
                            artistNestedPredictiveBackProgress =
                                artistNestedPredictiveBackState.progress,
                            artistNestedPredictiveBackExitConsumed =
                                artistNestedPredictiveBackState.exitConsumed,
                            onArtistNestedPredictiveBackExitConsumedReset =
                                artistNestedPredictiveBackState::reset,
                            moreDestinationPredictiveBackState = moreDestinationPredictiveBackState,
                            playbackBarOverlayHeight =
                                if (hideBottomChrome) 0.dp else playbackBarOverlayHeight,
                            hiddenMediaIds = libraryExclusions.hiddenMediaIds,
                            libraryRefreshVersion = libraryRefreshVersion,
                            libraryRefreshing = libraryRefreshing,
                            playbackSettings = playbackSettings,
                            artistSettings = artistSettings,
                            onRefreshLibrary = ::refreshLibrary,
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
                            themeMode = themeMode,
                            onTabPinnedChange = { route, pinned ->
                                scope.launch {
                                    navigationSettingsStore.setTabPinned(route, pinned)
                                }
                            },
                            onThemeModeChange = onThemeModeChange,
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
                                showTrackActions(item, TrackActionSource.Library)
                            },
                            onLovedSongsTrackMoreClick = { item ->
                                showTrackActions(item, TrackActionSource.Loved)
                            },
                            onPlaylistTrackMoreClick = { item ->
                                showTrackActions(item, TrackActionSource.Playlist)
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
                            onSearchClick = ::openCurrentSearch,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    if (
                        destination == MusicDestination.Artist ||
                            destination == MusicDestination.Album
                    ) {
                        TitleBarShadow(
                            modifier =
                                Modifier.align(Alignment.TopCenter)
                                    .offset(y = titleAreaHeight)
                                    .fillMaxWidth()
                                    .height(titleShadowHeight)
                                    .zIndex(1f)
                        )
                    }
                }
            }
        PageStackTransition(
            secondaryKey = currentDestination.takeIf { presentedFromMore },
            modifier = Modifier.fillMaxSize(),
            label = "more destination stack",
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
            Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                if (playbackBarComposed) {
                    PlaybackBar(
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
                        modifier = Modifier.fillMaxWidth().height(playbackBarHeight),
                        bottomDividerVisible = true,
                    )
                }
                MusicBottomBar(
                    currentDestination =
                        when {
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
        val trackActionItems =
            pendingTrackActionItem
                ?.let { actionItem ->
                    val mediaId = actionItem.mediaId
                    val isFavorite = mediaId in favoriteIds
                    val canAddToPlaylist =
                        mediaId.isNotBlank() && !actionItem.isExternalAudioLaunchItem()
                    val canFavorite =
                        mediaId.isNotBlank() && !actionItem.isExternalAudioLaunchItem()
                    val actions =
                        mutableListOf(
                            TrackActionItem(
                                labelRes = R.string.add_to_playlist,
                                iconRes = R.drawable.more_select_icon_addlist,
                                pressedIconRes = R.drawable.more_select_icon_addlist_down,
                                enabled = canAddToPlaylist,
                                onClick = {
                                    dismissTrackActions()
                                    requestAddToPlaylist(listOf(actionItem))
                                },
                            ),
                            TrackActionItem(
                                labelRes = R.string.add_to_queue,
                                iconRes = R.drawable.more_select_icon_addplay,
                                pressedIconRes = R.drawable.more_select_icon_addplay_down,
                                onClick = {
                                    dismissTrackActions()
                                    enqueueMediaItems(listOf(actionItem))
                                },
                            ),
                            TrackActionItem(
                                labelRes = if (isFavorite) R.string.cancel_love else R.string.love,
                                iconRes =
                                    if (isFavorite) {
                                        R.drawable.more_select_icon_favorite_cancel
                                    } else {
                                        R.drawable.more_select_icon_favorite_add
                                    },
                                pressedIconRes =
                                    if (isFavorite) {
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
                    if (pendingTrackActionSource == TrackActionSource.Library) {
                        actions +=
                            TrackActionItem(
                                labelRes = R.string.delete,
                                iconRes = R.drawable.more_select_icon_delete,
                                onClick = {
                                    dismissTrackActions()
                                    requestSongDeleteConfirmation(setOf(mediaId))
                                },
                            )
                    }
                    actions
                }
                .orEmpty()
        TrackActionsOverlay(
            visible = pendingTrackActionItem != null,
            actions = trackActionItems,
            onDismissRequest = ::dismissTrackActions,
            modifier = Modifier.fillMaxSize().zIndex(2.4f),
        )
        PlaybackOverlay(
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
        SearchOverlay(
            visible = searchVisible,
            query = searchQuery,
            mediaItems = libraryItems,
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
                showTrackActions(item, TrackActionSource.Library)
            },
            onDrilldownTargetChanged = { target ->
                searchDrilldownTarget = target
            },
            onAlbumClick = { albumId, albumTitle ->
                searchDrilldownTarget =
                    SearchDrilldownTarget.Album(
                        albumId = albumId,
                        albumTitle = albumTitle,
                    )
            },
            onArtistClick = { artistId, artistName ->
                searchDrilldownTarget =
                    SearchDrilldownTarget.Artist(
                        target =
                            ArtistTarget.Albums(
                                artistId = artistId,
                                artistName = artistName,
                            )
                    )
            },
            onToggleArtistAlbumViewMode = {
                val nextMode =
                    if (artistAlbumViewMode == AlbumViewMode.List) {
                        AlbumViewMode.Tile
                    } else {
                        AlbumViewMode.List
                    }
                scope.launch {
                    libraryDisplaySettingsStore.setArtistAlbumViewMode(nextMode)
                }
            },
            modifier = Modifier.fillMaxSize().zIndex(2f),
        )
        PlaybackPlaylistPickerOverlay(
            visible =
                pendingPlaylistPickerMediaItems != null && playbackPlaylistCreateRequest == null,
            playlists = playlists,
            onDismiss = {
                pendingPlaylistPickerMediaItems = null
            },
            onCreateNewPlaylist = {
                scope.launch {
                    playbackPlaylistCreateRequest =
                        PlaylistNameDialogRequest.Create(
                            initialName = playlistRepository.suggestNextUntitledName()
                        )
                }
            },
            onPlaylistSelected = { playlistId ->
                val mediaIds = pendingPlaylistPickerMediaItems?.map(MediaItem::mediaId).orEmpty()
                scope.launch {
                    val result = playlistRepository.addMediaIds(playlistId, mediaIds)
                    when {
                        result.addedCount > 0 -> {
                            Toast.makeText(context, R.string.playlist_added, Toast.LENGTH_SHORT)
                                .show()
                        }
                        result.duplicateCount > 0 -> {
                            Toast.makeText(
                                    context,
                                    R.string.playlist_song_exists,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                    }
                    pendingPlaylistPickerMediaItems = null
                }
            },
            modifier = Modifier.fillMaxSize().zIndex(4f),
        )
        PlaylistNameDialogOverlay(
            request = playbackPlaylistCreateRequest,
            onDismiss = {
                playbackPlaylistCreateRequest = null
            },
            onConfirm = { _, input ->
                val mediaIds = pendingPlaylistPickerMediaItems?.map(MediaItem::mediaId).orEmpty()
                scope.launch {
                    when (playlistRepository.createPlaylist(input, mediaIds)) {
                        PlaylistCreateResult.EmptyName -> {
                            Toast.makeText(
                                    context,
                                    R.string.playlist_create_failed,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                        PlaylistCreateResult.DuplicateName -> {
                            Toast.makeText(
                                    context,
                                    R.string.playlist_duplicate_name,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                        is PlaylistCreateResult.Success -> {
                            playbackPlaylistCreateRequest = null
                            pendingPlaylistPickerMediaItems = null
                            Toast.makeText(context, R.string.playlist_added, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            },
        )
        if (showSongDeleteConfirm) {
            SongDeleteConfirmOverlay(
                onDismiss = {
                    dismissSongDeleteConfirmation()
                },
                onConfirm = {
                    val mediaIds = pendingSongDeleteMediaIds
                    val dismissAction = pendingSongDeleteDismissAction
                    if (mediaIds.isEmpty()) {
                        dismissSongDeleteConfirmation()
                        return@SongDeleteConfirmOverlay
                    }
                    showSongDeleteConfirm = false
                    pendingSongDeleteMediaIds = emptySet()
                    pendingSongDeleteDismissAction = null
                    songsEditMode = false
                    selectedSongIds = emptySet()
                    requestSystemDeleteMediaIds(mediaIds)
                    dismissAction?.invoke()
                },
                modifier = Modifier.fillMaxSize().zIndex(2f),
            )
        }
        NavigationEditorOverlay(
            visible = navigationEditorVisible,
            layout = navigationLayout,
            selectedDestination =
                if (presentedFromMore) MusicDestination.More else currentDestination,
            onDismissRequest = {
                navigationEditorVisible = false
            },
            onCommit = { layout ->
                navigationEditorVisible = false
                scope.launch {
                    navigationSettingsStore.commitLayout(layout)
                }
            },
            modifier = Modifier.fillMaxSize().zIndex(5f),
        )
    }
}

private val DestinationsWithOwnedTitleBar =
    setOf(
        MusicDestination.Playlist,
        MusicDestination.More,
        MusicDestination.Genre,
        MusicDestination.LovedSongs,
        MusicDestination.Folder,
    )

private fun List<MediaItem>.withRatingOverrides(
    ratingOverrides: Map<String, Int>
): List<MediaItem> {
    if (isEmpty() || ratingOverrides.isEmpty()) {
        return this
    }
    return map { item ->
        val score = ratingOverrides[item.mediaId] ?: return@map item
        item.withPlaybackRating(score)
    }
}
