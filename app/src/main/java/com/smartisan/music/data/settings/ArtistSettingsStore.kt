package com.smartisan.music.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val ArtistSettingsStoreName = "artist_settings"

private val Context.artistSettingsDataStore by preferencesDataStore(
    name = ArtistSettingsStoreName,
)

data class ArtistSettings(
    val separators: Set<String> = emptySet(),
)

class ArtistSettingsStore(
    private val context: Context,
) {

    val settings: Flow<ArtistSettings> = context.artistSettingsDataStore.data
        .map { preferences ->
            ArtistSettings(
                separators = preferences[ArtistSeparatorsKey]
                    .orEmpty()
                    .normalizedArtistSeparators(),
            )
        }
        .distinctUntilChanged()

    suspend fun setSeparators(separators: Set<String>) {
        context.artistSettingsDataStore.edit { preferences ->
            preferences[ArtistSeparatorsKey] = separators.normalizedArtistSeparators()
        }
    }
}

fun parseArtistSeparatorInput(input: String): Set<String> {
    return input.asSequence()
        .map(Char::toString)
        .toSet()
        .normalizedArtistSeparators()
}

fun Set<String>.normalizedArtistSeparators(): Set<String> {
    return asSequence()
        .mapNotNull { separator ->
            separator.trim().takeIf { it.isNotEmpty() }
        }
        .toCollection(linkedSetOf())
}

private val ArtistSeparatorsKey = stringSetPreferencesKey("artist_separators")
