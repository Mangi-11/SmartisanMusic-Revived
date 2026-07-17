package com.smartisan.music.data.playlist

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_playlists")
internal data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "user_playlist_entries",
    primaryKeys = ["playlistId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index(value = ["playlistId", "sortOrder"], unique = true),
    ],
)
internal data class PlaylistEntryEntity(
    val playlistId: String,
    val mediaId: String,
    val sortOrder: Int,
    val addedAt: Long,
)

internal data class PlaylistSummaryRow(
    val playlistId: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
)

internal data class PlaylistDetailRecord(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "playlistId",
        entityColumn = "playlistId",
        entity = PlaylistEntryEntity::class,
    )
    val entries: List<PlaylistEntryEntity>,
)

@Dao
internal interface PlaylistDao {

    @Query(
        """
        SELECT p.playlistId, p.name, p.createdAt, p.updatedAt, COUNT(e.mediaId) AS songCount
        FROM user_playlists AS p
        LEFT JOIN user_playlist_entries AS e ON e.playlistId = p.playlistId
        GROUP BY p.playlistId
        ORDER BY p.createdAt ASC
        """,
    )
    fun observePlaylistSummaries(): Flow<List<PlaylistSummaryRow>>

    @Transaction
    @Query("SELECT * FROM user_playlists WHERE playlistId = :playlistId LIMIT 1")
    fun observePlaylistDetail(playlistId: String): Flow<PlaylistDetailRecord?>

    @Query("SELECT name FROM user_playlists ORDER BY createdAt ASC")
    suspend fun getPlaylistNames(): List<String>

    @Query("SELECT * FROM user_playlists WHERE playlistId = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM user_playlists WHERE name = :name)")
    suspend fun hasPlaylistWithName(name: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM user_playlists
            WHERE name = :name AND playlistId != :playlistId
        )
        """,
    )
    suspend fun hasPlaylistWithNameExcept(name: String, playlistId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(entity: PlaylistEntity)

    @Query(
        """
        UPDATE user_playlists
        SET name = :name, updatedAt = :updatedAt
        WHERE playlistId = :playlistId
        """,
    )
    suspend fun updatePlaylistName(
        playlistId: String,
        name: String,
        updatedAt: Long,
    )

    @Query("UPDATE user_playlists SET updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun touchPlaylist(
        playlistId: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM user_playlists WHERE playlistId IN (:playlistIds)")
    suspend fun deletePlaylists(playlistIds: Set<String>)

    @Query(
        """
        SELECT mediaId FROM user_playlist_entries
        WHERE playlistId = :playlistId
        ORDER BY sortOrder ASC
        """,
    )
    suspend fun getPlaylistMediaIds(playlistId: String): List<String>

    @Query(
        """
        SELECT * FROM user_playlist_entries
        WHERE playlistId = :playlistId
        ORDER BY sortOrder ASC
        """,
    )
    suspend fun getPlaylistEntries(playlistId: String): List<PlaylistEntryEntity>

    @Query(
        """
        SELECT DISTINCT playlistId FROM user_playlist_entries
        WHERE mediaId IN (:mediaIds)
        """,
    )
    suspend fun getPlaylistIdsContainingMediaIds(mediaIds: Set<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylistEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM user_playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearPlaylistEntries(playlistId: String)
}

@Database(
    entities = [PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class PlaylistDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var instance: PlaylistDatabase? = null

        fun getInstance(context: Context): PlaylistDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "user_playlists.db",
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
