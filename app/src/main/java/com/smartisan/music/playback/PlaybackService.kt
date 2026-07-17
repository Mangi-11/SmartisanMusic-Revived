@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisan.music.playback

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.core.content.ContextCompat
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.smartisan.music.MainActivity
import com.smartisan.music.data.library.LibraryExclusions
import com.smartisan.music.data.library.LibraryExclusionsStore
import com.smartisan.music.data.playback.PlaybackStatsRepository
import com.smartisan.music.data.settings.PlaybackSettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class PlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var localAudioLibrary: LocalAudioLibrary
    private lateinit var libraryExecutor: ListeningExecutorService
    private lateinit var libraryRefreshExecutor: ListeningExecutorService
    private lateinit var libraryExclusionsStore: LibraryExclusionsStore
    private lateinit var playbackSettingsStore: PlaybackSettingsStore
    private lateinit var playbackStatsRepository: PlaybackStatsRepository
    private lateinit var playbackSessionStateStore: PlaybackSessionStateStore
    private var playbackSessionStateCoordinator: PlaybackSessionStateCoordinator? = null
    private var playbackPlayCountTracker: PlaybackPlayCountTracker? = null
    private var playbackAudioFxController: PlaybackAudioFxController? = null
    private var playbackMetadataPreloader: PlaybackMetadataPreloader? = null
    private var mediaSessionArtworkBitmapLoader: MediaSessionArtworkBitmapLoader? = null
    private var pendingStatsLibraryRefreshJob: Job? = null
    private var pendingRatingLibraryRefreshJob: Job? = null
    private var pendingPlaybackStartJob: Job? = null
    private var pendingPlaybackStartFuture: SettableFuture<SessionResult>? = null
    private val playbackStartRequestGeneration = AtomicLong()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playbackStartFadeController = PlaybackStartFadeController(serviceScope)
    @Volatile private var exclusionsSnapshot: LibraryExclusions = LibraryExclusions()
    private val exclusionsReady = CompletableDeferred<LibraryExclusions>()
    private val audioFxPlayerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            playbackAudioFxController?.setAudioSessionId(audioSessionId)
        }
    }
    private val playbackStartFadePlayerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val playbackPlayer = player ?: return
            playbackStartFadeController.onPlayWhenReadyChanged(playbackPlayer, playWhenReady)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val playbackPlayer = player ?: return
            playbackStartFadeController.onIsPlayingChanged(playbackPlayer, isPlaying)
        }
    }

    override fun onCreate() {
        super.onCreate()
        playbackStatsRepository = PlaybackStatsRepository.getInstance(this)
        localAudioLibrary = LocalAudioLibrary(
            context = this,
            playbackStatsProvider = playbackStatsRepository::getStats,
            playbackStatsByIdsProvider = playbackStatsRepository::getStats,
        )
        libraryExclusionsStore = LibraryExclusionsStore(this)
        playbackSettingsStore = PlaybackSettingsStore(this)
        playbackSessionStateStore = PlaybackSessionStateStore(this)
        libraryExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
        libraryRefreshExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_LOCAL)
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration(PlaylistPreloadDurationUs))
            }
        val artworkBitmapLoader = MediaSessionArtworkBitmapLoader(this)

        player = exoPlayer
        playbackAudioFxController = PlaybackAudioFxController().also { controller ->
            controller.setAudioSessionId(exoPlayer.audioSessionId)
        }
        exoPlayer.addListener(audioFxPlayerListener)
        exoPlayer.addListener(playbackStartFadePlayerListener)
        playbackMetadataPreloader = PlaybackMetadataPreloader(
            context = this,
            player = exoPlayer,
            scope = serviceScope,
        ).also { preloader ->
            preloader.start()
        }
        mediaSessionArtworkBitmapLoader = artworkBitmapLoader
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            PlaybackStartFadePlayer(exoPlayer, playbackStartFadeController),
            PlaybackLibrarySessionCallback(),
        )
            .setSessionActivity(createSessionActivityPendingIntent())
            .setBitmapLoader(artworkBitmapLoader)
            .setPeriodicPositionUpdateEnabled(false)
            .build()

        playbackPlayCountTracker = PlaybackPlayCountTracker(
            player = exoPlayer,
            repository = playbackStatsRepository,
            scope = serviceScope,
            onPlayCountChanged = {
                scheduleStatsLibraryRefresh()
            },
        ).also { tracker ->
            tracker.start()
        }

        playbackSessionStateCoordinator = PlaybackSessionStateCoordinator(
            player = exoPlayer,
            stateStore = playbackSessionStateStore,
            scope = serviceScope,
            canLoadLibraryItems = { hasAudioPermission() },
            loadLibraryItemsByQueueKeys = { queueKeys -> getAudioItemsByQueueKeys(queueKeys) },
        ).also { coordinator ->
            coordinator.start()
        }

        serviceScope.launch(Dispatchers.IO) {
            libraryExclusionsStore.exclusions.collect { exclusions ->
                exclusionsSnapshot = exclusions
                if (!exclusionsReady.isCompleted) {
                    exclusionsReady.complete(exclusions)
                }
                withContext(Dispatchers.Main.immediate) {
                    removeHiddenQueuedItems(exclusions)
                    mediaLibrarySession?.notifyChildrenChanged(
                        LocalAudioLibrary.ROOT_ID,
                        Int.MAX_VALUE,
                        null,
                    )
                }
            }
        }
        serviceScope.launch(Dispatchers.Main.immediate) {
            playbackSettingsStore.settings.collect { settings ->
                playbackAudioFxController?.setSettings(settings)
            }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        if (!exclusionsReady.isCompleted) {
            exclusionsReady.complete(exclusionsSnapshot)
        }
        playbackSessionStateCoordinator?.let { coordinator ->
            runBlocking {
                coordinator.saveNow()
            }
            coordinator.stop()
        }
        playbackSessionStateCoordinator = null
        playbackPlayCountTracker?.let { tracker ->
            runBlocking {
                tracker.stopAndFlush()
            }
        }
        playbackPlayCountTracker = null
        pendingStatsLibraryRefreshJob?.cancel()
        pendingStatsLibraryRefreshJob = null
        pendingRatingLibraryRefreshJob?.cancel()
        pendingRatingLibraryRefreshJob = null
        playbackMetadataPreloader?.stop()
        playbackMetadataPreloader = null
        cancelPendingPlaybackStart()
        serviceScope.cancel()
        PlaybackSleepTimer.cancel()
        player?.removeListener(audioFxPlayerListener)
        player?.removeListener(playbackStartFadePlayerListener)
        playbackStartFadeController.release(player)
        playbackAudioFxController?.release()
        playbackAudioFxController = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null

        player?.release()
        player = null
        mediaSessionArtworkBitmapLoader?.shutdown()
        mediaSessionArtworkBitmapLoader = null
        libraryExecutor.shutdown()
        libraryRefreshExecutor.shutdown()

        super.onDestroy()
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun getAudioItems(forceRefresh: Boolean = false): List<MediaItem> {
        if (!hasAudioPermission()) {
            return emptyList()
        }
        val exclusions = if (exclusionsReady.isCompleted) {
            exclusionsSnapshot
        } else {
            runBlocking { exclusionsReady.await() }
        }
        return localAudioLibrary.getAudioItems(forceRefresh = forceRefresh)
            .asSequence()
            .filter { item ->
                val relativePath = item.mediaMetadata.extras
                    ?.getString(LocalAudioLibrary.RelativePathExtraKey)
                !exclusions.isMediaHidden(item.mediaId, relativePath)
            }
            .toList()
    }

    private fun getAudioItemsByIds(mediaIds: List<String>): List<MediaItem> {
        if (!hasAudioPermission() || mediaIds.isEmpty()) {
            return emptyList()
        }
        val exclusions = if (exclusionsReady.isCompleted) {
            exclusionsSnapshot
        } else {
            runBlocking { exclusionsReady.await() }
        }
        return localAudioLibrary.getAudioItemsByIds(mediaIds)
            .asSequence()
            .filter { item ->
                val relativePath = item.mediaMetadata.extras
                    ?.getString(LocalAudioLibrary.RelativePathExtraKey)
                !exclusions.isMediaHidden(item.mediaId, relativePath)
            }
            .toList()
    }

    private fun resolveSessionPlaybackMediaItems(mediaItems: List<MediaItem>): MutableList<MediaItem> {
        mediaItems.resolveDirectSessionPlaybackItemsOrNull()?.let { directItems ->
            return directItems
        }
        val localItemsById = getAudioItemsByIds(
            mediaItems
                .filterNot(MediaItem::canResolveDirectSessionPlaybackItem)
                .map(MediaItem::mediaId),
        ).associateBy(MediaItem::mediaId)
        return mediaItems.mapNotNullTo(mutableListOf()) { item ->
            item.toDirectSessionPlaybackItemOrNull() ?: localItemsById[item.mediaId]
        }
    }

    private fun replaceResolvedQueueAndPlay(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        shuffleModeEnabled: Boolean,
    ): SessionResult {
        val safeStartIndex = playbackQueueStartIndex(
            itemCount = mediaItems.size,
            startIndex = startIndex,
        ) ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val playbackPlayer = player ?: return SessionResult(SessionError.ERROR_UNKNOWN)
        playbackStartFadeController.protectNextPlayback(playbackPlayer)
        playbackPlayer.replaceQueueAndPlayDirect(
            mediaItems = mediaItems,
            startIndex = safeStartIndex,
            shuffleModeEnabled = shuffleModeEnabled,
        )
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun replaceQueueAndPlayFromSessionCommand(args: Bundle): ListenableFuture<SessionResult> {
        val mediaItems = args.decodeReplaceQueueAndPlayMediaItems()
        val startIndex = args.getInt(ReplaceQueueStartIndexKey, 0)
        val shuffleModeEnabled = args.getBoolean(ReplaceQueueShuffleModeKey, false)
        val safeStartIndex = playbackQueueStartIndex(
            itemCount = mediaItems.size,
            startIndex = startIndex,
        ) ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
        val requestGeneration = playbackStartRequestGeneration.incrementAndGet()
        cancelPendingPlaybackStart()

        val resultFuture = SettableFuture.create<SessionResult>()
        pendingPlaybackStartFuture = resultFuture
        lateinit var startJob: Job
        startJob = serviceScope.launch {
            val result = try {
                val resolvedItems = withContext(Dispatchers.IO) {
                    resolveSessionPlaybackMediaItems(mediaItems)
                }
                if (playbackStartRequestGeneration.get() != requestGeneration) {
                    SessionResult(SessionResult.RESULT_SUCCESS)
                } else {
                    replaceResolvedQueueAndPlay(
                        mediaItems = resolvedItems,
                        startIndex = safeStartIndex,
                        shuffleModeEnabled = shuffleModeEnabled,
                    )
                }
            } catch (_: CancellationException) {
                SessionResult(SessionResult.RESULT_SUCCESS)
            } catch (error: Exception) {
                Log.w(
                    PlaybackDiagnosticsTag,
                    "Playback start resolution failed type=${error.javaClass.simpleName} " +
                        "message=${error.message}",
                )
                SessionResult(SessionError.ERROR_UNKNOWN)
            }
            if (!resultFuture.isDone) {
                resultFuture.set(result)
            }
            if (pendingPlaybackStartJob === startJob) {
                pendingPlaybackStartJob = null
                pendingPlaybackStartFuture = null
            }
        }
        pendingPlaybackStartJob = startJob
        resultFuture.addListener(
            {
                if (resultFuture.isCancelled) {
                    startJob.cancel()
                }
            },
            MoreExecutors.directExecutor(),
        )
        return resultFuture
    }

    private fun cancelPendingPlaybackStart() {
        pendingPlaybackStartJob?.cancel()
        pendingPlaybackStartJob = null
        pendingPlaybackStartFuture
            ?.takeUnless(SettableFuture<SessionResult>::isDone)
            ?.set(SessionResult(SessionResult.RESULT_SUCCESS))
        pendingPlaybackStartFuture = null
    }

    private suspend fun getAudioItemsByQueueKeys(queueKeys: List<PlaybackQueueSnapshotItem>): List<MediaItem> {
        if (queueKeys.isEmpty()) {
            return emptyList()
        }
        return if (hasAudioPermission()) {
            val exclusions = if (exclusionsReady.isCompleted) {
                exclusionsSnapshot
            } else {
                exclusionsReady.await()
            }
            localAudioLibrary.getAudioItemsByQueueKeys(queueKeys)
                .asSequence()
                .filter { item ->
                    val relativePath = item.mediaMetadata.extras
                        ?.getString(LocalAudioLibrary.RelativePathExtraKey)
                    !exclusions.isMediaHidden(item.mediaId, relativePath)
                }
                .toList()
        } else {
            emptyList()
        }
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            PlaybackSessionActivityRequestCode,
            MainActivity.createOpenPlaybackIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun removeHiddenQueuedItems(exclusions: LibraryExclusions) {
        player.removeMediaItemsMatching { item ->
            val relativePath = item.mediaMetadata.extras
                ?.getString(LocalAudioLibrary.RelativePathExtraKey)
            exclusions.isMediaHidden(item.mediaId, relativePath)
        }
    }

    private inner class PlaybackLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(ScratchSeekModeCommand)
                .add(StartSleepTimerCommand)
                .add(CancelSleepTimerCommand)
                .add(RefreshLibraryCommand)
                .add(InvalidateLibraryCommand)
                .add(SetTrackRatingCommand)
                .add(ReplaceQueueAndPlayCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(localAudioLibrary.getRootItem(), params),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return libraryExecutor.submit<LibraryResult<ImmutableList<MediaItem>>> {
                if (parentId != LocalAudioLibrary.ROOT_ID) {
                    return@submit LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params)
                }

                val items = getAudioItems()
                if (items.isEmpty() && !hasAudioPermission()) {
                    return@submit LibraryResult.ofError(
                        SessionError.ERROR_PERMISSION_DENIED,
                        params,
                    )
                }

                val fromIndex = (page * pageSize).coerceAtMost(items.size)
                val toIndex = (fromIndex + pageSize).coerceAtMost(items.size)
                LibraryResult.ofItemList(items.subList(fromIndex, toIndex), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return libraryExecutor.submit<LibraryResult<MediaItem>> {
                if (!hasAudioPermission() && mediaId != LocalAudioLibrary.ROOT_ID) {
                    return@submit LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                }

                val item = if (mediaId == LocalAudioLibrary.ROOT_ID) {
                    localAudioLibrary.getRootItem()
                } else {
                    getAudioItemsByIds(listOf(mediaId)).firstOrNull()
                }
                if (item == null) {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                } else {
                    LibraryResult.ofItem(item, null)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            mediaItems.resolveDirectSessionPlaybackItemsOrNull()?.let { resolvedItems ->
                return Futures.immediateFuture(resolvedItems)
            }
            return libraryExecutor.submit<MutableList<MediaItem>> {
                resolveSessionPlaybackMediaItems(mediaItems)
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ScratchSeekModeAction) {
                val enabled = args.getBoolean(ScratchSeekModeEnabledKey, false)
                player?.setSeekParameters(
                    if (enabled) SeekParameters.EXACT else SeekParameters.DEFAULT,
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == StartSleepTimerAction) {
                val durationMs = args.getLong(SleepTimerDurationMsKey, 0L)
                if (durationMs <= 0L) {
                    return Futures.immediateFuture(
                        SessionResult(SessionError.ERROR_BAD_VALUE),
                    )
                }
                PlaybackSleepTimer.start(durationMs) {
                    player?.pause()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == CancelSleepTimerAction) {
                PlaybackSleepTimer.cancel()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ReplaceQueueAndPlayAction) {
                return replaceQueueAndPlayFromSessionCommand(args)
            }
            if (customCommand.customAction == RefreshLibraryAction) {
                return libraryRefreshExecutor.submit<SessionResult> {
                    if (!hasAudioPermission()) {
                        return@submit SessionResult(SessionError.ERROR_PERMISSION_DENIED)
                    }

                    val result = localAudioLibrary.refreshAudioItems()
                    mediaLibrarySession?.notifyChildrenChanged(
                        LocalAudioLibrary.ROOT_ID,
                        result.items.size,
                        null,
                    )
                    serviceScope.launch {
                        playbackSessionStateCoordinator?.restoreIfQueueEmpty()
                    }
                    SessionResult(
                        if (result.successful) {
                            SessionResult.RESULT_SUCCESS
                        } else {
                            SessionError.ERROR_UNKNOWN
                        },
                    )
                }
            }
            if (customCommand.customAction == InvalidateLibraryAction) {
                return libraryRefreshExecutor.submit<SessionResult> {
                    if (!hasAudioPermission()) {
                        return@submit SessionResult(SessionError.ERROR_PERMISSION_DENIED)
                    }

                    val items = getAudioItems(forceRefresh = true)
                    mediaLibrarySession?.notifyChildrenChanged(
                        LocalAudioLibrary.ROOT_ID,
                        items.size,
                        null,
                    )
                    serviceScope.launch {
                        playbackSessionStateCoordinator?.restoreIfQueueEmpty()
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
            if (customCommand.customAction == SetTrackRatingAction) {
                val mediaId = args.getString(TrackRatingMediaIdKey)?.trim().orEmpty()
                val score = args.getInt(TrackRatingScoreKey, -1)
                if (mediaId.isBlank() || score !in TrackRatingMinScore..TrackRatingMaxScore) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                }
                return libraryRefreshExecutor.submit<SessionResult> {
                    val savedScore = runBlocking {
                        playbackStatsRepository.setScore(mediaId, score)
                    } ?: return@submit SessionResult(SessionError.ERROR_UNKNOWN)
                    runBlocking(Dispatchers.Main.immediate) {
                        updateQueuedTrackRating(mediaId, savedScore)
                        scheduleRatingLibraryRefresh()
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun scheduleStatsLibraryRefresh() {
        serviceScope.launch(Dispatchers.Main.immediate) {
            pendingStatsLibraryRefreshJob?.cancel()
            pendingStatsLibraryRefreshJob = serviceScope.launch(Dispatchers.Main.immediate) {
                delay(StatsLibraryRefreshDebounceMs)
                refreshStatsLibrary()
            }
        }
    }

    private fun scheduleRatingLibraryRefresh() {
        pendingRatingLibraryRefreshJob?.cancel()
        pendingRatingLibraryRefreshJob = serviceScope.launch(Dispatchers.Main.immediate) {
            delay(RatingLibraryRefreshDebounceMs)
            pendingStatsLibraryRefreshJob?.cancel()
            pendingStatsLibraryRefreshJob = null
            refreshStatsLibrary()
        }
    }

    private fun refreshStatsLibrary() {
        localAudioLibrary.invalidateAudioItems()
        mediaLibrarySession?.notifyChildrenChanged(
            LocalAudioLibrary.ROOT_ID,
            Int.MAX_VALUE,
            null,
        )
    }

    private fun updateQueuedTrackRating(mediaId: String, score: Int) {
        val playbackPlayer = player ?: return
        for (index in 0 until playbackPlayer.mediaItemCount) {
            val item = playbackPlayer.getMediaItemAt(index)
            if (item.mediaId == mediaId) {
                playbackPlayer.replaceMediaItem(index, item.withPlaybackRating(score))
            }
        }
    }

    private companion object {
        private const val PlaybackSessionActivityRequestCode = 1001
        private const val StatsLibraryRefreshDebounceMs = 600L
        private const val RatingLibraryRefreshDebounceMs = 250L
        private const val PlaylistPreloadDurationUs = 30_000_000L
    }
}

private class PlaybackStartFadePlayer(
    private val playbackPlayer: Player,
    private val fadeController: PlaybackStartFadeController,
) : ForwardingPlayer(playbackPlayer) {

    override fun play() {
        fadeController.protectResumeIfNeeded(playbackPlayer)
        super.play()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            fadeController.protectResumeIfNeeded(playbackPlayer)
        }
        super.setPlayWhenReady(playWhenReady)
    }
}

private class PlaybackStartFadeController(
    private val scope: CoroutineScope,
) {
    private var fadeJob: Job? = null
    private var fadeArmTimeoutJob: Job? = null
    private var fadeArmed = false
    private var lastPausedAtMs: Long = 0L
    private var lastPausedMediaId: String? = null
    private var hasPlayed = false

    fun onPlayWhenReadyChanged(playbackPlayer: Player, playWhenReady: Boolean) {
        if (playWhenReady) {
            protectResumeIfNeeded(playbackPlayer)
            return
        }
        cancel(playbackPlayer, resetVolumeToFull = true)
        val currentMediaItem = playbackPlayer.currentMediaItem
        if (hasPlayed && currentMediaItem != null) {
            lastPausedAtMs = SystemClock.elapsedRealtime()
            lastPausedMediaId = currentMediaItem.mediaId
        }
    }

    fun onIsPlayingChanged(playbackPlayer: Player, isPlaying: Boolean) {
        if (isPlaying) {
            hasPlayed = true
            startFadeIfArmed(playbackPlayer)
        }
    }

    fun protectResumeIfNeeded(playbackPlayer: Player) {
        if (fadeJob != null || fadeArmed || !shouldProtectResume(playbackPlayer)) {
            return
        }
        lastPausedAtMs = 0L
        lastPausedMediaId = null
        armFade(playbackPlayer, startImmediatelyIfPlaying = true)
    }

    fun protectNextPlayback(playbackPlayer: Player) {
        lastPausedAtMs = 0L
        lastPausedMediaId = null
        hasPlayed = false
        armFade(playbackPlayer, startImmediatelyIfPlaying = false)
    }

    fun release(playbackPlayer: Player?) {
        cancel(playbackPlayer, resetVolumeToFull = true)
    }

    private fun shouldProtectResume(playbackPlayer: Player): Boolean {
        val currentMediaItem = playbackPlayer.currentMediaItem ?: return false
        if (lastPausedAtMs <= 0L) {
            return false
        }
        val pausedMediaId = lastPausedMediaId
        if (!pausedMediaId.isNullOrBlank() && currentMediaItem.mediaId != pausedMediaId) {
            return false
        }
        return SystemClock.elapsedRealtime() - lastPausedAtMs >= ResumeFadeThresholdMs
    }

    private fun armFade(
        playbackPlayer: Player,
        startImmediatelyIfPlaying: Boolean,
    ) {
        cancel(playbackPlayer = null, resetVolumeToFull = false)
        fadeArmed = true
        playbackPlayer.volume = 0f
        fadeArmTimeoutJob = scope.launch {
            delay(StartFadeArmTimeoutMs)
            if (fadeArmed && fadeJob == null) {
                playbackPlayer.volume = 1f
                fadeArmed = false
                fadeArmTimeoutJob = null
            }
        }
        if (startImmediatelyIfPlaying && playbackPlayer.isPlaying) {
            startFadeIfArmed(playbackPlayer)
        }
    }

    private fun startFadeIfArmed(playbackPlayer: Player) {
        if (!fadeArmed || fadeJob != null) {
            return
        }
        fadeArmTimeoutJob?.cancel()
        fadeArmTimeoutJob = null
        val steps = (ResumeFadeDurationMs / ResumeFadeStepIntervalMs)
            .toInt()
            .coerceIn(ResumeFadeMinSteps, ResumeFadeMaxSteps)
        val stepDelay = (ResumeFadeDurationMs / steps).coerceAtLeast(1L)
        fadeJob = scope.launch {
            repeat(steps) { step ->
                delay(stepDelay)
                playbackPlayer.volume = ((step + 1).toFloat() / steps).coerceAtMost(1f)
            }
            playbackPlayer.volume = 1f
            fadeJob = null
            fadeArmed = false
            lastPausedMediaId = null
        }
    }

    private fun cancel(
        playbackPlayer: Player?,
        resetVolumeToFull: Boolean,
    ) {
        val shouldRestoreVolume = resetVolumeToFull &&
            (fadeArmed || fadeJob?.isActive == true || fadeArmTimeoutJob?.isActive == true)
        fadeJob?.cancel()
        fadeJob = null
        fadeArmTimeoutJob?.cancel()
        fadeArmTimeoutJob = null
        fadeArmed = false
        if (shouldRestoreVolume) {
            playbackPlayer?.volume = 1f
        }
    }

    private companion object {
        private const val ResumeFadeThresholdMs = 800L
        private const val ResumeFadeDurationMs = 120L
        private const val ResumeFadeStepIntervalMs = 40L
        private const val StartFadeArmTimeoutMs = 5_000L
        private const val ResumeFadeMinSteps = 4
        private const val ResumeFadeMaxSteps = 30
    }
}

private const val PlaybackDiagnosticsTag = "SmartisanPlayback"
