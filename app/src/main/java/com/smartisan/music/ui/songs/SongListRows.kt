package com.smartisan.music.ui.songs

import android.content.Context
import androidx.media3.common.MediaItem
import com.smartisan.music.R

internal sealed class SongListRow {
    data class Header(val key: SongHeaderKey) : SongListRow()

    data class Song(
        val mediaItem: MediaItem,
        val songIndex: Int,
    ) : SongListRow()
}

internal sealed class SongHeaderKey {
    data class Name(val letter: String) : SongHeaderKey()

    data class Score(val score: Long) : SongHeaderKey()

    data class AddedTime(val bucket: Int) : SongHeaderKey()

    fun title(context: Context): String {
        return when (this) {
            is Name -> letter
            is Score ->
                if (score > 0L) {
                    context.getString(R.string.song_score_header, score)
                } else {
                    context.getString(R.string.nostar)
                }
            is AddedTime ->
                when (bucket) {
                    AddedTimeBucketToday -> context.getString(R.string.section_today)
                    AddedTimeBucketLastWeek -> context.getString(R.string.section_day_before)
                    AddedTimeBucketLastMonth -> context.getString(R.string.section_week_before)
                    else -> context.getString(R.string.section_month_before)
                }
        }
    }
}

internal fun buildSongRows(
    mediaItems: List<MediaItem>,
    sectionMode: SongSectionMode,
): List<SongListRow> {
    if (sectionMode == SongSectionMode.None) {
        return mediaItems.mapIndexed { index, mediaItem ->
            SongListRow.Song(mediaItem, index)
        }
    }
    val rows = mutableListOf<SongListRow>()
    var previousKey: SongHeaderKey? = null
    mediaItems.forEachIndexed { index, mediaItem ->
        val key =
            when (sectionMode) {
                SongSectionMode.Name -> SongHeaderKey.Name(mediaItem.songSectionLetter())
                SongSectionMode.Score -> SongHeaderKey.Score(mediaItem.songRating())
                SongSectionMode.AddedTime -> SongHeaderKey.AddedTime(mediaItem.addedTimeBucket())
                SongSectionMode.None -> null
            }
        if (key != null && key != previousKey) {
            rows += SongListRow.Header(key)
            previousKey = key
        }
        rows += SongListRow.Song(mediaItem, index)
    }
    return rows
}
