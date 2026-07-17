package com.smartisan.music.ui.shell.songs

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.RatingBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.ui.widgets.EditableLayout
import com.smartisan.music.ui.widgets.EditableListViewItem
import com.smartisan.music.ui.widgets.StretchTextView

internal class LegacySongsAdapter : BaseAdapter() {
    var onMoreClick: (MediaItem) -> Unit = {}
    var items: List<MediaItem> = emptyList()
        private set
    private var rows: List<LegacySongRow> = emptyList()
    private var currentMediaId: String? = null
    private var currentIsPlaying: Boolean = false
    private var displayMode: LegacySongsSortDisplayMode = LegacySongsSortDisplayMode.Name
    private var sectionMode: LegacySongsSectionMode = LegacySongsSectionMode.Name
    private var quickBarCollapsedVisibleWidth: Int = 0
    private var editMode: Boolean = false
    private var selectedMediaIds: Set<String> = emptySet()

    fun updateItems(
        nextItems: List<MediaItem>,
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
        nextDisplayMode: LegacySongsSortDisplayMode,
        nextSectionMode: LegacySongsSectionMode,
        nextQuickBarCollapsedVisibleWidth: Int,
        nextEditMode: Boolean,
        nextSelectedMediaIds: Set<String>,
    ): Boolean {
        val contentChanged = items != nextItems ||
            displayMode != nextDisplayMode ||
            sectionMode != nextSectionMode
        val quickBarInsetChanged = quickBarCollapsedVisibleWidth != nextQuickBarCollapsedVisibleWidth
        val playbackChanged = currentMediaId != nextCurrentMediaId ||
            currentIsPlaying != nextCurrentIsPlaying
        val editModeChanged = editMode != nextEditMode
        val selectionChanged = selectedMediaIds != nextSelectedMediaIds
        if (!contentChanged && !quickBarInsetChanged && !playbackChanged && !editModeChanged && !selectionChanged) {
            return false
        }

        items = nextItems
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
        displayMode = nextDisplayMode
        sectionMode = nextSectionMode
        quickBarCollapsedVisibleWidth = nextQuickBarCollapsedVisibleWidth
        editMode = nextEditMode
        selectedMediaIds = nextSelectedMediaIds
        if (contentChanged) {
            rows = buildLegacySongRows(nextItems, nextSectionMode)
            notifyDataSetChanged()
        } else if (quickBarInsetChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun setPlaybackState(
        nextCurrentMediaId: String?,
        nextCurrentIsPlaying: Boolean,
    ) {
        currentMediaId = nextCurrentMediaId
        currentIsPlaying = nextCurrentIsPlaying
    }

    fun updateVisiblePlaybackState(listView: ListView) {
        updateVisibleSongRows(
            listView = listView,
            animateEditMode = false,
        )
    }

    fun updateVisibleSongRows(
        listView: ListView,
        animateEditMode: Boolean,
    ) {
        val headerCount = listView.headerViewsCount
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex - headerCount
            val mediaItem = itemAt(position)
            val child = listView.getChildAt(childIndex)?.legacySongContentView()
            val titleView = child
                ?.findViewById<TextView>(R.id.listview_item_line_one) as? StretchTextView
            if (mediaItem == null || child == null || titleView == null) {
                continue
            }
            if (!editMode && mediaItem.mediaId == currentMediaId) {
                titleView.c(currentIsPlaying)
            } else {
                titleView.setShowingPlayImage(false)
            }
            child.bindLegacySongEditState(mediaItem, animateEditMode)
        }
    }

    fun itemAt(position: Int): MediaItem? = (rows.getOrNull(position) as? LegacySongRow.Song)?.mediaItem

    fun songIndexAt(position: Int): Int? = (rows.getOrNull(position) as? LegacySongRow.Song)?.songIndex

    fun positionForLetter(letter: String): Int {
        return rows.indexOfFirst { row ->
            row is LegacySongRow.Header && row.key == LegacySongHeaderKey.Name(letter)
        }
    }

    override fun getCount(): Int = rows.size

    override fun getItem(position: Int): Any = rows[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 5

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is LegacySongRow.Header -> 0
            is LegacySongRow.Song -> when (displayMode) {
                LegacySongsSortDisplayMode.Name -> 1
                LegacySongsSortDisplayMode.Score -> 2
                LegacySongsSortDisplayMode.PlayCount -> 3
                LegacySongsSortDisplayMode.AddedTime -> 4
            }
        }
    }

    override fun isEnabled(position: Int): Boolean {
        return rows.getOrNull(position) is LegacySongRow.Song
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return when (val row = rows[position]) {
            is LegacySongRow.Header -> getHeaderView(row, convertView, parent)
            is LegacySongRow.Song -> getSongView(row.mediaItem, convertView, parent)
        }
    }

    private fun getHeaderView(
        row: LegacySongRow.Header,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.smartlist_header, parent, false)
        view.setBackgroundResource(R.drawable.smartlist_header_bg)
        view.findViewById<TextView>(R.id.text)?.text = row.key.title(parent.context)
        return view
    }

    private fun getSongView(
        item: MediaItem,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val swipeRow = convertView as? LegacySongSwipeDeleteItemView
            ?: LegacySongSwipeDeleteItemView(parent.context)
        val view = swipeRow.contentView()
            ?: LayoutInflater.from(parent.context)
                .inflate(displayMode.layoutRes, swipeRow, false)
                .also(swipeRow::setContentView)
        val metadata = item.mediaMetadata
        val selected = item.mediaId == currentMediaId
        val title = metadata.displayTitle?.toString()
            ?: metadata.title?.toString()
            ?: parent.context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()
            ?: metadata.subtitle?.toString()
            ?: parent.context.getString(R.string.unknown_artist)
        val album = metadata.albumTitle?.toString()
            ?.takeIf(String::isNotBlank)
        val subtitle = if (displayMode == LegacySongsSortDisplayMode.Score || album.isNullOrBlank()) {
            artist
        } else {
            "$artist - $album"
        }

        view.isSelected = false
        view.isActivated = false
        view.applyLegacyQuickBarInset()
        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = title
            isSelected = true
            setTextColor(LegacyPrimaryTextColor)
            if (this is StretchTextView) {
                if (!editMode && selected) {
                    c(currentIsPlaying)
                } else {
                    setShowingPlayImage(false)
                }
            }
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.apply {
            text = subtitle
            setTextColor(LegacySecondaryTextColor)
        }
        view.findViewById<TextView>(R.id.tv_play_count)?.text = item.legacyPlayCount().toString()
        view.findViewById<RatingBar>(R.id.rb_score)?.apply {
            visibility = View.VISIBLE
            progress = item.legacyRating().toInt()
        }
        view.findViewById<ImageView>(R.id.mime_type)?.apply {
            val badgeRes = item.legacyQualityBadgeRes()
            if (badgeRes != null) {
                visibility = View.VISIBLE
                setImageResource(badgeRes)
            } else {
                visibility = View.GONE
            }
        }
        view.findViewById<CheckBox>(R.id.cb_del)?.isChecked = item.mediaId in selectedMediaIds
        view.findViewById<View>(R.id.iv_right)?.visibility = View.GONE
        view.findViewById<View>(R.id.img_action_more)?.apply {
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = false
            setOnClickListener {
                onMoreClick(item)
            }
        }
        view.bindLegacySongEditState(item, animate = false)
        swipeRow.resetLegacySwipeDelete()
        return swipeRow
    }

    private fun View.applyLegacyQuickBarInset() {
        val actionMore = findViewById<View>(R.id.img_action_more)
        val actionMoreWidth = actionMore.legacyMeasuredWidth()
        val rightInset = if (displayMode == LegacySongsSortDisplayMode.Name && quickBarCollapsedVisibleWidth > 0) {
            (quickBarCollapsedVisibleWidth - actionMoreWidth / 3).coerceAtLeast(0)
        } else {
            0
        }
        if (paddingRight != rightInset) {
            setPadding(paddingLeft, paddingTop, rightInset, paddingBottom)
        }
    }

    private fun View?.legacyMeasuredWidth(): Int {
        this ?: return 0
        if (measuredWidth <= 0) {
            measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
        }
        return measuredWidth
    }

    private fun View.bindLegacySongEditState(
        item: MediaItem,
        animate: Boolean,
    ) {
        val checked = item.mediaId in selectedMediaIds
        (this as? EditableListViewItem)?.bindLegacyEditState(
            enabled = editMode,
            checked = checked,
            animate = animate,
        )
        (this as? EditableLayout)?.bindLegacyEditState(
            enabled = editMode,
            checked = checked,
            animate = animate,
        )
    }
}

private fun View.legacySongContentView(): View {
    return (this as? LegacySongSwipeDeleteItemView)?.contentView() ?: this
}

private sealed class LegacySongRow {
    data class Header(val key: LegacySongHeaderKey) : LegacySongRow()

    data class Song(
        val mediaItem: MediaItem,
        val songIndex: Int,
    ) : LegacySongRow()
}

private sealed class LegacySongHeaderKey {
    data class Name(val letter: String) : LegacySongHeaderKey()
    data class Score(val score: Long) : LegacySongHeaderKey()
    data class AddedTime(val bucket: Int) : LegacySongHeaderKey()

    fun title(context: Context): String {
        return when (this) {
            is Name -> letter
            is Score -> if (score > 0L) {
                context.getString(R.string.song_score_header, score)
            } else {
                context.getString(R.string.nostar)
            }
            is AddedTime -> when (bucket) {
                LegacyAddedTimeBucketToday -> context.getString(R.string.section_today)
                LegacyAddedTimeBucketLastWeek -> context.getString(R.string.section_day_before)
                LegacyAddedTimeBucketLastMonth -> context.getString(R.string.section_week_before)
                else -> context.getString(R.string.section_month_before)
            }
        }
    }
}

private fun buildLegacySongRows(
    mediaItems: List<MediaItem>,
    sectionMode: LegacySongsSectionMode,
): List<LegacySongRow> {
    if (sectionMode == LegacySongsSectionMode.None) {
        return mediaItems.mapIndexed { index, mediaItem ->
            LegacySongRow.Song(mediaItem, index)
        }
    }
    val rows = mutableListOf<LegacySongRow>()
    var previousKey: LegacySongHeaderKey? = null
    mediaItems.forEachIndexed { index, mediaItem ->
        val key = when (sectionMode) {
            LegacySongsSectionMode.Name -> LegacySongHeaderKey.Name(mediaItem.legacySectionLetter())
            LegacySongsSectionMode.Score -> LegacySongHeaderKey.Score(mediaItem.legacyRating())
            LegacySongsSectionMode.AddedTime -> LegacySongHeaderKey.AddedTime(mediaItem.legacyAddedTimeBucket())
            LegacySongsSectionMode.None -> null
        }
        if (key != null && key != previousKey) {
            rows += LegacySongRow.Header(key)
            previousKey = key
        }
        rows += LegacySongRow.Song(mediaItem, index)
    }
    return rows
}

private val LegacySongsSortDisplayMode.layoutRes: Int
    get() = when (this) {
        LegacySongsSortDisplayMode.Name -> R.layout.item_sort_by_name_layout
        LegacySongsSortDisplayMode.Score -> R.layout.item_sort_by_score_layout
        LegacySongsSortDisplayMode.PlayCount -> R.layout.item_sort_by_play_count
        LegacySongsSortDisplayMode.AddedTime -> R.layout.item_sort_by_time_layout
    }

private val LegacyPrimaryTextColor = Color.rgb(0x35, 0x35, 0x39)
private val LegacySecondaryTextColor = Color.rgb(0xa4, 0xa7, 0xac)

private fun MediaItem.legacyQualityBadgeRes(): Int? {
    return when (mediaMetadata.extras?.getString(LocalAudioLibrary.AudioQualityBadgeExtraKey)) {
        "flac" -> R.drawable.audio_quality_flac
        "ape" -> R.drawable.audio_quality_ape
        "wav" -> R.drawable.audio_quality_wav
        "aiff" -> R.drawable.audio_quality_aiff
        "alac" -> R.drawable.audio_quality_alac
        "cue" -> R.drawable.audio_quality_cue
        else -> null
    }
}
