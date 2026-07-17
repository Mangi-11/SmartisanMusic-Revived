package com.smartisan.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PlaybackSessionStateCoordinator(
    private val player: ExoPlayer,
    private val stateStore: PlaybackSessionStateStore,
    private val scope: CoroutineScope,
    private val canLoadLibraryItems: () -> Boolean,
    private val loadLibraryItemsByQueueKeys: suspend (List<PlaybackQueueSnapshotItem>) -> List<MediaItem>,
) {

    private var persistJob: Job? = null
    private var periodicSaveJob: Job? = null
    private var restoring = false
    private var lastQueuedSnapshot: PlaybackSessionSnapshot? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (restoring) {
                return
            }
            if (
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                persist(delayMillis = PlaybackSessionDebounceSaveMs)
            }
        }
    }

    fun start() {
        player.addListener(playerListener)
        periodicSaveJob = scope.launch {
            while (isActive) {
                delay(PlaybackSessionPeriodicSaveIntervalMs)
                if (!restoring && player.isPlaying && player.mediaItemCount > 0) {
                    persist()
                }
            }
        }
        restore()
    }

    fun restoreIfQueueEmpty() {
        if (player.mediaItemCount == 0) {
            restore()
        }
    }

    fun stop() {
        player.removeListener(playerListener)
        periodicSaveJob?.cancel()
        persistJob?.cancel()
        periodicSaveJob = null
        persistJob = null
    }

    suspend fun saveNow() {
        persistJob?.cancel()
        val snapshot = player.toPlaybackSessionSnapshot()
        if (shouldSkipEmptyQueueSave(snapshot)) {
            return
        }
        withContext(Dispatchers.IO) {
            stateStore.save(snapshot)
        }
        lastQueuedSnapshot = snapshot
    }

    private fun restore() {
        if (restoring) {
            return
        }
        restoring = true
        scope.launch {
            var shouldPersistAfterRestore = true
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    stateStore.load()
                }
                lastQueuedSnapshot = snapshot
                player.repeatMode = snapshot.repeatMode.sanitizedRepeatMode()
                player.shuffleModeEnabled = snapshot.shuffleModeEnabled
                if (snapshot.mediaIds.isNotEmpty()) {
                    val restoredItems = restoreItems(snapshot).filterRestorablePlaybackItems()
                    if (restoredItems.isEmpty() && !canLoadLibraryItems()) {
                        shouldPersistAfterRestore = false
                    } else if (restoredItems.isNotEmpty() && player.mediaItemCount == 0) {
                        val restoredIndex = snapshot.restoredIndexIn(restoredItems)
                        val restoredPositionMs = if (
                            restoredItems[restoredIndex].mediaId == snapshot.currentMediaId
                        ) {
                            snapshot.positionMs.coerceAtLeast(0L)
                        } else {
                            0L
                        }
                        player.restoreQueuePaused(restoredItems, restoredIndex, restoredPositionMs)
                    }
                }
            } finally {
                restoring = false
            }
            if (shouldPersistAfterRestore) {
                persist()
            }
        }
    }

    private suspend fun restoreItems(snapshot: PlaybackSessionSnapshot): List<MediaItem> {
        val items = withContext(Dispatchers.IO) {
            loadLibraryItemsByQueueKeys(snapshot.queueItems)
        }
        return restoreQueueItemOccurrences(snapshot.queueItems, items)
    }

    private fun PlaybackSessionSnapshot.restoredIndexIn(items: List<MediaItem>): Int {
        return restoredQueueIndex(this, items)
    }

    private fun persist(delayMillis: Long = 0L) {
        if (restoring) {
            return
        }
        persistJob?.cancel()
        persistJob = scope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            if (restoring) {
                return@launch
            }
            val snapshot = player.toPlaybackSessionSnapshot()
            if (shouldSkipEmptyQueueSave(snapshot)) {
                return@launch
            }
            if (snapshot == lastQueuedSnapshot) {
                return@launch
            }
            withContext(Dispatchers.IO) {
                stateStore.save(snapshot)
            }
            lastQueuedSnapshot = snapshot
        }
    }

    private fun shouldSkipEmptyQueueSave(snapshot: PlaybackSessionSnapshot): Boolean {
        return snapshot.mediaIds.isEmpty() && !canLoadLibraryItems()
    }

    private companion object {
        private const val PlaybackSessionDebounceSaveMs = 250L
        private const val PlaybackSessionPeriodicSaveIntervalMs = 15_000L
    }
}

private fun Player.toPlaybackSessionSnapshot(): PlaybackSessionSnapshot {
    val queue = buildList {
        for (index in 0 until mediaItemCount) {
            val mediaId = getMediaItemAt(index).mediaId.trim()
            if (mediaId.isNotEmpty()) {
                add(mediaId)
            }
        }
    }
    val queueItems = buildList {
        for (index in 0 until mediaItemCount) {
            val item = getMediaItemAt(index)
            val mediaId = item.mediaId.trim()
            if (mediaId.isNotEmpty()) {
                add(
                    item.toPlaybackQueueSnapshotItem(mediaId),
                )
            }
        }
    }
    return PlaybackSessionSnapshot(
        mediaIds = queue,
        queueItems = queueItems,
        currentMediaId = currentMediaItem
            ?.mediaId
            ?.trim()
            ?.takeIf(String::isNotEmpty),
        currentIndex = currentMediaItemIndex.coerceAtLeast(0),
        positionMs = currentPosition.coerceAtLeast(0L),
        repeatMode = repeatMode.sanitizedRepeatMode(),
        shuffleModeEnabled = shuffleModeEnabled,
    )
}

private fun MediaItem.toPlaybackQueueSnapshotItem(mediaId: String): PlaybackQueueSnapshotItem {
    return PlaybackQueueSnapshotItem(
        mediaId = mediaId,
        stableKey = stableKey.orEmpty(),
    )
}

/**
 * Rebuilds the saved queue without collapsing repeated local tracks. Media ids are preferred,
 * while the stable library key lets a moved or re-indexed file keep its queue position.
 */
internal fun restoreQueueItemOccurrences(
    queueItems: List<PlaybackQueueSnapshotItem>,
    candidates: List<MediaItem>,
): List<MediaItem> {
    if (queueItems.isEmpty() || candidates.isEmpty()) return emptyList()
    val remaining = candidates.toMutableList()
    return queueItems.mapNotNull { snapshot ->
        val matchIndex = remaining.indexOfFirst { item ->
            item.matchesRestoredOccurrence(snapshot)
        }
        if (matchIndex >= 0) {
            remaining.removeAt(matchIndex)
        } else {
            candidates.firstOrNull { item -> item.matchesRestoredOccurrence(snapshot) }
        }
    }
}

internal fun restoredQueueIndex(
    snapshot: PlaybackSessionSnapshot,
    items: List<MediaItem>,
): Int {
    if (items.isEmpty()) return 0
    val savedQueueItem = snapshot.queueItems.getOrNull(snapshot.currentIndex)
    if (savedQueueItem != null) {
        val occurrenceOrdinal = snapshot.queueItems
            .take(snapshot.currentIndex + 1)
            .count { candidate -> candidate.matchesRestoredOccurrence(savedQueueItem) } - 1
        if (occurrenceOrdinal >= 0) {
            items.asSequence()
                .withIndex()
                .filter { (_, item) -> item.matchesRestoredOccurrence(savedQueueItem) }
                .drop(occurrenceOrdinal)
                .firstOrNull()
                ?.index
                ?.let { restoredIndex -> return restoredIndex }
        }
    }
    val savedIndex = snapshot.currentIndex.coerceIn(items.indices)
    val currentMediaId = snapshot.currentMediaId
    if (currentMediaId == null || items[savedIndex].mediaId == currentMediaId) {
        return savedIndex
    }
    return items.indexOfFirst { item -> item.mediaId == currentMediaId }
        .takeIf { index -> index >= 0 }
        ?: savedIndex
}

private fun PlaybackQueueSnapshotItem.matchesRestoredOccurrence(
    other: PlaybackQueueSnapshotItem,
): Boolean {
    val sameStableKey = stableKey.isNotBlank() && stableKey == other.stableKey
    return mediaId == other.mediaId || sameStableKey
}

private fun MediaItem.matchesRestoredOccurrence(
    snapshot: PlaybackQueueSnapshotItem,
): Boolean {
    val sameStableKey = snapshot.stableKey.isNotBlank() && stableKey == snapshot.stableKey
    return mediaId == snapshot.mediaId || sameStableKey
}

private fun Int.sanitizedRepeatMode(): Int {
    return when (this) {
        Player.REPEAT_MODE_OFF,
        Player.REPEAT_MODE_ONE,
        Player.REPEAT_MODE_ALL -> this
        else -> Player.REPEAT_MODE_OFF
    }
}

private fun Player.restoreQueuePaused(
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long,
) {
    pause()
    setMediaItems(mediaItems, startIndex, startPositionMs)
    prepare()
}

internal fun List<MediaItem>.filterRestorablePlaybackItems(): List<MediaItem> {
    return filter { item ->
        isRestorablePlaybackItemState(
            hasPlaybackUri = item.localConfiguration?.uri != null,
        )
    }
}

internal fun isRestorablePlaybackItemState(hasPlaybackUri: Boolean): Boolean {
    return hasPlaybackUri
}
