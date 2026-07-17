package com.smartisan.music.ui.artist

import com.smartisan.music.data.settings.ArtistSettings
import java.util.Locale

internal fun CharSequence?.toArtistDisplayNames(
    artistSettings: ArtistSettings,
    unknownArtistTitle: String,
): List<String> {
    val artistName = toArtistDisplayText() ?: unknownArtistTitle
    val separators = artistSettings.separators.asSequence()
        .mapNotNull { it.toArtistDisplayText() }
        .distinct()
        .sortedByDescending { it.length }
        .toList()
    if (separators.isEmpty()) {
        return listOf(artistName)
    }

    val names = separators.fold(listOf(artistName)) { currentNames, separator ->
        currentNames.flatMap { name -> name.split(separator) }
    }

    return names.asSequence()
        .mapNotNull { it.toArtistDisplayText() }
        .distinctBy { it.artistNormalizedKey() }
        .toList()
        .ifEmpty { listOf(unknownArtistTitle) }
}

internal fun CharSequence?.toArtistDisplayText(): String? {
    return this?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun String.artistNormalizedKey(): String {
    return lowercase(Locale.ROOT)
}
