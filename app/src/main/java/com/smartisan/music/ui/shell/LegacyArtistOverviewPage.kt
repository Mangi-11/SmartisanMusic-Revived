package com.smartisan.music.ui.shell

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisan.music.R
import com.smartisan.music.ui.artist.ArtistSummary

@Composable
internal fun LegacyPortArtistOverviewPage(
    active: Boolean,
    artists: List<ArtistSummary>,
    onArtistSelected: (ArtistSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            LegacyArtistOverviewRoot(viewContext)
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            val adapter = root.listView.legacyWrappedAdapter<LegacyArtistOverviewAdapter>()
                ?: LegacyArtistOverviewAdapter().also { adapter ->
                    root.listView.adapter = adapter
                }
            if (adapter.updateItems(artists)) {
                root.listView.scheduleLayoutAnimation()
            }
            root.bindFooter(artists.size)
            root.listView.setOnItemClickListener { _, _, position, _ ->
                adapter.itemAt(position)?.let(onArtistSelected)
            }
        },
    )
}

private class LegacyArtistOverviewRoot(context: Context) : FrameLayout(context) {
    val listView: ListView

    init {
        setBackgroundResource(R.drawable.account_background)
        listView = ListView(context).apply {
            id = R.id.list
            divider = ColorDrawable(context.getColor(R.color.listview_divider_color))
            dividerHeight = resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundResource(R.drawable.account_background)
            layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.list_anim_layout)
            addLegacyPortListFooter()
        }
        addView(
            listView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun bindFooter(artistCount: Int) {
        listView.bindLegacyPortListFooter(
            pluralsRes = R.plurals.legacy_artist_count,
            count = artistCount,
            visible = artistCount >= LegacyArtistListFooterThreshold,
        )
    }
}

private class LegacyArtistOverviewAdapter : BaseAdapter() {
    private var items: List<ArtistSummary> = emptyList()

    fun updateItems(nextItems: List<ArtistSummary>): Boolean {
        if (items == nextItems) {
            return false
        }
        items = nextItems
        notifyDataSetChanged()
        return true
    }

    fun itemAt(position: Int): ArtistSummary? = items.getOrNull(position)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.artist_listview_items_text, parent, false)
        val artist = items[position]
        view.setBackgroundResource(R.drawable.listview_selector)
        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = artist.name
            setTextColor(LegacyArtistPrimaryTextColor)
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.apply {
            val albumCount = parent.context.resources.getQuantityString(
                R.plurals.legacy_album_count,
                artist.albumCount,
                artist.albumCount,
            )
            val trackCount = parent.context.resources.getQuantityString(
                R.plurals.track_count,
                artist.trackCount,
                artist.trackCount,
            )
            text = parent.context.getString(
                R.string.legacy_artist_summary,
                albumCount,
                trackCount,
            )
            setTextColor(LegacyArtistSecondaryTextColor)
        }
        return view
    }
}
