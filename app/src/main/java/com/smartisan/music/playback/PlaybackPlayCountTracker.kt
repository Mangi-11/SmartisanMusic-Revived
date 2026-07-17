package com.smartisan.music.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.data.playback.PlaybackStatsRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.max

internal class PlaybackPlayCountTracker(
    private val player: Player,
    private val repository: PlaybackStatsRepository,
    private val scope: CoroutineScope,
    private val onPlayCountChanged: () -> Unit,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {

    private var activePlayback: ActivePlayback? = null
    private var lastClockMs: Long? = null
    private var wasPlaying = false
    private var started = false
    private val pendingCountJobs = Collections.synchronizedSet(mutableSetOf<Job>())

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncPlaybackClock()
            finishActivePlayback()
            beginActivePlayback(mediaItem)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED)
            ) {
                syncPlaybackClock()
                alignActivePlayback(player.currentMediaItem)
                if (player.playbackState == Player.STATE_ENDED) {
                    finishActivePlayback()
                } else {
                    countActivePlaybackIfEligible()
                }
            }
        }
    }

    fun start() {
        if (started) {
            return
        }
        started = true
        beginActivePlayback(player.currentMediaItem)
        wasPlaying = player.isPlaying
        lastClockMs = elapsedRealtime().takeIf { wasPlaying }
        player.addListener(listener)
    }

    suspend fun stopAndFlush() {
        syncPlaybackClock()
        finishActivePlayback(notifyAfterWrite = false)
        player.removeListener(listener)
        pendingCountJobs.snapshot().joinAll()
        started = false
        lastClockMs = null
        wasPlaying = false
    }

    private fun syncPlaybackClock() {
        val now = elapsedRealtime()
        if (wasPlaying) {
            val last = lastClockMs
            if (last != null) {
                activePlayback?.let { playback ->
                    playback.activePlaybackMs += (now - last).coerceAtLeast(0L)
                }
            }
        }
        wasPlaying = player.isPlaying
        lastClockMs = now.takeIf { wasPlaying }
    }

    private fun alignActivePlayback(mediaItem: MediaItem?) {
        val mediaId = mediaItem?.countableMediaId()
        if (activePlayback?.mediaId == mediaId) {
            return
        }
        finishActivePlayback()
        beginActivePlayback(mediaItem)
    }

    private fun beginActivePlayback(mediaItem: MediaItem?) {
        activePlayback = mediaItem?.countableMediaId()?.let { mediaId ->
            ActivePlayback(
                mediaId = mediaId,
                durationMs = mediaItem.playbackDurationMs(),
            )
        }
    }

    private fun finishActivePlayback(notifyAfterWrite: Boolean = true) {
        countActivePlaybackIfEligible(notifyAfterWrite)
        activePlayback = null
    }

    private fun countActivePlaybackIfEligible(notifyAfterWrite: Boolean = true) {
        val playback = activePlayback ?: return
        if (playback.counted || playback.activePlaybackMs < playback.thresholdMs) {
            return
        }
        playback.counted = true
        val countJob = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            repository.incrementPlayCount(playback.mediaId) ?: return@launch
            if (notifyAfterWrite) {
                scope.launch(Dispatchers.Main.immediate) {
                    onPlayCountChanged()
                }
            }
        }
        pendingCountJobs += countJob
        countJob.invokeOnCompletion {
            pendingCountJobs -= countJob
        }
        countJob.start()
    }

    private data class ActivePlayback(
        val mediaId: String,
        val durationMs: Long,
        var activePlaybackMs: Long = 0L,
        var counted: Boolean = false,
    ) {
        val thresholdMs: Long = durationMs
            .takeIf { it > 0L }
            ?.let { duration ->
                minOf(PlaybackCountThresholdMs, max(PlaybackCountMinimumThresholdMs, duration / 2L))
            }
            ?: PlaybackCountThresholdMs
    }

    private companion object {
        private const val PlaybackCountThresholdMs = 30_000L
        private const val PlaybackCountMinimumThresholdMs = 1_000L
    }
}

private fun Set<Job>.snapshot(): List<Job> {
    return synchronized(this) {
        toList()
    }
}

private fun MediaItem.countableMediaId(): String? {
    val normalizedMediaId = mediaId.trim()
    return normalizedMediaId
        .takeIf { it.isNotEmpty() && it.toLongOrNull() != null }
}

private fun MediaItem.playbackDurationMs(): Long {
    val metadataDurationMs = mediaMetadata.durationMs ?: C.TIME_UNSET
    return metadataDurationMs.takeIf { it > 0L } ?: C.TIME_UNSET
}
