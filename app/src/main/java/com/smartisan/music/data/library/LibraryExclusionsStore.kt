package com.smartisan.music.data.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val LibraryExclusionsStoreName = "library_exclusions"

private val Context.libraryExclusionsDataStore by preferencesDataStore(
    name = LibraryExclusionsStoreName,
)

data class LibraryExclusions(
    val hiddenMediaIds: Set<String> = emptySet(),
    val hiddenDirectoryKeys: Set<String> = emptySet(),
) {

    fun isDirectoryHidden(directoryKey: String): Boolean {
        val normalized = directoryKey.normalizedDirectoryKey()
        if (normalized.isEmpty()) {
            return false
        }
        return hiddenDirectoryKeys.contains(normalized)
    }

    fun isMediaHidden(mediaId: String, relativePath: String?): Boolean {
        if (hiddenMediaIds.contains(mediaId)) {
            return true
        }
        val normalizedPath = relativePath?.normalizedDirectoryKey().orEmpty()
        if (normalizedPath.isEmpty()) {
            return false
        }
        return hiddenDirectoryKeys.contains(normalizedPath)
    }

    fun contentHash(): Int {
        return 31 * hiddenMediaIds.hashCode() + hiddenDirectoryKeys.hashCode()
    }
}

class LibraryExclusionsStore(
    private val context: Context,
) {

    val exclusions: Flow<LibraryExclusions> = context.libraryExclusionsDataStore.data
        .map { preferences ->
            LibraryExclusions(
                hiddenMediaIds = preferences[HiddenMediaIdsKey].orEmpty(),
                hiddenDirectoryKeys = preferences[HiddenDirectoryKeysKey]
                    .orEmpty()
                    .asSequence()
                    .map { it.normalizedDirectoryKey() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
            )
        }
        .distinctUntilChanged()

    val revision: Flow<Int> = exclusions
        .map { it.contentHash() }
        .distinctUntilChanged()

    suspend fun hideMediaIds(mediaIds: Set<String>) {
        setMediaIdsHidden(mediaIds = mediaIds, hidden = true)
    }

    suspend fun showMediaIds(mediaIds: Set<String>) {
        setMediaIdsHidden(mediaIds = mediaIds, hidden = false)
    }

    suspend fun setMediaIdsHidden(mediaIds: Set<String>, hidden: Boolean) {
        val normalized = mediaIds.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (normalized.isEmpty()) {
            return
        }
        context.libraryExclusionsDataStore.edit { preferences ->
            val current = preferences[HiddenMediaIdsKey].orEmpty().toMutableSet()
            if (hidden) {
                current.addAll(normalized)
            } else {
                current.removeAll(normalized)
            }
            preferences[HiddenMediaIdsKey] = current
        }
    }

    suspend fun hideDirectoryKeys(directoryKeys: Set<String>) {
        setDirectoryKeysHidden(directoryKeys = directoryKeys, hidden = true)
    }

    suspend fun showDirectoryKeys(directoryKeys: Set<String>) {
        setDirectoryKeysHidden(directoryKeys = directoryKeys, hidden = false)
    }

    suspend fun setDirectoryKeysHidden(directoryKeys: Set<String>, hidden: Boolean) {
        val normalized = directoryKeys.asSequence()
            .map { it.normalizedDirectoryKey() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (normalized.isEmpty()) {
            return
        }
        context.libraryExclusionsDataStore.edit { preferences ->
            val current = preferences[HiddenDirectoryKeysKey]
                .orEmpty()
                .asSequence()
                .map { it.normalizedDirectoryKey() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (hidden) {
                current.addAll(normalized)
            } else {
                current.removeAll(normalized)
            }
            preferences[HiddenDirectoryKeysKey] = current
        }
    }
}

private val HiddenMediaIdsKey = stringSetPreferencesKey("hidden_media_ids")
private val HiddenDirectoryKeysKey = stringSetPreferencesKey("hidden_directory_keys")

internal fun String.normalizedDirectoryKey(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return ""
    }
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}
