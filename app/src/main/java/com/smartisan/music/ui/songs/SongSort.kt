package com.smartisan.music.ui.songs

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.smartisan.music.platform.text.HanLatinTransliterator
import com.smartisan.music.playback.LocalAudioLibrary
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

internal fun List<MediaItem>.sortedForSongSort(sortIndex: Int): List<MediaItem> {
    return when (sortIndex) {
        0 ->
            sortedWith(
                compareBy<MediaItem> { item ->
                        item.songSortBucket()
                    }
                    .thenBy { item ->
                        item.songSortKey()
                    }
            )
        1 ->
            sortedWith(
                compareByDescending<MediaItem> { item ->
                        item.songRating()
                    }
                    .thenBy { item ->
                        item.songSortKey()
                    }
                    .thenBy { item ->
                        item.mediaId
                    }
            )
        2 ->
            sortedWith(
                compareByDescending<MediaItem> { item ->
                        item.songPlayCount()
                    }
                    .thenBy { item ->
                        item.songSortKey()
                    }
                    .thenBy { item ->
                        item.mediaId
                    }
            )
        3 ->
            sortedWith(
                compareByDescending<MediaItem> { item ->
                        item.generationAdded()
                    }
                    .thenBy { item ->
                        item.songSortKey()
                    }
                    .thenBy { item ->
                        item.mediaId
                    }
            )
        else -> this
    }
}

internal enum class SongSortDisplayMode {
    Name,
    Score,
    PlayCount,
    AddedTime,
}

internal enum class SongSectionMode {
    None,
    Name,
    Score,
    AddedTime,
}

internal fun Int.toSongSortDisplayMode(): SongSortDisplayMode {
    return when (this) {
        1 -> SongSortDisplayMode.Score
        2 -> SongSortDisplayMode.PlayCount
        3 -> SongSortDisplayMode.AddedTime
        else -> SongSortDisplayMode.Name
    }
}

internal fun SongSortDisplayMode.toSectionMode(): SongSectionMode {
    return when (this) {
        SongSortDisplayMode.Name -> SongSectionMode.Name
        SongSortDisplayMode.Score -> SongSectionMode.Score
        SongSortDisplayMode.PlayCount -> SongSectionMode.None
        SongSortDisplayMode.AddedTime -> SongSectionMode.AddedTime
    }
}

internal fun MediaItem.songSortTitle(): String {
    return mediaMetadata.displayTitle?.toString() ?: mediaMetadata.title?.toString() ?: ""
}

internal fun MediaItem.songSortKey(): String {
    return mediaMetadata.extras?.getString(LocalAudioLibrary.TitleSortKeyExtraKey)?.takeIf {
        it.isNotBlank()
    } ?: SongTitleNormalizer.normalize(songSortTitle())
}

internal fun MediaItem.songSortBucket(): String {
    val letter = songSectionLetter()
    return if (letter == "#") {
        "ZZZ"
    } else {
        letter
    }
}

internal fun MediaItem.songSectionLetter(): String {
    return mediaMetadata.extras?.getString(LocalAudioLibrary.TitleSectionExtraKey)?.takeIf {
        it.isNotBlank()
    } ?: songSortKey().sectionLetterFromSortKey()
}

internal fun String.sectionLetterFromSortKey(): String {
    val firstLetter =
        firstOrNull { char ->
            char.isLetterOrDigit()
        } ?: return "#"
    val upper = firstLetter.uppercaseChar()
    return if (upper in 'A'..'Z') {
        upper.toString()
    } else {
        "#"
    }
}

internal const val AddedTimeBucketToday = 1
internal const val AddedTimeBucketLastWeek = 2
internal const val AddedTimeBucketLastMonth = 3
internal const val AddedTimeBucketOlder = 4
private const val DayMillis = 24L * 60L * 60L * 1000L

internal fun MediaItem.songRating(): Long {
    return mediaMetadata.extras
        ?.extraLong(
            LocalAudioLibrary.RatingExtraKey,
            "star",
            "score",
            "rating",
            "play_score",
        )
        ?.coerceIn(0L, 5L) ?: 0L
}

internal fun MediaItem.songPlayCount(): Long {
    return mediaMetadata.extras?.extraLong(
        LocalAudioLibrary.PlayCountExtraKey,
        "play_count",
        "playCount",
        "play_count_all",
    ) ?: 0L
}

private fun Bundle.extraLong(vararg keys: String): Long {
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

internal fun MediaItem.generationAdded(): Long {
    return mediaMetadata.extras?.getLong(LocalAudioLibrary.GenerationAddedExtraKey, 0L) ?: 0L
}

internal fun MediaItem.addedTimeBucket(): Int {
    val addedAtMillis =
        (mediaMetadata.extras?.getLong(LocalAudioLibrary.DateAddedExtraKey, 0L) ?: 0L) * 1000L
    val todayStart =
        Calendar.getInstance()
            .apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            .timeInMillis
    val weekStart = todayStart - 7L * DayMillis
    val monthStart = todayStart - 30L * DayMillis
    return when {
        addedAtMillis > todayStart -> AddedTimeBucketToday
        addedAtMillis > weekStart -> AddedTimeBucketLastWeek
        addedAtMillis > monthStart -> AddedTimeBucketLastMonth
        else -> AddedTimeBucketOlder
    }
}

internal object SongTitleNormalizer {
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
