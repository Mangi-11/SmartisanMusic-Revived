@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisan.music.playback

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.Chapter
import androidx.media3.inspector.MetadataRetriever
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * A chapter parsed from an audiobook container by Media3 (QuickTime chapters are preferred over
 * Nero chapters when both are present).
 */
internal data class PlaybackChapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
) {
    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0L)
}

internal fun Tracks.playbackChapters(): List<PlaybackChapter> {
    val chapters = mutableListOf<PlaybackChapter>()
    for (group in groups) {
        for (trackIndex in 0 until group.length) {
            group.getTrackFormat(trackIndex).metadata?.collectPlaybackChapters(chapters)
        }
    }
    return chapters.normalizedPlaybackChapters()
}

private fun TrackGroupArray.collectPlaybackChapters(chapters: MutableList<PlaybackChapter>) {
    for (groupIndex in 0 until length) {
        val group = get(groupIndex)
        for (trackIndex in 0 until group.length) {
            group.getFormat(trackIndex).metadata?.collectPlaybackChapters(chapters)
        }
    }
}

private fun Metadata.collectPlaybackChapters(chapters: MutableList<PlaybackChapter>) {
    for (index in 0 until length()) {
        val entry = get(index)
        if (entry is Chapter) {
            chapters += entry.toPlaybackChapter()
        }
    }
}

private fun Chapter.toPlaybackChapter(): PlaybackChapter {
    return PlaybackChapter(
        title = title?.value?.trim().orEmpty(),
        startTimeMs = startTimeMs.coerceAtLeast(0L),
        endTimeMs = endTimeMs.coerceAtLeast(0L),
    )
}

/**
 * Chapters can be attached to more than one track format, so duplicates are collapsed and the
 * result is ordered by start time.
 */
internal fun List<PlaybackChapter>.normalizedPlaybackChapters(): List<PlaybackChapter> {
    if (isEmpty()) {
        return emptyList()
    }
    return sortedBy(PlaybackChapter::startTimeMs)
        .distinctBy(PlaybackChapter::startTimeMs)
}

/**
 * Nero (`chpl`) chapters only carry a start time, so their end time arrives unset. Fill the gap
 * with the next chapter's start, and the last chapter with the whole file duration, so the list
 * can show real chapter lengths for both Nero and QuickTime chapters.
 */
internal fun List<PlaybackChapter>.withResolvedEndTimes(
    totalDurationMs: Long,
): List<PlaybackChapter> {
    if (isEmpty()) {
        return this
    }
    return mapIndexed { index, chapter ->
        val resolvedEndTimeMs =
            when {
                chapter.endTimeMs > chapter.startTimeMs -> chapter.endTimeMs
                index < lastIndex -> this[index + 1].startTimeMs
                totalDurationMs > chapter.startTimeMs -> totalDurationMs
                else -> chapter.endTimeMs
            }
        if (resolvedEndTimeMs == chapter.endTimeMs) {
            chapter
        } else {
            chapter.copy(endTimeMs = resolvedEndTimeMs)
        }
    }
}

internal suspend fun loadPlaybackChapters(
    context: Context,
    mediaItem: MediaItem,
): List<PlaybackChapter> {
    mediaItem.localConfiguration?.uri ?: return emptyList()

    return try {
        MetadataRetriever.Builder(context, mediaItem).build().use { retriever ->
            val chapters = mutableListOf<PlaybackChapter>()
            retriever.retrieveTrackGroups().await(context).collectPlaybackChapters(chapters)
            chapters.normalizedPlaybackChapters()
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        emptyList()
    }
}

internal fun playbackChapterIndexAt(
    chapters: List<PlaybackChapter>,
    positionMs: Long,
): Int {
    if (chapters.isEmpty()) {
        return -1
    }
    for (index in chapters.indices.reversed()) {
        if (positionMs >= chapters[index].startTimeMs) {
            return index
        }
    }
    return 0
}

/** Start of the chapter after the one containing [positionMs], or null if it is the last one. */
internal fun nextPlaybackChapterStartMs(
    chapters: List<PlaybackChapter>,
    positionMs: Long,
): Long? {
    val currentIndex = playbackChapterIndexAt(chapters, positionMs)
    if (currentIndex < 0) {
        return null
    }
    return chapters.getOrNull(currentIndex + 1)?.startTimeMs
}

/**
 * Start of the current chapter when playback already advanced into it, otherwise the start of the
 * previous chapter. Returns null when playback is at the very first chapter and should fall back to
 * the previous queue item.
 */
internal fun previousPlaybackChapterStartMs(
    chapters: List<PlaybackChapter>,
    positionMs: Long,
    restartThresholdMs: Long = PlaybackChapterRestartThresholdMs,
): Long? {
    val currentIndex = playbackChapterIndexAt(chapters, positionMs)
    if (currentIndex < 0) {
        return null
    }
    val currentChapter = chapters[currentIndex]
    if (positionMs - currentChapter.startTimeMs > restartThresholdMs) {
        return currentChapter.startTimeMs
    }
    return chapters.getOrNull(currentIndex - 1)?.startTimeMs
}

internal fun isM4bFileName(displayName: String?): Boolean {
    return displayName?.trim()?.endsWith(".m4b", ignoreCase = true) == true
}

internal fun MediaItem.isM4bAudiobook(): Boolean {
    if (mediaMetadata.extras?.getBoolean(LocalAudioLibrary.AudiobookExtraKey) == true) {
        return true
    }
    val path = localConfiguration?.uri?.path?.lowercase(Locale.ROOT) ?: return false
    return path.endsWith(".m4b")
}

internal object NowPlayingChaptersRepository {
    private val cache = object : LruCache<ChaptersRequestKey, List<PlaybackChapter>>(
        MaxCachedChapterEntries
    ) {
        override fun sizeOf(key: ChaptersRequestKey, value: List<PlaybackChapter>): Int {
            return value.size.coerceAtLeast(1)
        }
    }
    private val missingKeys = LruCache<ChaptersRequestKey, Long>(MissingChaptersCacheSize)
    private val inFlightLoads =
        ConcurrentHashMap<ChaptersRequestKey, Deferred<List<PlaybackChapter>>>()
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun peek(mediaItem: MediaItem): List<PlaybackChapter>? {
        return cache.get(mediaItem.chaptersRequestKey())
    }

    suspend fun load(
        context: Context,
        mediaItem: MediaItem,
        rememberMissing: Boolean = true,
    ): List<PlaybackChapter> {
        val appContext = context.applicationContext
        val key = mediaItem.chaptersRequestKey()
        cache.get(key)?.let { return it }
        if (isRecentlyMissing(key)) {
            return emptyList()
        }

        val chapters = loadCoalesced(key) {
            loadPlaybackChapters(appContext, mediaItem)
        }
        if (chapters.isNotEmpty()) {
            cache.put(key, chapters)
            missingKeys.remove(key)
        } else if (rememberMissing) {
            missingKeys.put(key, SystemClock.elapsedRealtime())
        }
        return chapters
    }

    private fun isRecentlyMissing(key: ChaptersRequestKey): Boolean {
        val missingAtMs = missingKeys.get(key) ?: return false
        return SystemClock.elapsedRealtime() - missingAtMs < MissingChaptersCooldownMs
    }

    private suspend fun loadCoalesced(
        key: ChaptersRequestKey,
        loader: suspend () -> List<PlaybackChapter>,
    ): List<PlaybackChapter> {
        val newLoad = loadScope.async(start = CoroutineStart.LAZY) {
            cache.get(key) ?: loader()
        }
        val activeLoad = inFlightLoads.putIfAbsent(key, newLoad)
        val load = activeLoad ?: newLoad.also { pendingLoad ->
            pendingLoad.invokeOnCompletion {
                inFlightLoads.remove(key, pendingLoad)
            }
            pendingLoad.start()
        }
        if (activeLoad != null) {
            newLoad.cancel()
        }
        return load.await()
    }
}

private data class ChaptersRequestKey(
    val mediaId: String?,
    val mediaUri: String?,
)

private fun MediaItem.chaptersRequestKey(): ChaptersRequestKey {
    return ChaptersRequestKey(
        mediaId = mediaId.trim().takeIf(String::isNotEmpty),
        mediaUri = localConfiguration?.uri?.toString(),
    )
}

internal const val PlaybackChapterRestartThresholdMs = 3_000L

private const val MaxCachedChapterEntries = 2_000
private const val MissingChaptersCacheSize = 128
private const val MissingChaptersCooldownMs = 5 * 60 * 1000L
