package com.smartisan.music.data.playlist

import android.content.Context
import androidx.room.withTransaction
import com.smartisan.music.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class PlaylistRepository private constructor(
    private val appContext: Context,
    private val database: PlaylistDatabase,
) {

    private val playlistDao = database.playlistDao()

    val playlists: Flow<List<UserPlaylistSummary>> = playlistDao.observePlaylistSummaries()
        .map { rows ->
            rows.map { row ->
                UserPlaylistSummary(
                    id = row.playlistId,
                    name = row.name,
                    songCount = row.songCount,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                )
            }
        }

    fun observePlaylistDetail(playlistId: String): Flow<UserPlaylistDetail?> {
        return playlistDao.observePlaylistDetail(playlistId)
            .map { record ->
                record?.let {
                    UserPlaylistDetail(
                        id = it.playlist.playlistId,
                        name = it.playlist.name,
                        mediaIds = it.entries.sortedBy(PlaylistEntryEntity::sortOrder).map(PlaylistEntryEntity::mediaId),
                        createdAt = it.playlist.createdAt,
                        updatedAt = it.playlist.updatedAt,
                    )
                }
            }
    }

    suspend fun suggestNextUntitledName(): String {
        return nextUntitledPlaylistName(
            existingNames = playlistDao.getPlaylistNames(),
            baseName = appContext.getString(R.string.playlist_default_name),
        )
    }

    suspend fun createPlaylist(
        name: String,
        initialMediaIds: List<String> = emptyList(),
    ): PlaylistCreateResult {
        val normalizedName = normalizePlaylistName(name)
        if (normalizedName.isEmpty()) {
            return PlaylistCreateResult.EmptyName
        }

        return database.withTransaction {
            if (playlistDao.hasPlaylistWithName(normalizedName)) {
                return@withTransaction PlaylistCreateResult.DuplicateName
            }

            val playlistId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val uniqueMediaIds = normalizeMediaIds(initialMediaIds)
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    playlistId = playlistId,
                    name = normalizedName,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
            if (uniqueMediaIds.isNotEmpty()) {
                playlistDao.insertPlaylistEntries(
                    uniqueMediaIds.mapIndexed { index, mediaId ->
                        PlaylistEntryEntity(
                            playlistId = playlistId,
                            mediaId = mediaId,
                            sortOrder = index,
                            addedAt = timestamp,
                        )
                    },
                )
            }
            PlaylistCreateResult.Success(
                playlistId = playlistId,
                addedCount = uniqueMediaIds.size,
            )
        }
    }

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): PlaylistRenameResult {
        val normalizedName = normalizePlaylistName(name)
        if (normalizedName.isEmpty()) {
            return PlaylistRenameResult.EmptyName
        }

        return database.withTransaction {
            val playlist = playlistDao.getPlaylist(playlistId)
                ?: return@withTransaction PlaylistRenameResult.MissingPlaylist
            if (playlist.name == normalizedName) {
                return@withTransaction PlaylistRenameResult.Success
            }
            if (playlistDao.hasPlaylistWithNameExcept(normalizedName, playlistId)) {
                return@withTransaction PlaylistRenameResult.DuplicateName
            }
            playlistDao.updatePlaylistName(
                playlistId = playlistId,
                name = normalizedName,
                updatedAt = System.currentTimeMillis(),
            )
            PlaylistRenameResult.Success
        }
    }

    suspend fun deletePlaylists(playlistIds: Set<String>) {
        if (playlistIds.isEmpty()) {
            return
        }
        playlistDao.deletePlaylists(playlistIds)
    }

    suspend fun addMediaIds(
        playlistId: String,
        mediaIds: List<String>,
    ): PlaylistAddResult {
        val uniqueMediaIds = normalizeMediaIds(mediaIds)
        if (uniqueMediaIds.isEmpty()) {
            return PlaylistAddResult(addedCount = 0, duplicateCount = 0)
        }

        return database.withTransaction {
            val playlist = playlistDao.getPlaylist(playlistId)
                ?: return@withTransaction PlaylistAddResult(0, uniqueMediaIds.size)
            val existingIds = playlistDao.getPlaylistMediaIds(playlistId).toSet()
            val newIds = uniqueMediaIds.filterNot(existingIds::contains)
            if (newIds.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val startIndex = existingIds.size
                playlistDao.insertPlaylistEntries(
                    newIds.mapIndexed { index, mediaId ->
                        PlaylistEntryEntity(
                            playlistId = playlist.playlistId,
                            mediaId = mediaId,
                            sortOrder = startIndex + index,
                            addedAt = timestamp,
                        )
                    },
                )
                playlistDao.touchPlaylist(playlist.playlistId, timestamp)
            }
            PlaylistAddResult(
                addedCount = newIds.size,
                duplicateCount = uniqueMediaIds.size - newIds.size,
            )
        }
    }

    suspend fun removeMediaIds(
        playlistId: String,
        mediaIds: Set<String>,
    ): Int {
        val normalizedMediaIds = normalizeMediaIds(mediaIds.toList()).toSet()
        if (normalizedMediaIds.isEmpty()) {
            return 0
        }

        return database.withTransaction {
            removeMediaIdsFromPlaylistLocked(playlistId, normalizedMediaIds)
        }
    }

    suspend fun removeMediaIdsFromAll(mediaIds: Set<String>): Int {
        val normalizedMediaIds = normalizeMediaIds(mediaIds.toList()).toSet()
        if (normalizedMediaIds.isEmpty()) {
            return 0
        }

        return database.withTransaction {
            playlistDao.getPlaylistIdsContainingMediaIds(normalizedMediaIds)
                .sumOf { playlistId ->
                    removeMediaIdsFromPlaylistLocked(playlistId, normalizedMediaIds)
            }
        }
    }

    suspend fun reorderVisibleMediaIds(
        playlistId: String,
        orderedVisibleMediaIds: List<String>,
    ): Boolean {
        val normalizedMediaIds = normalizeMediaIds(orderedVisibleMediaIds)
        if (normalizedMediaIds.isEmpty()) {
            return false
        }

        return database.withTransaction {
            val playlist = playlistDao.getPlaylist(playlistId) ?: return@withTransaction false
            val entries = playlistDao.getPlaylistEntries(playlistId)
            val sortedEntries = entries.sortedBy(PlaylistEntryEntity::sortOrder)
            val reorderedEntries = reorderVisiblePlaylistEntries(
                playlistId = playlist.playlistId,
                entries = sortedEntries,
                orderedVisibleMediaIds = normalizedMediaIds,
            )
            if (reorderedEntries.map(PlaylistEntryEntity::mediaId) == sortedEntries.map(PlaylistEntryEntity::mediaId)) {
                return@withTransaction false
            }

            playlistDao.clearPlaylistEntries(playlist.playlistId)
            playlistDao.insertPlaylistEntries(reorderedEntries)
            playlistDao.touchPlaylist(playlist.playlistId, System.currentTimeMillis())
            true
        }
    }

    private suspend fun removeMediaIdsFromPlaylistLocked(
        playlistId: String,
        mediaIds: Set<String>,
    ): Int {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return 0
        val entries = playlistDao.getPlaylistEntries(playlistId)
        val remainingEntries = compactPlaylistEntriesAfterRemoval(
            playlistId = playlist.playlistId,
            entries = entries,
            mediaIds = mediaIds,
        )
        val removedCount = entries.size - remainingEntries.size
        if (removedCount <= 0) {
            return 0
        }

        playlistDao.clearPlaylistEntries(playlist.playlistId)
        if (remainingEntries.isNotEmpty()) {
            playlistDao.insertPlaylistEntries(remainingEntries)
        }
        playlistDao.touchPlaylist(playlist.playlistId, System.currentTimeMillis())
        return removedCount
    }

    companion object {
        @Volatile
        private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository {
            return instance ?: synchronized(this) {
                val appContext = context.applicationContext
                instance ?: PlaylistRepository(
                    appContext,
                    PlaylistDatabase.getInstance(appContext),
                ).also { instance = it }
            }
        }
    }
}

private fun normalizeMediaIds(mediaIds: List<String>): List<String> {
    return mediaIds.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()
}

internal fun compactPlaylistEntriesAfterRemoval(
    playlistId: String,
    entries: List<PlaylistEntryEntity>,
    mediaIds: Set<String>,
): List<PlaylistEntryEntity> {
    return entries.asSequence()
        .sortedBy(PlaylistEntryEntity::sortOrder)
        .filterNot { it.mediaId in mediaIds }
        .mapIndexed { index, entry ->
            entry.copy(
                playlistId = playlistId,
                sortOrder = index,
            )
        }
        .toList()
}

internal fun reorderVisiblePlaylistEntries(
    playlistId: String,
    entries: List<PlaylistEntryEntity>,
    orderedVisibleMediaIds: List<String>,
): List<PlaylistEntryEntity> {
    val sortedEntries = entries.sortedBy(PlaylistEntryEntity::sortOrder)
    val entriesByMediaId = sortedEntries.associateBy(PlaylistEntryEntity::mediaId)
    val orderedExistingMediaIds = orderedVisibleMediaIds
        .filter(entriesByMediaId::containsKey)
        .distinct()
    if (orderedExistingMediaIds.isEmpty()) {
        return sortedEntries.mapIndexed { index, entry ->
            entry.copy(
                playlistId = playlistId,
                sortOrder = index,
            )
        }
    }

    val orderedVisibleSet = orderedExistingMediaIds.toSet()
    val replacementIterator = orderedExistingMediaIds.iterator()
    return sortedEntries.mapIndexed { index, entry ->
        val reorderedEntry = if (entry.mediaId in orderedVisibleSet && replacementIterator.hasNext()) {
            entriesByMediaId.getValue(replacementIterator.next())
        } else {
            entry
        }
        reorderedEntry.copy(
            playlistId = playlistId,
            sortOrder = index,
        )
    }
}
