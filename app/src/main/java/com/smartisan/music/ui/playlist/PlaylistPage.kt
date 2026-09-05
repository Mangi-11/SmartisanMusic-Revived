package com.smartisan.music.ui.playlist

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.data.playlist.PlaylistCreateResult
import com.smartisan.music.data.playlist.PlaylistRenameResult
import com.smartisan.music.data.playlist.PlaylistRepository
import com.smartisan.music.data.playlist.UserPlaylistDetail
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.components.withSelection
import com.smartisan.music.ui.shell.PageStackTransition
import com.smartisan.music.ui.shell.PredictiveBackHandler
import com.smartisan.music.ui.shell.PredictiveBackState
import com.smartisan.music.ui.shell.rememberPredictiveBackState
import com.smartisan.music.ui.shell.titlebar.TitleBarShadow
import com.smartisan.music.ui.songs.SongsPage
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val PlaylistAddModeSlideMillis = 300
internal const val PlaylistRootFooterThreshold = 8
private val PlaylistAddModeEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

internal data class PlaylistTarget(
    val playlistId: String,
    val title: String,
)

private data class PlaylistDetailSnapshot(
    val playlistId: String,
    val playlist: UserPlaylistDetail?,
    val title: String,
    val tracks: List<MediaItem>,
    val libraryLoading: Boolean,
)

internal sealed interface PlaylistNameDialogRequest {
    val initialName: String

    data class Create(
        override val initialName: String,
        @param:StringRes val titleRes: Int = R.string.new_playlist,
    ) : PlaylistNameDialogRequest

    data class Rename(
        val playlistId: String,
        override val initialName: String,
    ) : PlaylistNameDialogRequest
}

internal enum class PlaylistDeleteRequest {
    RootSelected,
    DetailPlaylist,
    DetailTracks,
}

@Composable
internal fun PlaylistPage(
    mediaItems: List<MediaItem>,
    libraryLoaded: Boolean,
    active: Boolean,
    hiddenMediaIds: Set<String>,
    onTrackMoreClick: (MediaItem) -> Unit,
    onAddModeActiveChanged: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onClose: (() -> Unit)?,
    closePredictiveBackState: PredictiveBackState?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val scope = rememberCoroutineScope()
    val playlistRepository =
        remember(context.applicationContext) {
            PlaylistRepository.getInstance(context.applicationContext)
        }
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    val visibleSongs =
        remember(mediaItems, hiddenMediaIds) {
            mediaItems.filterNot { item -> item.mediaId in hiddenMediaIds }
        }
    val songsById =
        remember(visibleSongs) {
            visibleSongs.associateBy(MediaItem::mediaId)
        }

    var target by remember { mutableStateOf<PlaylistTarget?>(null) }
    var rootEditMode by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf(emptySet<String>()) }
    var detailEditMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf(emptySet<String>()) }
    var addMode by remember { mutableStateOf(false) }
    var addModeTarget by remember { mutableStateOf<PlaylistTarget?>(null) }
    var addModeReturnsToRoot by remember { mutableStateOf(false) }
    var selectedAddSongIds by remember { mutableStateOf(emptySet<String>()) }
    var nameDialogRequest by remember { mutableStateOf<PlaylistNameDialogRequest?>(null) }
    var deleteRequest by remember { mutableStateOf<PlaylistDeleteRequest?>(null) }
    val detailPredictiveBackState = rememberPredictiveBackState()

    val activePlaylistId = target?.playlistId
    val activePlaylistFlow =
        remember(activePlaylistId, playlistRepository) {
            activePlaylistId?.let(playlistRepository::observePlaylistDetail) ?: flowOf(null)
        }
    val activePlaylist by activePlaylistFlow.collectAsState(initial = null)
    val activeSummary =
        remember(playlists, activePlaylistId) {
            activePlaylistId?.let { id -> playlists.firstOrNull { playlist -> playlist.id == id } }
        }
    val detailTitle = activePlaylist?.name ?: activeSummary?.name ?: target?.title.orEmpty()
    val detailTracks =
        remember(activePlaylist, songsById) {
            activePlaylist?.mediaIds?.mapNotNull(songsById::get).orEmpty()
        }
    val detailPlaylistHasKnownTracks =
        activePlaylist?.mediaIds?.isNotEmpty() == true ||
            (activePlaylist == null && (activeSummary?.songCount ?: 0) > 0)
    val detailLibraryLoading = target != null && !libraryLoaded && detailPlaylistHasKnownTracks
    val addModeExistingIds =
        remember(addModeTarget, activePlaylistId, activePlaylist) {
            if (addModeTarget?.playlistId == activePlaylistId) {
                activePlaylist?.mediaIds?.toSet().orEmpty()
            } else {
                emptySet()
            }
        }
    var retainedDetailSnapshot by remember {
        mutableStateOf<PlaylistDetailSnapshot?>(null)
    }
    val addModeVisible = addMode && addModeTarget != null

    fun closeAddMode() {
        addMode = false
        selectedAddSongIds = emptySet()
        if (addModeReturnsToRoot) {
            target = null
        }
        addModeReturnsToRoot = false
    }

    LaunchedEffect(activePlaylistId, activePlaylist, playlists) {
        if (
            activePlaylistId != null &&
                activePlaylist == null &&
                playlists.none { it.id == activePlaylistId }
        ) {
            target = null
            detailEditMode = false
            addMode = false
            addModeTarget = null
            addModeReturnsToRoot = false
            selectedTrackIds = emptySet()
            selectedAddSongIds = emptySet()
        }
    }
    LaunchedEffect(
        activePlaylistId,
        activePlaylist,
        detailTitle,
        detailTracks,
        detailLibraryLoading,
    ) {
        val playlistId = activePlaylistId ?: return@LaunchedEffect
        retainedDetailSnapshot =
            PlaylistDetailSnapshot(
                playlistId = playlistId,
                playlist = activePlaylist,
                title = detailTitle,
                tracks = detailTracks,
                libraryLoading = detailLibraryLoading,
            )
    }
    LaunchedEffect(addModeVisible) {
        onAddModeActiveChanged(addModeVisible)
    }
    DisposableEffect(Unit) {
        onDispose {
            onAddModeActiveChanged(false)
        }
    }

    BackHandler(enabled = addModeVisible) {
        closeAddMode()
    }
    BackHandler(enabled = !addModeVisible && detailEditMode) {
        detailEditMode = false
        selectedTrackIds = emptySet()
    }
    BackHandler(enabled = target == null && rootEditMode) {
        rootEditMode = false
        selectedPlaylistIds = emptySet()
    }
    if (closePredictiveBackState != null && onClose != null) {
        PredictiveBackHandler(
            enabled = active && target == null && !rootEditMode && !addModeVisible,
            state = closePredictiveBackState,
            onBack = onClose,
        )
    } else if (onClose != null) {
        BackHandler(enabled = active && target == null && !rootEditMode && !addModeVisible) {
            onClose()
        }
    }
    PredictiveBackHandler(
        enabled = !addModeVisible && !detailEditMode && target != null,
        state = detailPredictiveBackState,
    ) {
        target = null
    }

    val titleAreaHeight =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            dimensionResource(R.dimen.title_bar_height)
    val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)

    Box(modifier = modifier.fillMaxSize().background(colorResource(R.color.page_background))) {
        Column(modifier = Modifier.fillMaxSize()) {
            PlaylistTitleArea(
                target = target,
                detailTitle = detailTitle,
                rootEditMode = rootEditMode,
                rootSelectedCount = selectedPlaylistIds.size,
                detailEditMode = detailEditMode,
                predictiveBackProgress = detailPredictiveBackState.progress,
                predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                onRootEnterEdit = {
                    rootEditMode = true
                    selectedPlaylistIds = emptySet()
                },
                onRootExitEdit = {
                    rootEditMode = false
                    selectedPlaylistIds = emptySet()
                },
                onRootDeleteSelected = {
                    if (selectedPlaylistIds.isNotEmpty()) {
                        deleteRequest = PlaylistDeleteRequest.RootSelected
                    }
                },
                onRootBack = onClose,
                onDetailBack = {
                    target = null
                    detailEditMode = false
                    addMode = false
                    addModeTarget = null
                    addModeReturnsToRoot = false
                    selectedTrackIds = emptySet()
                    selectedAddSongIds = emptySet()
                },
                onDetailEnterEdit = {
                    detailEditMode = true
                    selectedTrackIds = emptySet()
                },
                onDetailExitEdit = {
                    detailEditMode = false
                    selectedTrackIds = emptySet()
                },
                onSearchClick = onSearchClick,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PageStackTransition(
                    secondaryKey = target,
                    modifier = Modifier.fillMaxSize(),
                    label = "playlist transition",
                    predictiveBackProgress = detailPredictiveBackState.progress,
                    predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                    onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                    primaryContent = {
                        PlaylistRootPage(
                            active = active,
                            playlists = playlists,
                            editMode = rootEditMode,
                            selectedPlaylistIds = selectedPlaylistIds,
                            onCreatePlaylist = {
                                scope.launch {
                                    nameDialogRequest =
                                        PlaylistNameDialogRequest.Create(
                                            initialName =
                                                playlistRepository.suggestNextUntitledName()
                                        )
                                }
                            },
                            onRenamePlaylist = { playlist ->
                                nameDialogRequest =
                                    PlaylistNameDialogRequest.Rename(
                                        playlistId = playlist.id,
                                        initialName = playlist.name,
                                    )
                            },
                            onPlaylistClick = { playlist ->
                                if (rootEditMode) {
                                    selectedPlaylistIds =
                                        selectedPlaylistIds.togglePlaylistSelection(playlist.id)
                                } else {
                                    target =
                                        PlaylistTarget(
                                            playlistId = playlist.id,
                                            title = playlist.name,
                                        )
                                }
                            },
                            onPlaylistSelectionChange = { playlist, selected ->
                                selectedPlaylistIds =
                                    selectedPlaylistIds.withSelection(playlist.id, selected)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    secondaryContent = { playlistTarget ->
                        val detailSnapshot =
                            if (playlistTarget == target) {
                                PlaylistDetailSnapshot(
                                    playlistId = playlistTarget.playlistId,
                                    playlist = activePlaylist,
                                    title = detailTitle,
                                    tracks = detailTracks,
                                    libraryLoading = detailLibraryLoading,
                                )
                            } else {
                                retainedDetailSnapshot?.takeIf { snapshot ->
                                    snapshot.playlistId == playlistTarget.playlistId
                                }
                                    ?: PlaylistDetailSnapshot(
                                        playlistId = playlistTarget.playlistId,
                                        playlist = activePlaylist,
                                        title = playlistTarget.title,
                                        tracks = detailTracks,
                                        libraryLoading = detailLibraryLoading,
                                    )
                            }
                        PlaylistDetailPage(
                            active = active && !addModeVisible,
                            playlist = detailSnapshot.playlist,
                            title = detailSnapshot.title,
                            tracks = detailSnapshot.tracks,
                            libraryLoading = detailSnapshot.libraryLoading,
                            editMode = detailEditMode,
                            selectedTrackIds = selectedTrackIds,
                            browser = browser,
                            onShuffle = {
                                if (detailSnapshot.tracks.isEmpty()) {
                                    return@PlaylistDetailPage
                                }
                                browser.replaceQueueAndPlayShuffled(detailSnapshot.tracks)
                            },
                            onDeletePlaylist = {
                                deleteRequest = PlaylistDeleteRequest.DetailPlaylist
                            },
                            onEditModeChange = { enabled ->
                                detailEditMode = enabled
                                selectedTrackIds = emptySet()
                            },
                            onAddOrRemoveClick = {
                                if (selectedTrackIds.isEmpty()) {
                                    addModeTarget = target
                                    addModeReturnsToRoot = false
                                    addMode = true
                                    selectedAddSongIds = emptySet()
                                } else {
                                    deleteRequest = PlaylistDeleteRequest.DetailTracks
                                }
                            },
                            onToggleAll = { checked ->
                                selectedTrackIds =
                                    if (checked) {
                                        detailSnapshot.tracks.map(MediaItem::mediaId).toSet()
                                    } else {
                                        emptySet()
                                    }
                            },
                            onReorderTracks = { orderedMediaIds ->
                                val playlistId = target?.playlistId ?: return@PlaylistDetailPage
                                scope.launch {
                                    playlistRepository.reorderVisibleMediaIds(
                                        playlistId,
                                        orderedMediaIds,
                                    )
                                }
                            },
                            onTrackSelectionChange = { mediaId, selected ->
                                selectedTrackIds = selectedTrackIds.withSelection(mediaId, selected)
                            },
                            onTrackClick = { item, index ->
                                if (detailEditMode) {
                                    selectedTrackIds =
                                        selectedTrackIds.togglePlaylistSelection(item.mediaId)
                                    return@PlaylistDetailPage
                                }
                                browser.replaceQueueAndPlay(detailSnapshot.tracks, index)
                            },
                            onTrackMoreClick = onTrackMoreClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
        if (!addModeVisible) {
            TitleBarShadow(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .offset(y = titleAreaHeight)
                        .fillMaxWidth()
                        .height(titleShadowHeight)
                        .zIndex(1f)
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = addModeVisible,
            modifier = Modifier.fillMaxSize().zIndex(2f),
            enter =
                slideInVertically(
                    animationSpec =
                        tween(
                            durationMillis = PlaylistAddModeSlideMillis,
                            easing = PlaylistAddModeEasing,
                        ),
                    initialOffsetY = { it },
                ),
            exit =
                slideOutVertically(
                    animationSpec =
                        tween(
                            durationMillis = PlaylistAddModeSlideMillis,
                            easing = PlaylistAddModeEasing,
                        ),
                    targetOffsetY = { it },
                ),
        ) {
            val currentAddModeTarget = addModeTarget ?: return@AnimatedVisibility
            Column(
                modifier = Modifier.fillMaxSize().background(colorResource(R.color.page_background))
            ) {
                PlaylistAddModeTitleArea(
                    target = currentAddModeTarget,
                    onConfirm = {
                        val playlistId =
                            addModeTarget?.playlistId ?: return@PlaylistAddModeTitleArea
                        val mediaIds = selectedAddSongIds.toList()
                        if (mediaIds.isEmpty()) {
                            closeAddMode()
                            return@PlaylistAddModeTitleArea
                        }
                        scope.launch {
                            playlistRepository.addMediaIds(playlistId, mediaIds)
                            closeAddMode()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SongsPage(
                    mediaItems = visibleSongs,
                    libraryLoaded = libraryLoaded,
                    active = active && addModeVisible,
                    editMode = true,
                    selectedSongIds = selectedAddSongIds,
                    hiddenMediaIds = addModeExistingIds,
                    onSongSelectionChange = { mediaId, selected ->
                        selectedAddSongIds = selectedAddSongIds.withSelection(mediaId, selected)
                    },
                    onTrackMoreClick = {},
                    onRequestSongDeleteConfirmation = { _, onDismiss ->
                        onDismiss?.invoke()
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }

    PlaylistNameDialogOverlay(
        request = nameDialogRequest,
        onDismiss = {
            nameDialogRequest = null
        },
        onConfirm = { request, name ->
            scope.launch {
                when (request) {
                    is PlaylistNameDialogRequest.Create -> {
                        when (val result = playlistRepository.createPlaylist(name)) {
                            is PlaylistCreateResult.Success -> {
                                nameDialogRequest = null
                                val createdTarget =
                                    PlaylistTarget(
                                        playlistId = result.playlistId,
                                        title = name.trim(),
                                    )
                                target = createdTarget
                                detailEditMode = false
                                selectedTrackIds = emptySet()
                                if (visibleSongs.isNotEmpty() || !libraryLoaded) {
                                    addModeTarget = createdTarget
                                    addModeReturnsToRoot = true
                                    target = null
                                    addMode = true
                                    selectedAddSongIds = emptySet()
                                }
                            }
                            PlaylistCreateResult.DuplicateName -> {
                                Toast.makeText(
                                        context,
                                        R.string.playlist_duplicate_name,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                            PlaylistCreateResult.EmptyName -> Unit
                        }
                    }
                    is PlaylistNameDialogRequest.Rename -> {
                        when (playlistRepository.renamePlaylist(request.playlistId, name)) {
                            PlaylistRenameResult.Success -> {
                                nameDialogRequest = null
                                if (target?.playlistId == request.playlistId) {
                                    target = target?.copy(title = name.trim())
                                }
                            }
                            PlaylistRenameResult.DuplicateName -> {
                                Toast.makeText(
                                        context,
                                        R.string.playlist_duplicate_name,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                            PlaylistRenameResult.EmptyName,
                            PlaylistRenameResult.MissingPlaylist -> Unit
                        }
                    }
                }
            }
        },
    )

    PlaylistDeleteDialog(
        request = deleteRequest,
        onDismiss = {
            deleteRequest = null
        },
        onConfirm = { request ->
            scope.launch {
                when (request) {
                    PlaylistDeleteRequest.RootSelected -> {
                        playlistRepository.deletePlaylists(selectedPlaylistIds)
                        selectedPlaylistIds = emptySet()
                        rootEditMode = false
                    }
                    PlaylistDeleteRequest.DetailPlaylist -> {
                        val playlistId = target?.playlistId
                        if (playlistId != null) {
                            playlistRepository.deletePlaylists(setOf(playlistId))
                        }
                        target = null
                        detailEditMode = false
                        addMode = false
                        selectedTrackIds = emptySet()
                    }
                    PlaylistDeleteRequest.DetailTracks -> {
                        val playlistId = target?.playlistId
                        if (playlistId != null) {
                            playlistRepository.removeMediaIds(playlistId, selectedTrackIds)
                        }
                        selectedTrackIds = emptySet()
                        detailEditMode = false
                    }
                }
                deleteRequest = null
            }
        },
    )
}

internal fun String.ellipsizeMiddle(maxChars: Int): String {
    if (length <= maxChars) {
        return this
    }
    return take((maxChars - 3).coerceAtLeast(1)) + "..."
}
