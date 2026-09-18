package com.smartisan.music.data.playback

import android.content.Context
import androidx.room.withTransaction

data class PlaybackStatsRecord(
    val playCount: Long,
    val score: Int,
)

data class PlaybackProgressRecord(
    val positionMs: Long,
    val durationMs: Long,
)

class PlaybackStatsRepository private constructor(
    private val database: PlaybackStatsDatabase,
) {

    private val playbackStatsDao = database.playbackStatsDao()

    fun getStats(): Map<String, PlaybackStatsRecord> {
        return playbackStatsDao.getStats()
            .toStatsMap()
    }

    fun getStats(mediaIds: Set<String>): Map<String, PlaybackStatsRecord> {
        if (mediaIds.isEmpty()) {
            return emptyMap()
        }
        return mediaIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .chunked(PlaybackStatsQueryChunkSize)
            .flatMap { chunk -> playbackStatsDao.getStats(chunk) }
            .toList()
            .toStatsMap()
    }

    suspend fun incrementPlayCount(
        mediaId: String,
        updatedAt: Long = System.currentTimeMillis(),
    ): Long? {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return null
        }
        return database.withTransaction {
            if (playbackStatsDao.incrementExisting(normalizedMediaId, updatedAt) == 0) {
                val inserted = playbackStatsDao.insert(
                    PlaybackStatsEntity(
                        mediaId = normalizedMediaId,
                        playCount = 1L,
                        score = 0,
                        updatedAt = updatedAt,
                    ),
                )
                if (inserted == -1L) {
                    playbackStatsDao.incrementExisting(normalizedMediaId, updatedAt)
                }
            }
            playbackStatsDao.getPlayCount(normalizedMediaId)
        }
    }

    suspend fun setScore(
        mediaId: String,
        score: Int,
        updatedAt: Long = System.currentTimeMillis(),
    ): Int? {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return null
        }
        val normalizedScore = score.coerceIn(MinScore, MaxScore)
        return database.withTransaction {
            if (playbackStatsDao.updateScore(normalizedMediaId, normalizedScore, updatedAt) == 0) {
                val inserted = playbackStatsDao.insert(
                    PlaybackStatsEntity(
                        mediaId = normalizedMediaId,
                        playCount = 0L,
                        score = normalizedScore,
                        updatedAt = updatedAt,
                    ),
                )
                if (inserted == -1L) {
                    playbackStatsDao.updateScore(normalizedMediaId, normalizedScore, updatedAt)
                }
            }
            playbackStatsDao.getScore(normalizedMediaId)
        }
    }

    fun getProgress(mediaId: String): PlaybackProgressRecord? {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return null
        }
        return playbackStatsDao.getProgress(normalizedMediaId)?.let { row ->
            PlaybackProgressRecord(
                positionMs = row.positionMs.coerceAtLeast(0L),
                durationMs = row.durationMs.coerceAtLeast(0L),
            )
        }
    }

    suspend fun setProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): Boolean {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return false
        }
        playbackStatsDao.upsertProgress(
            PlaybackProgressEntity(
                mediaId = normalizedMediaId,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAt = updatedAt,
            ),
        )
        return true
    }

    suspend fun clearProgress(mediaId: String) {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return
        }
        playbackStatsDao.deleteProgress(normalizedMediaId)
    }

    companion object {
        @Volatile
        private var instance: PlaybackStatsRepository? = null

        fun getInstance(context: Context): PlaybackStatsRepository {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsRepository(
                    PlaybackStatsDatabase.getInstance(context.applicationContext),
                ).also { instance = it }
            }
        }

        internal fun create(database: PlaybackStatsDatabase): PlaybackStatsRepository {
            return PlaybackStatsRepository(database)
        }

        private const val MinScore = 0
        private const val MaxScore = 5
        private const val PlaybackStatsQueryChunkSize = 500
    }
}

private fun List<PlaybackStatsRow>.toStatsMap(): Map<String, PlaybackStatsRecord> {
    return associate { row ->
        row.mediaId to PlaybackStatsRecord(
            playCount = row.playCount,
            score = row.score,
        )
    }
}
