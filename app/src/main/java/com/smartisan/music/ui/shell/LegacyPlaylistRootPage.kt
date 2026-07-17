package com.smartisan.music.ui.shell

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.playlist.PlaylistCreateResult
import com.smartisan.music.data.playlist.PlaylistRenameResult
import com.smartisan.music.data.playlist.PlaylistRepository
import com.smartisan.music.data.playlist.UserPlaylistDetail
import com.smartisan.music.data.playlist.UserPlaylistSummary
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.ui.shell.titlebar.LegacyPortSmartisanTitleBar
import com.smartisan.music.ui.widgets.CustomCheckBox
import com.smartisan.music.ui.widgets.EditableLayout
import com.smartisan.music.ui.widgets.EditableListViewItem
import com.smartisan.music.ui.widgets.StretchTextView
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.smartisan.music.ui.widgets.legacy.ActionButtonGroup
import com.smartisan.music.ui.widgets.legacy.MenuDialog
import com.smartisan.music.ui.widgets.legacy.QuickBarEx
import com.smartisan.music.ui.widgets.legacy.TitleBar
import java.text.Normalizer
import java.util.Locale

@Composable
internal fun LegacyPlaylistRootPage(
    active: Boolean,
    playlists: List<UserPlaylistSummary>,
    editMode: Boolean,
    selectedPlaylistIds: Set<String>,
    onCreatePlaylist: () -> Unit,
    onRenamePlaylist: (UserPlaylistSummary) -> Unit,
    onPlaylistClick: (UserPlaylistSummary) -> Unit,
    onPlaylistSelectionChange: (UserPlaylistSummary, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LegacyPlaylistRootView(context)
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            root.bind(
                playlists = playlists,
                editMode = editMode,
                selectedPlaylistIds = selectedPlaylistIds,
                onCreatePlaylist = onCreatePlaylist,
                onRenamePlaylist = onRenamePlaylist,
                onPlaylistClick = onPlaylistClick,
                onPlaylistSelectionChange = onPlaylistSelectionChange,
            )
        },
    )
}

private class LegacyPlaylistRootView(context: Context) : FrameLayout(context) {
    private val addRow = LinearLayout(context)
    private val listView = ListView(context)
    private var boundEditMode: Boolean? = null
    private var pendingEditAnimationMode: Boolean? = null
    private val blankView = LegacyPlaylistBlankView(
        context = context,
        iconRes = R.drawable.blank_playlist,
        primaryText = context.getString(R.string.no_playlist),
        secondaryText = context.getString(R.string.create_playlist),
    )

    init {
        setBackgroundResource(R.drawable.account_background)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        addRow.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.list_header_selector)
            isClickable = true
            isFocusable = true
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.add_icon_selector)
                    scaleType = ImageView.ScaleType.CENTER
                },
                LinearLayout.LayoutParams(dpPx(60), LinearLayout.LayoutParams.MATCH_PARENT),
            )
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.new_playlist)
                    setTextColor(context.getColor(R.color.list_item_first_line))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_size_yun))
                    gravity = Gravity.CENTER_VERTICAL
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        column.addView(addRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpPx(60)))

        val listFrame = FrameLayout(context)
        column.addView(listFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        listView.apply {
            divider = ColorDrawable(context.getColor(R.color.listview_divider_color))
            dividerHeight = resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.TRANSPARENT)
            layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.list_anim_layout)
            isVerticalScrollBarEnabled = false
            addLegacyPortListFooter()
        }
        listFrame.addView(listView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        listFrame.addView(blankView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(
        playlists: List<UserPlaylistSummary>,
        editMode: Boolean,
        selectedPlaylistIds: Set<String>,
        onCreatePlaylist: () -> Unit,
        onRenamePlaylist: (UserPlaylistSummary) -> Unit,
        onPlaylistClick: (UserPlaylistSummary) -> Unit,
        onPlaylistSelectionChange: (UserPlaylistSummary, Boolean) -> Unit,
    ) {
        addRow.alpha = if (editMode) 0.35f else 1f
        addRow.isEnabled = !editMode
        addRow.setOnClickListener {
            if (!editMode) {
                onCreatePlaylist()
            }
        }
        blankView.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (playlists.isEmpty()) View.INVISIBLE else View.VISIBLE
        listView.bindLegacyPortListFooter(
            pluralsRes = R.plurals.playlists_count,
            count = playlists.size,
            visible = playlists.size >= PlaylistRootFooterThreshold,
        )

        val adapter = listView.legacyWrappedAdapter<LegacyPlaylistRootAdapter>()
            ?: LegacyPlaylistRootAdapter().also { adapter ->
                listView.adapter = adapter
            }
        adapter.onRenamePlaylist = onRenamePlaylist
        val previousEditMode = boundEditMode
        val animateEditMode = previousEditMode != null && previousEditMode != editMode
        if (animateEditMode) {
            adapter.forceStaticEditMode(previousEditMode)
        }
        boundEditMode = editMode
        val contentChanged = adapter.updateItems(
            nextItems = playlists,
            nextEditMode = editMode,
            nextSelectedIds = selectedPlaylistIds,
        )
        if (animateEditMode) {
            pendingEditAnimationMode = editMode
            animateVisibleRowsWhenReady(
                adapter = adapter,
                editMode = editMode,
            )
        } else if (contentChanged) {
            pendingEditAnimationMode = null
            listView.scheduleLayoutAnimation()
        } else if (pendingEditAnimationMode == editMode) {
            // 原版 ModeChanger 会直接驱动当前可见行；等待 post 动画执行前不要静态覆盖起点。
        } else {
            adapter.clearForcedStaticEditMode()
            adapter.updateVisibleRows(
                listView = listView,
                animateEditMode = false,
            )
        }
        val slideSelectionController = listView.legacySlideSelectionController(
            startArea = LegacySlideSelectionStartArea.Checkbox,
        )
        slideSelectionController.update(
            enabled = editMode,
            selectedKeys = selectedPlaylistIds,
            keyAtPosition = { position ->
                adapter.itemAt(position)?.id
            },
            onSelectionChange = { playlistId, selected ->
                playlists.firstOrNull { playlist -> playlist.id == playlistId }
                    ?.let { playlist -> onPlaylistSelectionChange(playlist, selected) }
            },
        )
        listView.setOnTouchListener { _, event ->
            slideSelectionController.handleTouch(event)
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position >= adapter.count) {
                return@setOnItemClickListener
            }
            onPlaylistClick(adapter.itemAt(position) ?: return@setOnItemClickListener)
        }
    }

    private fun animateVisibleRowsWhenReady(
        adapter: LegacyPlaylistRootAdapter,
        editMode: Boolean,
        attempt: Int = 0,
    ) {
        listView.post {
            if (boundEditMode != editMode || pendingEditAnimationMode != editMode) {
                return@post
            }
            if (listView.childCount == 0 && attempt < 4) {
                animateVisibleRowsWhenReady(
                    adapter = adapter,
                    editMode = editMode,
                    attempt = attempt + 1,
                )
                return@post
            }
            pendingEditAnimationMode = null
            adapter.clearForcedStaticEditMode()
            adapter.updateVisibleRows(
                listView = listView,
                animateEditMode = true,
            )
        }
    }
}

private class LegacyPlaylistRootAdapter : BaseAdapter() {
    private var items: List<UserPlaylistSummary> = emptyList()
    private var editMode = false
    private var forcedStaticEditMode: Boolean? = null
    private var selectedIds: Set<String> = emptySet()
    var onRenamePlaylist: (UserPlaylistSummary) -> Unit = {}

    fun forceStaticEditMode(mode: Boolean) {
        forcedStaticEditMode = mode
    }

    fun clearForcedStaticEditMode() {
        forcedStaticEditMode = null
    }

    fun updateItems(
        nextItems: List<UserPlaylistSummary>,
        nextEditMode: Boolean,
        nextSelectedIds: Set<String>,
    ): Boolean {
        val contentChanged = items != nextItems
        val editModeChanged = editMode != nextEditMode
        val selectionChanged = selectedIds != nextSelectedIds
        if (!contentChanged && !editModeChanged && !selectionChanged) {
            return false
        }
        items = nextItems
        editMode = nextEditMode
        selectedIds = nextSelectedIds
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun updateVisibleRows(
        listView: ListView,
        animateEditMode: Boolean,
    ) {
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex
            val item = itemAt(position) ?: continue
            val child = listView.getChildAt(childIndex) ?: continue
            bindRow(
                view = child,
                item = item,
                visualEditMode = editMode,
                animateEditMode = animateEditMode,
            )
        }
    }

    fun itemAt(position: Int): UserPlaylistSummary? = items.getOrNull(position)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_listview, parent, false)
        val item = items[position]
        bindRow(
            view = view,
            item = item,
            visualEditMode = forcedStaticEditMode ?: editMode,
            animateEditMode = false,
        )
        return view
    }

    private fun bindRow(
        view: View,
        item: UserPlaylistSummary,
        visualEditMode: Boolean,
        animateEditMode: Boolean,
    ) {
        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = item.name
            setTextColor(PlaylistPrimaryTextColor)
        }
        val context = view.context
        view.findViewById<TextView>(R.id.listview_item_line_two)?.apply {
            text = context.resources.getQuantityString(
                R.plurals.legacy_playlist_song_count,
                item.songCount,
                item.songCount,
            )
            setTextColor(PlaylistSecondaryTextColor)
        }
        view.findViewById<View>(R.id.iv_right_view)?.setOnClickListener {
            if (editMode) {
                onRenamePlaylist(item)
            }
        }
        view.findViewById<CheckBox>(R.id.cb_del)?.isChecked = item.id in selectedIds
        (view as? EditableLayout)?.bindLegacyEditState(
            enabled = visualEditMode,
            checked = item.id in selectedIds,
            animate = animateEditMode,
        )
    }
}
