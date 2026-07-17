package com.smartisan.music.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private const val SearchHistoryStoreName = "search_history"
private const val MaxHistoryEntries = 10

private val Context.searchHistoryDataStore by preferencesDataStore(
    name = SearchHistoryStoreName,
)

class SearchHistoryStore(
    private val context: Context,
) {
    val history: Flow<List<String>> = context.searchHistoryDataStore.data
        .map { preferences ->
            decodeHistoryEntries(preferences[SearchHistoryEntriesKey].orEmpty())
        }

    suspend fun record(query: String) {
        val sanitizedQuery = sanitizeHistoryQuery(query) ?: return
        context.searchHistoryDataStore.edit { preferences ->
            val currentEntries = decodeHistoryEntries(preferences[SearchHistoryEntriesKey].orEmpty())
            preferences[SearchHistoryEntriesKey] = encodeHistoryEntries(
                trimHistoryEntries(listOf(sanitizedQuery) + currentEntries),
            )
        }
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { preferences ->
            preferences.remove(SearchHistoryEntriesKey)
        }
    }

    private companion object {
        val SearchHistoryEntriesKey = stringPreferencesKey("entries")
    }
}

internal fun sanitizeHistoryQuery(query: String): String? {
    return query.trim().takeIf { it.isNotEmpty() }
}

internal fun trimHistoryEntries(
    entries: List<String>,
    maxEntries: Int = MaxHistoryEntries,
): List<String> {
    val normalizedEntries = linkedMapOf<String, String>()
    entries.forEach { entry ->
        val sanitizedEntry = sanitizeHistoryQuery(entry) ?: return@forEach
        val normalizedKey = sanitizedEntry.lowercase(Locale.ROOT)
        normalizedEntries.putIfAbsent(normalizedKey, sanitizedEntry)
    }
    return normalizedEntries.values.take(maxEntries)
}

internal fun encodeHistoryEntries(entries: List<String>): String {
    return trimHistoryEntries(entries)
        .joinToString(separator = "\n") { entry ->
            URLEncoder.encode(entry, StandardCharsets.UTF_8.name())
        }
}

internal fun decodeHistoryEntries(rawValue: String): List<String> {
    if (rawValue.isBlank()) {
        return emptyList()
    }
    return trimHistoryEntries(
        rawValue
            .lineSequence()
            .map { entry -> URLDecoder.decode(entry, StandardCharsets.UTF_8.name()) }
            .toList(),
    )
}
