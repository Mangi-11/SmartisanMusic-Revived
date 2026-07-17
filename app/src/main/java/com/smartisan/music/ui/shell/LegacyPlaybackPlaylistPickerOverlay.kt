package com.smartisan.music.ui.shell

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.smartisan.music.R
import com.smartisan.music.data.playlist.UserPlaylistSummary
import com.smartisan.music.ui.widgets.legacy.MenuDialogTitleBar

@Composable
internal fun LegacyPlaybackPlaylistPickerOverlay(
    visible: Boolean,
    playlists: List<UserPlaylistSummary>,
    onDismiss: () -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
    createNewPlaylistVisible: Boolean = true,
    @StringRes titleRes: Int = R.string.playlist_picker_title,
    @StringRes createNewPlaylistTitleRes: Int = R.string.new_playlist,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(LegacyPlaylistPickerAnimationMillis)),
        exit = fadeOut(animationSpec = tween(LegacyPlaylistPickerAnimationMillis)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            AnimatedVisibility(
                visible = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = slideInVertically(
                    animationSpec = tween(
                        durationMillis = LegacyPlaylistPickerAnimationMillis,
                        easing = LegacyPlaylistPickerEasing,
                    ),
                    initialOffsetY = { fullHeight -> fullHeight },
                ),
                exit = slideOutVertically(
                    animationSpec = tween(
                        durationMillis = LegacyPlaylistPickerAnimationMillis,
                        easing = LegacyPlaylistPickerEasing,
                    ),
                    targetOffsetY = { fullHeight -> fullHeight },
                ),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        LegacyPlaybackPlaylistPickerView(context).apply {
                            setOnClickListener { }
                        }
                    },
                    update = { view ->
                        view.bind(
                            playlists = playlists,
                            onDismiss = onDismiss,
                            onCreateNewPlaylist = onCreateNewPlaylist,
                            onPlaylistSelected = onPlaylistSelected,
                            createNewPlaylistVisible = createNewPlaylistVisible,
                            titleRes = titleRes,
                            createNewPlaylistTitleRes = createNewPlaylistTitleRes,
                        )
                    },
                )
            }
        }
    }
}

private class LegacyPlaybackPlaylistPickerView(
    context: Context,
) : LinearLayout(context) {
    private val adapter = PlaylistPickerAdapter(context)
    private val titleBar = MenuDialogTitleBar(context).apply {
        forceRequestAccessibilityFocusWhenAttached(false)
        setTitle(R.string.playlist_picker_title)
        setLeftButtonVisibility(View.INVISIBLE)
        setRightButtonVisibility(View.VISIBLE)
    }
    private val listView = ListView(context).apply {
        divider = ColorDrawable(ContextCompat.getColor(context, R.color.listview_divider_color))
        dividerHeight = 1
        selector = ContextCompat.getDrawable(context, R.drawable.listview_selector)
        cacheColorHint = Color.TRANSPARENT
        isVerticalScrollBarEnabled = false
        adapter = this@LegacyPlaybackPlaylistPickerView.adapter
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        isClickable = true
        addView(
            titleBar,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.titlebar_height),
            ),
        )
        addView(
            listView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun bind(
        playlists: List<UserPlaylistSummary>,
        onDismiss: () -> Unit,
        onCreateNewPlaylist: () -> Unit,
        onPlaylistSelected: (String) -> Unit,
        createNewPlaylistVisible: Boolean,
        @StringRes titleRes: Int,
        @StringRes createNewPlaylistTitleRes: Int,
    ) {
        titleBar.setTitle(titleRes)
        titleBar.setOnRightButtonClickListener {
            onDismiss()
        }
        adapter.update(
            playlists = playlists,
            createNewPlaylistVisible = createNewPlaylistVisible,
            createNewPlaylistTitleRes = createNewPlaylistTitleRes,
        )
        listView.layoutParams = listView.layoutParams.apply {
            val rowCount = playlists.size + if (createNewPlaylistVisible) 1 else 0
            height = context.dpInt(
                (LegacyPlaylistPickerRowHeightDp * rowCount.coerceAtLeast(1))
                    .coerceAtMost(LegacyPlaylistPickerMaxListHeightDp),
            )
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            if (createNewPlaylistVisible && position == 0) {
                onCreateNewPlaylist()
            } else {
                val playlistIndex = if (createNewPlaylistVisible) position - 1 else position
                playlists.getOrNull(playlistIndex)?.let { playlist ->
                    onPlaylistSelected(playlist.id)
                }
            }
        }
    }
}

private class PlaylistPickerAdapter(
    private val context: Context,
) : BaseAdapter() {
    private var playlists: List<UserPlaylistSummary> = emptyList()
    private var createNewPlaylistVisible = true
    @StringRes private var createNewPlaylistTitleRes = R.string.new_playlist

    fun update(
        playlists: List<UserPlaylistSummary>,
        createNewPlaylistVisible: Boolean,
        @StringRes createNewPlaylistTitleRes: Int,
    ) {
        this.playlists = playlists
        this.createNewPlaylistVisible = createNewPlaylistVisible
        this.createNewPlaylistTitleRes = createNewPlaylistTitleRes
        notifyDataSetChanged()
    }

    override fun getCount(): Int = playlists.size + if (createNewPlaylistVisible) 1 else 0

    override fun getItem(position: Int): UserPlaylistSummary? {
        val playlistIndex = if (createNewPlaylistVisible) position - 1 else position
        return playlists.getOrNull(playlistIndex)
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val holder: PlaylistPickerRowHolder
        val root = if (convertView == null) {
            val row = RelativeLayout(context).apply {
                layoutParams = AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    context.dpInt(LegacyPlaylistPickerRowHeightDp),
                )
                setBackgroundResource(R.drawable.listview_selector)
            }
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val title = TextView(context).apply {
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(context, R.color.list_item_first_line))
                typeface = Typeface.DEFAULT
            }
            val subtitle = TextView(context).apply {
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(context, R.color.list_item_second_line))
            }
            textContainer.addView(title)
            textContainer.addView(subtitle)
            row.addView(
                textContainer,
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = context.dpInt(18f)
                    rightMargin = context.dpInt(50f)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
            val arrow = ImageView(context).apply {
                setBackgroundResource(R.drawable.arrow3_selector)
            }
            row.addView(
                arrow,
                RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    rightMargin = context.dpInt(16f)
                    addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                },
            )
            holder = PlaylistPickerRowHolder(title, subtitle, arrow)
            row.tag = holder
            row
        } else {
            holder = convertView.tag as PlaylistPickerRowHolder
            convertView
        }

        if (createNewPlaylistVisible && position == 0) {
            holder.title.setText(createNewPlaylistTitleRes)
            holder.subtitle.text = ""
            holder.subtitle.visibility = View.GONE
            holder.arrow.visibility = View.VISIBLE
        } else {
            val playlistIndex = if (createNewPlaylistVisible) position - 1 else position
            val playlist = playlists[playlistIndex]
            holder.title.text = playlist.name
            holder.subtitle.text = context.resources.getQuantityString(
                R.plurals.legacy_playlist_song_count,
                playlist.songCount,
                playlist.songCount,
            )
            holder.subtitle.visibility = View.VISIBLE
            holder.arrow.visibility = View.VISIBLE
        }
        return root
    }
}

private data class PlaylistPickerRowHolder(
    val title: TextView,
    val subtitle: TextView,
    val arrow: ImageView,
)

private fun Context.dpInt(value: Float): Int {
    return (value * resources.displayMetrics.density + 0.5f).toInt()
}

private const val LegacyPlaylistPickerAnimationMillis = 300
private const val LegacyPlaylistPickerRowHeightDp = 60f
private const val LegacyPlaylistPickerMaxListHeightDp = 420f
private val LegacyPlaylistPickerEasing = androidx.compose.animation.core.Easing { fraction ->
    val inverse = 1f - fraction
    1f - inverse * inverse
}
