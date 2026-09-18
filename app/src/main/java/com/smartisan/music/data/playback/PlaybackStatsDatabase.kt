package com.smartisan.music.data.playback

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "playback_stats")
internal data class PlaybackStatsEntity(
    @PrimaryKey val mediaId: String,
    val playCount: Long,
    val score: Int,
    val updatedAt: Long,
)

internal data class PlaybackStatsRow(
    val mediaId: String,
    val playCount: Long,
    val score: Int,
)

@Entity(tableName = "playback_progress")
internal data class PlaybackProgressEntity(
    @PrimaryKey val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

internal data class PlaybackProgressRow(
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
)

@Dao
internal interface PlaybackStatsDao {

    @Query("SELECT mediaId, playCount, score FROM playback_stats")
    fun getStats(): List<PlaybackStatsRow>

    @Query("SELECT mediaId, playCount, score FROM playback_stats WHERE mediaId IN (:mediaIds)")
    fun getStats(mediaIds: List<String>): List<PlaybackStatsRow>

    @Query("SELECT playCount FROM playback_stats WHERE mediaId = :mediaId")
    suspend fun getPlayCount(mediaId: String): Long?

    @Query("SELECT score FROM playback_stats WHERE mediaId = :mediaId")
    suspend fun getScore(mediaId: String): Int?

    @Query(
        """
        UPDATE playback_stats
        SET playCount = playCount + 1,
            updatedAt = :updatedAt
        WHERE mediaId = :mediaId
        """,
    )
    suspend fun incrementExisting(mediaId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE playback_stats
        SET score = :score,
            updatedAt = :updatedAt
        WHERE mediaId = :mediaId
        """,
    )
    suspend fun updateScore(mediaId: String, score: Int, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PlaybackStatsEntity): Long

    @Query("SELECT mediaId, positionMs, durationMs FROM playback_progress WHERE mediaId = :mediaId")
    fun getProgress(mediaId: String): PlaybackProgressRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entity: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE mediaId = :mediaId")
    suspend fun deleteProgress(mediaId: String)
}

@Database(
    entities = [PlaybackStatsEntity::class, PlaybackProgressEntity::class],
    version = 3,
    exportSchema = false,
)
internal abstract class PlaybackStatsDatabase : RoomDatabase() {
    abstract fun playbackStatsDao(): PlaybackStatsDao

    companion object {
        @Volatile
        private var instance: PlaybackStatsDatabase? = null

        fun getInstance(context: Context): PlaybackStatsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaybackStatsDatabase::class.java,
                    "playback_stats.db",
                )
                    .addMigrations(Migration1To2, Migration2To3)
                    .build()
                    .also { instance = it }
            }
        }

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playback_stats ADD COLUMN score INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playback_progress (
                        mediaId TEXT NOT NULL,
                        positionMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(mediaId)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
