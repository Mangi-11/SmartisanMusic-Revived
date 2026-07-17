package com.smartisan.music.ui.folder

import androidx.media3.common.MediaItem
import com.smartisan.music.data.library.LibraryExclusions
import com.smartisan.music.data.library.normalizedDirectoryKey
import com.smartisan.music.playback.LocalAudioLibrary
import java.text.Collator
import java.util.Locale

internal data class DirectoryEntry(
    val key: String,
    val name: String,
    val displayPath: String,
    val totalCount: Int,
    val visibleCount: Int,
    val hidden: Boolean,
)

internal fun buildDirectoryEntries(
    mediaItems: List<MediaItem>,
    exclusions: LibraryExclusions,
    storageLabel: String,
): List<DirectoryEntry> {
    val counts = linkedMapOf<String, MutableDirectoryCount>()

    mediaItems.forEach { item ->
        val relativePath = item.mediaMetadata.extras
            ?.getString(LocalAudioLibrary.RelativePathExtraKey)
            ?.normalizedDirectoryKey()
            .orEmpty()
        if (relativePath.isEmpty()) {
            return@forEach
        }

        val count = counts.getOrPut(relativePath) { MutableDirectoryCount() }
        count.total++
        if (!exclusions.isMediaHidden(item.mediaId, relativePath)) {
            count.visible++
        }
    }

    return counts.entries
        .map { (key, count) ->
            val trimmedKey = key.trimEnd('/')
            val name = trimmedKey.substringAfterLast('/', trimmedKey)
            DirectoryEntry(
                key = key,
                name = name,
                displayPath = "/$storageLabel/$trimmedKey",
                totalCount = count.total,
                visibleCount = count.visible,
                hidden = exclusions.isDirectoryHidden(key),
            )
        }
        .sortedWith(directoryNameComparator())
}

internal fun filterDirectoryEntriesForDisplay(
    entries: List<DirectoryEntry>,
    editMode: Boolean,
): List<DirectoryEntry> {
    if (editMode) {
        return entries.filter { it.totalCount > 0 }
    }
    return entries.filter { entry ->
        !entry.hidden && entry.visibleCount > 0
    }
}

internal fun filterMediaItemsForDirectory(
    mediaItems: List<MediaItem>,
    directoryKey: String,
    exclusions: LibraryExclusions,
): List<MediaItem> {
    val normalizedKey = directoryKey.normalizedDirectoryKey()
    if (normalizedKey.isEmpty()) {
        return emptyList()
    }

    return mediaItems
        .asSequence()
        .filter { item ->
            val relativePath = item.mediaMetadata.extras
                ?.getString(LocalAudioLibrary.RelativePathExtraKey)
            isMediaInDirectory(relativePath = relativePath, directoryKey = normalizedKey)
        }
        .filter { item ->
            val relativePath = item.mediaMetadata.extras
                ?.getString(LocalAudioLibrary.RelativePathExtraKey)
            !exclusions.isMediaHidden(item.mediaId, relativePath)
        }
        .toList()
}

internal fun mediaIdsInDirectory(
    mediaItems: List<MediaItem>,
    directoryKey: String,
): Set<String> {
    val normalizedKey = directoryKey.normalizedDirectoryKey()
    if (normalizedKey.isEmpty()) {
        return emptySet()
    }

    return mediaItems.asSequence()
        .filter { item ->
            val relativePath = item.mediaMetadata.extras
                ?.getString(LocalAudioLibrary.RelativePathExtraKey)
            isMediaInDirectory(relativePath = relativePath, directoryKey = normalizedKey)
        }
        .map(MediaItem::mediaId)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
}

internal fun isMediaInDirectory(
    relativePath: String?,
    directoryKey: String,
): Boolean {
    val normalizedKey = directoryKey.normalizedDirectoryKey()
    if (normalizedKey.isEmpty()) {
        return false
    }
    return relativePath?.normalizedDirectoryKey() == normalizedKey
}

private data class MutableDirectoryCount(
    var total: Int = 0,
    var visible: Int = 0,
)

private fun directoryNameComparator(): Comparator<DirectoryEntry> {
    val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }
    return Comparator { left, right ->
        val localizedOrder = collator.compare(left.name, right.name)
        if (localizedOrder != 0) {
            localizedOrder
        } else {
            left.name.lowercase(Locale.ROOT).compareTo(right.name.lowercase(Locale.ROOT))
        }
    }
}
