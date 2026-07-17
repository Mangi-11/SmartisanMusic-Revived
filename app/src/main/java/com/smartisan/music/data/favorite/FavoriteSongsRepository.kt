package com.smartisan.music.data.favorite

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class FavoriteSongRecord(
    val mediaId: String,
    val likedAt: Long,
)

class FavoriteSongsRepository private constructor(
    private val database: FavoriteSongsDatabase,
) {

    private val favoriteSongDao = database.favoriteSongDao()

    fun observeFavorites(): Flow<List<FavoriteSongRecord>> {
        return favoriteSongDao.observeAll()
            .map { rows ->
                rows.map { row ->
                    FavoriteSongRecord(
                        mediaId = row.mediaId,
                        likedAt = row.likedAt,
                    )
                }
            }
    }

    fun observeFavoriteIds(): Flow<Set<String>> {
        return favoriteSongDao.observeIds()
            .map(List<String>::toSet)
            .distinctUntilChanged()
    }

    suspend fun add(mediaId: String, likedAt: Long = System.currentTimeMillis()) {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return
        }
        favoriteSongDao.upsert(
            FavoriteSongEntity(
                mediaId = normalizedMediaId,
                likedAt = likedAt,
            ),
        )
    }

    suspend fun addMissing(mediaIds: Set<String>, likedAt: Long = System.currentTimeMillis()) {
        val normalizedMediaIds = mediaIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        if (normalizedMediaIds.isEmpty()) {
            return
        }
        favoriteSongDao.insertAllMissing(
            normalizedMediaIds.map { mediaId ->
                FavoriteSongEntity(
                    mediaId = mediaId,
                    likedAt = likedAt,
                )
            },
        )
    }

    suspend fun remove(mediaId: String) {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return
        }
        favoriteSongDao.deleteById(normalizedMediaId)
    }

    suspend fun removeAll(mediaIds: Set<String>) {
        val normalizedMediaIds = mediaIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        if (normalizedMediaIds.isEmpty()) {
            return
        }
        favoriteSongDao.deleteByIds(normalizedMediaIds)
    }

    suspend fun toggle(mediaId: String, likedAt: Long = System.currentTimeMillis()): Boolean {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return false
        }
        return if (favoriteSongDao.exists(normalizedMediaId)) {
            favoriteSongDao.deleteById(normalizedMediaId)
            false
        } else {
            favoriteSongDao.upsert(
                FavoriteSongEntity(
                    mediaId = normalizedMediaId,
                    likedAt = likedAt,
                ),
            )
            true
        }
    }

    suspend fun exists(mediaId: String): Boolean {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isEmpty()) {
            return false
        }
        return favoriteSongDao.exists(normalizedMediaId)
    }

    companion object {
        @Volatile
        private var instance: FavoriteSongsRepository? = null

        fun getInstance(context: Context): FavoriteSongsRepository {
            return instance ?: synchronized(this) {
                instance ?: FavoriteSongsRepository(
                    FavoriteSongsDatabase.getInstance(context.applicationContext),
                ).also { instance = it }
            }
        }

        fun create(database: FavoriteSongsDatabase): FavoriteSongsRepository {
            return FavoriteSongsRepository(database)
        }
    }
}
