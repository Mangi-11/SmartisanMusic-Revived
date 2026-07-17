package com.smartisan.music.ui.shell.songs

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.platform.text.HanLatinTransliterator
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

internal fun List<MediaItem>.sortedForLegacySort(sortIndex: Int): List<MediaItem> {
    return when (sortIndex) {
        0 -> sortedWith(
            compareBy<MediaItem> { item ->
                item.legacySortBucket()
            }.thenBy { item ->
                item.legacySortKey()
            },
        )
        1 -> sortedWith(
            compareByDescending<MediaItem> { item ->
                item.legacyRating()
            }.thenBy { item ->
                item.legacySortKey()
            }.thenBy { item ->
                item.mediaId
            },
        )
        2 -> sortedWith(
            compareByDescending<MediaItem> { item ->
                item.legacyPlayCount()
            }.thenBy { item ->
                item.legacySortKey()
            }.thenBy { item ->
                item.mediaId
            },
        )
        3 -> sortedWith(
            compareByDescending<MediaItem> { item ->
                item.legacyGenerationAdded()
            }.thenBy { item ->
                item.legacySortKey()
            }.thenBy { item ->
                item.mediaId
            },
        )
        else -> this
    }
}

internal enum class LegacySongsSortDisplayMode {
    Name,
    Score,
    PlayCount,
    AddedTime,
}

internal enum class LegacySongsSectionMode {
    None,
    Name,
    Score,
    AddedTime,
}

internal fun Int.toLegacySongsSortDisplayMode(): LegacySongsSortDisplayMode {
    return when (this) {
        1 -> LegacySongsSortDisplayMode.Score
        2 -> LegacySongsSortDisplayMode.PlayCount
        3 -> LegacySongsSortDisplayMode.AddedTime
        else -> LegacySongsSortDisplayMode.Name
    }
}

internal fun LegacySongsSortDisplayMode.toSectionMode(): LegacySongsSectionMode {
    return when (this) {
        LegacySongsSortDisplayMode.Name -> LegacySongsSectionMode.Name
        LegacySongsSortDisplayMode.Score -> LegacySongsSectionMode.Score
        LegacySongsSortDisplayMode.PlayCount -> LegacySongsSectionMode.None
        LegacySongsSortDisplayMode.AddedTime -> LegacySongsSectionMode.AddedTime
    }
}

internal fun MediaItem.legacySortTitle(): String {
    return mediaMetadata.displayTitle?.toString()
        ?: mediaMetadata.title?.toString()
        ?: ""
}

internal fun MediaItem.legacySortKey(): String {
    return mediaMetadata.extras
        ?.getString(LocalAudioLibrary.TitleSortKeyExtraKey)
        ?.takeIf { it.isNotBlank() }
        ?: LegacyTitleNormalizer.normalize(legacySortTitle())
}

internal fun MediaItem.legacySortBucket(): String {
    val letter = legacySectionLetter()
    return if (letter == "#") {
        "ZZZ"
    } else {
        letter
    }
}

internal fun MediaItem.legacySectionLetter(): String {
    return mediaMetadata.extras
        ?.getString(LocalAudioLibrary.TitleSectionExtraKey)
        ?.takeIf { it.isNotBlank() }
        ?: legacySortKey().legacySectionLetterFromSortKey()
}

internal fun String.legacySectionLetterFromSortKey(): String {
    val firstLetter = firstOrNull { char ->
        char.isLetterOrDigit()
    } ?: return "#"
    val upper = firstLetter.uppercaseChar()
    return if (upper in 'A'..'Z') {
        upper.toString()
    } else {
        "#"
    }
}

internal const val LegacyAddedTimeBucketToday = 1
internal const val LegacyAddedTimeBucketLastWeek = 2
internal const val LegacyAddedTimeBucketLastMonth = 3
internal const val LegacyAddedTimeBucketOlder = 4
private const val LegacyDayMillis = 24L * 60L * 60L * 1000L

internal fun MediaItem.legacyRating(): Long {
    return mediaMetadata.extras?.legacyExtraLong(
        LocalAudioLibrary.RatingExtraKey,
        "star",
        "score",
        "rating",
        "play_score",
    )?.coerceIn(0L, 5L) ?: 0L
}

internal fun MediaItem.legacyPlayCount(): Long {
    return mediaMetadata.extras?.legacyExtraLong(
        LocalAudioLibrary.PlayCountExtraKey,
        "play_count",
        "playCount",
        "play_count_all",
    ) ?: 0L
}

private fun Bundle.legacyExtraLong(vararg keys: String): Long {
    keys.forEach { key ->
        if (!containsKey(key)) {
            return@forEach
        }
        val longValue = getLong(key, Long.MIN_VALUE)
        if (longValue != Long.MIN_VALUE) {
            return longValue
        }
        val intValue = getInt(key, Int.MIN_VALUE)
        if (intValue != Int.MIN_VALUE) {
            return intValue.toLong()
        }
        val doubleValue = getDouble(key, Double.NaN)
        if (!doubleValue.isNaN()) {
            return doubleValue.toLong()
        }
    }
    return 0L
}

internal fun MediaItem.legacyGenerationAdded(): Long {
    return mediaMetadata.extras?.getLong(LocalAudioLibrary.GenerationAddedExtraKey, 0L) ?: 0L
}

internal fun MediaItem.legacyAddedTimeBucket(): Int {
    val addedAtMillis = (mediaMetadata.extras?.getLong(LocalAudioLibrary.DateAddedExtraKey, 0L) ?: 0L) * 1000L
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val weekStart = todayStart - 7L * LegacyDayMillis
    val monthStart = todayStart - 30L * LegacyDayMillis
    return when {
        addedAtMillis > todayStart -> LegacyAddedTimeBucketToday
        addedAtMillis > weekStart -> LegacyAddedTimeBucketLastWeek
        addedAtMillis > monthStart -> LegacyAddedTimeBucketLastMonth
        else -> LegacyAddedTimeBucketOlder
    }
}

internal object LegacyTitleNormalizer {
    private val combiningMarks = "\\p{Mn}+".toRegex()

    fun normalize(title: String): String {
        val trimmed = title.trim()
        val transliterated = HanLatinTransliterator.transliterate(trimmed)
        return Normalizer.normalize(transliterated, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}
