package com.smartisan.music.ui.genre

import androidx.media3.common.MediaItem
import java.text.Collator
import java.util.Locale

internal data class GenreSummary(
    val id: String,
    val name: String,
    val songs: List<MediaItem>,
) {
    val trackCount: Int = songs.size
}

internal fun buildGenreSummaries(
    mediaItems: List<MediaItem>,
    genreMap: Map<String, String?>,
    unknownGenreTitle: String,
): List<GenreSummary> {
    val groups = linkedMapOf<String, MutableGenreGroup>()

    mediaItems.forEach { item ->
        val genreName = genreMap[item.mediaId]?.takeIf(String::isNotBlank) ?: unknownGenreTitle
        val genreKey = genreName.genreGroupingKey(unknownGenreTitle)
        val group = groups.getOrPut(genreKey) {
            MutableGenreGroup(
                id = "genre:$genreKey",
                name = genreName,
            )
        }
        group.songs += item
    }

    return groups.values
        .map { group ->
            GenreSummary(
                id = group.id,
                name = group.name,
                songs = group.songs.toList(),
            )
        }
        .sortedWith(genreNameComparator(unknownGenreTitle))
}

internal fun shouldResetGenreDetailSelection(
    selectedGenreId: String?,
    hasPermission: Boolean,
    hasSongs: Boolean,
    hasGenres: Boolean?,
): Boolean {
    if (selectedGenreId == null) {
        return false
    }
    if (!hasPermission) {
        return true
    }
    if (!hasSongs) {
        return true
    }
    return hasGenres == false
}

internal fun isGenreSelectionAvailable(
    selectedGenreId: String?,
    genres: List<GenreSummary>,
): Boolean {
    if (selectedGenreId == null) {
        return true
    }
    return genres.any { it.id == selectedGenreId }
}

private data class MutableGenreGroup(
    val id: String,
    val name: String,
    val songs: MutableList<MediaItem> = mutableListOf(),
)

private fun String.genreGroupingKey(unknownGenreTitle: String): String {
    return if (this == unknownGenreTitle) {
        UnknownGenreKey
    } else {
        normalizedKey()
    }
}

private fun genreNameComparator(unknownGenreTitle: String): Comparator<GenreSummary> {
    val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }

    return Comparator { left, right ->
        val leftUnknown = left.name == unknownGenreTitle
        val rightUnknown = right.name == unknownGenreTitle
        when {
            leftUnknown && !rightUnknown -> 1
            !leftUnknown && rightUnknown -> -1
            else -> {
                val localizedOrder = collator.compare(left.name, right.name)
                if (localizedOrder != 0) {
                    localizedOrder
                } else {
                    left.name.normalizedKey().compareTo(right.name.normalizedKey())
                }
            }
        }
    }
}

private fun String.normalizedKey(): String {
    return lowercase(Locale.ROOT)
}

private const val UnknownGenreKey = "__unknown_genre__"
