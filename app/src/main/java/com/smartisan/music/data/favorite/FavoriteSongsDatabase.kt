package com.smartisan.music.data.favorite

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val mediaId: String,
    val likedAt: Long,
)

@Dao
interface FavoriteSongDao {

    @Query("SELECT * FROM favorite_songs ORDER BY likedAt DESC")
    fun observeAll(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT mediaId FROM favorite_songs")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteSongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllMissing(entities: List<FavoriteSongEntity>)

    @Query("DELETE FROM favorite_songs WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: String)

    @Query("DELETE FROM favorite_songs WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByIds(mediaIds: Set<String>)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE mediaId = :mediaId)")
    suspend fun exists(mediaId: String): Boolean
}

@Database(
    entities = [FavoriteSongEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FavoriteSongsDatabase : RoomDatabase() {
    abstract fun favoriteSongDao(): FavoriteSongDao

    companion object {
        @Volatile
        private var instance: FavoriteSongsDatabase? = null

        fun getInstance(context: Context): FavoriteSongsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FavoriteSongsDatabase::class.java,
                    "favorite_songs.db",
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
