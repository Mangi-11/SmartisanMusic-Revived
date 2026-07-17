package com.smartisan.music.data.favorite

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteSongsRepositoryInstrumentedTest {

    private lateinit var database: FavoriteSongsDatabase
    private lateinit var repository: FavoriteSongsRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            FavoriteSongsDatabase::class.java,
        ).build()
        repository = FavoriteSongsRepository.create(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addAndRemoveUpdatesFavoriteIdsFlow() = runBlocking {
        repository.add(mediaId = "track-1", likedAt = 10L)

        assertEquals(setOf("track-1"), repository.observeFavoriteIds().first())

        repository.remove("track-1")

        assertEquals(emptySet<String>(), repository.observeFavoriteIds().first())
    }

    @Test
    fun observeFavoritesReturnsLatestFirst() = runBlocking {
        repository.add(mediaId = "older", likedAt = 10L)
        repository.add(mediaId = "newer", likedAt = 30L)

        val favorites = repository.observeFavorites().first()

        assertEquals(listOf("newer", "older"), favorites.map { it.mediaId })
        assertEquals(listOf(30L, 10L), favorites.map { it.likedAt })
    }

    @Test
    fun toggleAddsThenRemovesFavorite() = runBlocking {
        val added = repository.toggle(mediaId = "track-1", likedAt = 10L)
        val removed = repository.toggle(mediaId = "track-1", likedAt = 20L)

        assertTrue(added)
        assertFalse(removed)
        assertFalse(repository.exists("track-1"))
    }
}
