package com.smartisan.music.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartisan.music.ui.album.AlbumViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val LibraryDisplaySettingsStoreName = "library_display_settings"

private val Context.libraryDisplaySettingsDataStore by preferencesDataStore(
    name = LibraryDisplaySettingsStoreName,
)

data class LibraryDisplaySettings(
    val albumViewMode: AlbumViewMode = AlbumViewMode.List,
    val artistAlbumViewMode: AlbumViewMode = AlbumViewMode.List,
)

class LibraryDisplaySettingsStore(
    private val context: Context,
) {

    val settings: Flow<LibraryDisplaySettings> = context.libraryDisplaySettingsDataStore.data
        .map { preferences ->
            LibraryDisplaySettings(
                albumViewMode = preferences[AlbumViewModeKey].toAlbumViewMode(),
                artistAlbumViewMode = preferences[ArtistAlbumViewModeKey].toAlbumViewMode(),
            )
        }
        .distinctUntilChanged()

    suspend fun setAlbumViewMode(mode: AlbumViewMode) {
        setAlbumViewMode(AlbumViewModeKey, mode)
    }

    suspend fun setArtistAlbumViewMode(mode: AlbumViewMode) {
        setAlbumViewMode(ArtistAlbumViewModeKey, mode)
    }

    private suspend fun setAlbumViewMode(
        key: Preferences.Key<String>,
        mode: AlbumViewMode,
    ) {
        context.libraryDisplaySettingsDataStore.edit { preferences ->
            preferences[key] = mode.name
        }
    }
}

private fun String?.toAlbumViewMode(): AlbumViewMode {
    return this?.let { value ->
        runCatching { AlbumViewMode.valueOf(value) }.getOrNull()
    } ?: AlbumViewMode.List
}

private val AlbumViewModeKey = stringPreferencesKey("album_view_mode")
private val ArtistAlbumViewModeKey = stringPreferencesKey("artist_album_view_mode")
