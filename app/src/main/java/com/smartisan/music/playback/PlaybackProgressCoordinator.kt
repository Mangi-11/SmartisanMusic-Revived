package com.smartisan.music.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.data.playback.PlaybackStatsRepository
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remembers the playback position of every M4B file and restores it when that file is opened
 * again, independently of the current playback queue.
 */
internal class PlaybackProgressCoordinator(
    private val player: Player,
    private val repository: PlaybackStatsRepository,
    private val scope: CoroutineScope,
) {

    private var started = false
    private var periodicSaveJob: Job? = null
    private var saveJob: Job? = null
    private var restoreJob: Job? = null
    private var lastRestoredMediaId: String? = null
    private val pendingSaveJobs = Collections.synchronizedSet(mutableSetOf<Job>())

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // A playlist change re-selects the same book (for example when it is tapped again in the
            // library), so allow that file to resume even though it was restored earlier.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                lastRestoredMediaId = null
            }
            scheduleRestore(mediaItem)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val previousItem = oldPosition.mediaItem
            if (previousItem != null && previousItem.mediaId != newPosition.mediaItem?.mediaId) {
                persist(
                    item = previousItem,
                    positionMs = oldPosition.positionMs,
                    fallbackDurationMs = C.TIME_UNSET,
                )
            }
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                scheduleCurrentSave(delayMillis = SeekSaveDebounceMs)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                scheduleCurrentSave()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> scheduleRestore(player.currentMediaItem)
                Player.STATE_ENDED -> scheduleCurrentSave(forceCompleted = true)
                else -> Unit
            }
        }
    }

    fun start() {
        if (started) {
            return
        }
        started = true
        player.addListener(listener)
        periodicSaveJob = scope.launch {
            while (isActive) {
                delay(ProgressSaveIntervalMs)
                if (player.isPlaying) {
                    scheduleCurrentSave()
                }
            }
        }
        scheduleRestore(player.currentMediaItem)
    }

    suspend fun stopAndFlush() {
        if (!started) {
            return
        }
        started = false
        player.removeListener(listener)
        periodicSaveJob?.cancel()
        periodicSaveJob = null
        saveJob?.cancel()
        saveJob = null
        restoreJob?.cancel()
        restoreJob = null
        flushCurrentPosition()
        pendingSaveJobs.snapshot().joinAll()
    }

    private fun scheduleCurrentSave(
        delayMillis: Long = 0L,
        forceCompleted: Boolean = false,
    ) {
        saveJob?.cancel()
        saveJob = scope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            val mediaItem = player.currentMediaItem ?: return@launch
            persist(
                item = mediaItem,
                positionMs = player.currentPosition,
                fallbackDurationMs = player.duration,
                forceCompleted = forceCompleted,
            )
        }
    }

    private fun scheduleRestore(mediaItem: MediaItem?) {
        if (!started) {
            return
        }
        if (mediaItem?.isM4bAudiobook() != true) {
            restoreJob?.cancel()
            restoreJob = null
            lastRestoredMediaId = null
            return
        }
        val mediaId = mediaItem.mediaId.trim()
        if (mediaId.isEmpty() || mediaId == lastRestoredMediaId) {
            return
        }
        lastRestoredMediaId = mediaId
        restoreJob?.cancel()
        restoreJob = scope.launch {
            val savedProgress = withContext(Dispatchers.IO) {
                repository.getProgress(mediaId)
            } ?: return@launch
            val currentItem = player.currentMediaItem ?: return@launch
            if (currentItem.mediaId.trim() != mediaId) {
                return@launch
            }
            val targetPositionMs = resolveAudiobookResumePositionMs(
                savedPositionMs = savedProgress.positionMs,
                savedDurationMs = savedProgress.durationMs,
                currentDurationMs = player.duration,
                currentPositionMs = player.currentPosition,
            ) ?: return@launch
            player.seekTo(targetPositionMs)
        }
    }

    private fun persist(
        item: MediaItem,
        positionMs: Long,
        fallbackDurationMs: Long,
        forceCompleted: Boolean = false,
    ) {
        val mediaId = item.mediaId.trim()
        if (mediaId.isEmpty() || !item.isM4bAudiobook()) {
            return
        }
        val durationMs = item.mediaMetadata.durationMs
            ?.takeIf { it > 0L }
            ?: fallbackDurationMs.takeIf { it > 0L }
            ?: 0L
        val isCompleted = forceCompleted || isAudiobookCompletion(positionMs, durationMs)
        val storedPositionMs = if (isCompleted) 0L else positionMs.coerceAtLeast(0L)
        val job = scope.launch(Dispatchers.IO) {
            repository.setProgress(
                mediaId = mediaId,
                positionMs = storedPositionMs,
                durationMs = durationMs,
            )
        }
        trackSave(job)
    }

    private suspend fun flushCurrentPosition() {
        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId.trim()
        if (mediaId.isEmpty() || !mediaItem.isM4bAudiobook()) {
            return
        }
        val positionMs = player.currentPosition
        val fallbackDurationMs = player.duration
        val durationMs = mediaItem.mediaMetadata.durationMs
            ?.takeIf { it > 0L }
            ?: fallbackDurationMs.takeIf { it > 0L }
            ?: 0L
        val storedPositionMs =
            if (isAudiobookCompletion(positionMs, durationMs)) 0L else positionMs.coerceAtLeast(0L)
        withContext(Dispatchers.IO) {
            repository.setProgress(
                mediaId = mediaId,
                positionMs = storedPositionMs,
                durationMs = durationMs,
            )
        }
    }

    private fun trackSave(job: Job) {
        pendingSaveJobs += job
        job.invokeOnCompletion {
            pendingSaveJobs -= job
        }
    }

    private companion object {
        private const val ProgressSaveIntervalMs = 5_000L
        private const val SeekSaveDebounceMs = 1_000L
    }
}

/**
 * Returns the position that should be restored for an audiobook, or null when playback should stay
 * where it currently is (fresh book, finished book, or the queue restore already positioned it).
 */
internal fun resolveAudiobookResumePositionMs(
    savedPositionMs: Long,
    savedDurationMs: Long,
    currentDurationMs: Long,
    currentPositionMs: Long,
): Long? {
    if (savedPositionMs <= MinAudiobookResumePositionMs) {
        return null
    }
    if (currentPositionMs > AudiobookRestorePositionToleranceMs) {
        return null
    }
    val durationMs = currentDurationMs.takeIf { it > 0L } ?: savedDurationMs
    if (isAudiobookCompletion(savedPositionMs, durationMs)) {
        return null
    }
    return savedPositionMs
}

internal fun isAudiobookCompletion(positionMs: Long, durationMs: Long): Boolean {
    if (durationMs <= 0L) {
        return false
    }
    return positionMs >= durationMs - AudiobookCompletionToleranceMs
}

private const val MinAudiobookResumePositionMs = 1_000L
private const val AudiobookRestorePositionToleranceMs = 2_000L
private const val AudiobookCompletionToleranceMs = 15_000L

private fun Set<Job>.snapshot(): List<Job> {
    return synchronized(this) {
        toList()
    }
}
