package com.smartisan.music.ui.shell

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HeaderViewListAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.replaceQueueAndPlay
import com.smartisan.music.playback.replaceQueueAndPlayShuffled
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.album.AlbumViewMode
import com.smartisan.music.ui.album.buildAlbumSummaries
import com.smartisan.music.ui.widgets.EditableLayout

private const val AlbumSwitchBaseDurationMillis = 150L
private const val AlbumSwitchStaggerMillis = 10L
private const val LegacyAlbumListFooterThreshold = 8
private val LegacyAlbumPrimaryTextColor = Color.rgb(0x35, 0x35, 0x39)
private val LegacyAlbumSecondaryTextColor = Color.rgb(0xa4, 0xa7, 0xac)
private val LegacyAlbumSelectedTextColor = Color.rgb(0xe6, 0x40, 0x40)
private val LegacyAlbumFooterTextColor = Color.rgb(0xbc, 0xbc, 0xbc)

private fun android.content.res.Resources.getDimensionPixelSizeCompatFooterPadding(): Int {
    return getDimensionPixelSize(R.dimen.footer_padding)
}

@Composable
internal fun LegacyPortAlbumPage(
    mediaItems: List<MediaItem>,
    active: Boolean,
    viewMode: AlbumViewMode,
    editMode: Boolean,
    selectedAlbumId: String?,
    selectedAlbumIds: Set<String>,
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    hiddenMediaIds: Set<String>,
    onAlbumSelected: (String, String) -> Unit,
    onAlbumSelectionChange: (String, Boolean) -> Unit,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onTrackMoreClick: (MediaItem) -> Unit,
    artistSettings: ArtistSettings = ArtistSettings(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val visibleSongs = remember(mediaItems, hiddenMediaIds) {
        mediaItems.filterNot { mediaItem -> mediaItem.mediaId in hiddenMediaIds }
    }
    val unknownAlbumTitle = stringResource(R.string.unknown_album)
    val multipleArtistsTitle = stringResource(R.string.many_artist)
    val albums = remember(visibleSongs, unknownAlbumTitle, multipleArtistsTitle, artistSettings) {
        buildAlbumSummaries(
            mediaItems = visibleSongs,
            unknownAlbumTitle = unknownAlbumTitle,
            multipleArtistsTitle = multipleArtistsTitle,
            artistSettings = artistSettings,
        )
    }
    val selectedAlbum = remember(albums, selectedAlbumId) {
        albums.firstOrNull { album -> album.id == selectedAlbumId }
    }
    var currentMediaId by remember(browser) {
        mutableStateOf(browser?.currentMediaItem?.mediaId)
    }
    val switchAnimator = remember { LegacyAlbumViewSwitchAnimator() }

    DisposableEffect(browser) {
        val playbackBrowser = browser ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                currentMediaId = player.currentMediaItem?.mediaId
            }
        }
        playbackBrowser.addListener(listener)
        onDispose {
            playbackBrowser.removeListener(listener)
        }
    }

    LegacyPortPageStackTransition(
        secondaryKey = selectedAlbum,
        modifier = modifier,
        label = "legacy album detail transition",
        predictiveBackProgress = predictiveBackProgress,
        predictiveBackExitConsumed = predictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onPredictiveBackExitConsumedReset,
        primaryContent = {
            LegacyPortAlbumOverviewPage(
                active = active,
                albums = albums,
                currentMediaId = currentMediaId,
                browser = browser,
                viewMode = viewMode,
                editMode = editMode,
                selectedAlbumIds = selectedAlbumIds,
                onAlbumSelected = onAlbumSelected,
                onAlbumSelectionChange = onAlbumSelectionChange,
                switchAnimator = switchAnimator,
                modifier = Modifier.fillMaxSize(),
            )
        },
        secondaryContent = { album ->
            LegacyPortAlbumDetailPage(
                album = album,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onTrackMoreClick = onTrackMoreClick,
                artistSettings = artistSettings,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

@Composable
private fun LegacyPortAlbumOverviewPage(
    active: Boolean,
    albums: List<AlbumSummary>,
    currentMediaId: String?,
    browser: Player?,
    viewMode: AlbumViewMode,
    editMode: Boolean,
    selectedAlbumIds: Set<String>,
    onAlbumSelected: (String, String) -> Unit,
    onAlbumSelectionChange: (String, Boolean) -> Unit,
    switchAnimator: LegacyAlbumViewSwitchAnimator,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            LegacyAlbumRoot(viewContext)
        },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            root.bindPlayActions(
                albums = albums,
                enabled = albums.isNotEmpty() && !editMode,
                onPlay = play@{ shuffle ->
                    val playbackBrowser = browser ?: return@play
                    val albumSongs = albums.flatMap { it.songs }
                    if (albumSongs.isEmpty()) {
                        return@play
                    }
                    if (shuffle) {
                        playbackBrowser.replaceQueueAndPlayShuffled(albumSongs)
                    } else {
                        playbackBrowser.replaceQueueAndPlay(albumSongs)
                    }
                },
            )

            val listAdapter = root.listView.legacyAlbumListAdapter()
                ?: LegacyAlbumListAdapter(root.artworkLoader).also { adapter ->
                    root.listView.adapter = adapter
                }
            val gridAdapter = root.gridView.adapter as? LegacyAlbumGridAdapter
                ?: LegacyAlbumGridAdapter(root.artworkLoader).also { adapter ->
                    root.gridView.adapter = adapter
                }
            val previousEditMode = root.editMode
            val animateEditMode = previousEditMode != null && previousEditMode != editMode
            root.editMode = editMode
            val listContentChanged = listAdapter.updateItems(
                nextItems = albums,
                nextCurrentMediaId = currentMediaId,
                nextEditMode = editMode,
                nextSelectedAlbumIds = selectedAlbumIds,
            )
            val gridContentChanged = gridAdapter.updateItems(
                nextItems = albums,
                nextCurrentMediaId = currentMediaId,
                nextEditMode = editMode,
                nextSelectedAlbumIds = selectedAlbumIds,
            )
            root.bindFooter(albumCount = albums.size)
            if (!listContentChanged) {
                listAdapter.updateVisibleRows(root.listView, animate = animateEditMode)
            }
            if (!gridContentChanged) {
                gridAdapter.updateVisibleRows(root.gridView)
            }
            val listSlideSelectionController = root.listView.legacySlideSelectionController(
                startArea = LegacySlideSelectionStartArea.Checkbox,
            )
            listSlideSelectionController.update(
                enabled = editMode,
                selectedKeys = selectedAlbumIds,
                keyAtPosition = { position ->
                    listAdapter.itemAt(position)?.id
                },
                onSelectionChange = { albumId, selected ->
                    onAlbumSelectionChange(albumId, selected)
                },
            )
            root.listView.setOnTouchListener { _, event ->
                listSlideSelectionController.handleTouch(event)
            }
            val gridSlideSelectionController = root.gridView.legacySlideSelectionController(
                startArea = LegacySlideSelectionStartArea.FullItem,
                activation = LegacySlideSelectionActivation.HorizontalBeforeVertical,
            )
            gridSlideSelectionController.update(
                enabled = editMode,
                selectedKeys = selectedAlbumIds,
                keyAtPosition = { position ->
                    gridAdapter.itemAt(position)?.id
                },
                onSelectionChange = { albumId, selected ->
                    onAlbumSelectionChange(albumId, selected)
                },
                startArea = LegacySlideSelectionStartArea.FullItem,
                activation = LegacySlideSelectionActivation.HorizontalBeforeVertical,
            )
            root.gridView.setOnTouchListener { _, event ->
                gridSlideSelectionController.handleTouch(event)
            }

            root.listView.setOnItemClickListener { _, _, position, _ ->
                val album = listAdapter.itemAt(position) ?: return@setOnItemClickListener
                if (editMode) {
                    onAlbumSelectionChange(album.id, album.id !in selectedAlbumIds)
                    return@setOnItemClickListener
                }
                onAlbumSelected(album.id, album.title)
            }
            root.gridView.setOnItemClickListener { _, _, position, _ ->
                val album = gridAdapter.itemAt(position) ?: return@setOnItemClickListener
                if (editMode) {
                    onAlbumSelectionChange(album.id, album.id !in selectedAlbumIds)
                    return@setOnItemClickListener
                }
                onAlbumSelected(album.id, album.title)
            }

            val previousMode = root.viewMode
            root.viewMode = viewMode
            if (previousMode == null) {
                root.showModeImmediately(viewMode)
            } else if (previousMode != viewMode) {
                switchAnimator.animate(
                    root = root,
                    from = previousMode,
                    to = viewMode,
                )
            }
        },
    )
}

private class LegacyAlbumRoot(context: Context) : LinearLayout(context) {
    val listHost: FrameLayout
    val listView: ListView
    val gridView: GridView
    val artworkLoader = LegacyAlbumArtworkLoader(context)
    private val listFooterView = LegacyAlbumFooterView(context)
    var viewMode: AlbumViewMode? = null
    var editMode: Boolean? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)

        val playContainer = LayoutInflater.from(context)
            .inflate(R.layout.layout_play_container, this, false)
            .apply {
                id = R.id.play_container
            }
        addView(
            playContainer,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            View(context).apply {
                setBackgroundColor(context.getColor(R.color.listview_divider_color))
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.listview_dividerHeight),
            ),
        )

        val content = FrameLayout(context).apply {
            id = R.id.fl_list_tile
            setBackgroundColor(Color.WHITE)
        }
        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        listHost = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            visibility = View.VISIBLE
        }
        content.addView(
            listHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        listView = ListView(context).apply {
            id = R.id.list
            divider = ColorDrawable(context.getColor(R.color.listview_divider_color))
            dividerHeight = resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.WHITE)
            layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.list_anim_layout)
            addFooterView(listFooterView, null, false)
        }
        listHost.addView(
            listView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        gridView = GridView(context).apply {
            id = R.id.listview_grid
            numColumns = resources.getInteger(R.integer.gridview_columns)
            gravity = Gravity.CENTER_HORIZONTAL
            selector = ColorDrawable(Color.TRANSPARENT)
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(Color.WHITE)
            clipChildren = false
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            verticalSpacing = resources.getDimensionPixelSize(R.dimen.gridview_verticalSpacing)
            horizontalSpacing = resources.getDimensionPixelSize(R.dimen.gridview_horizontalSpacing)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.gridview_margin),
                0,
                resources.getDimensionPixelSize(R.dimen.gridview_margin),
                resources.getDimensionPixelSize(R.dimen.gridview_margin),
            )
            clipToPadding = false
            visibility = View.GONE
        }
        content.addView(
            gridView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun bindPlayActions(
        albums: List<AlbumSummary>,
        enabled: Boolean,
        onPlay: (shuffle: Boolean) -> Unit,
    ) {
        findViewById<View>(R.id.play_container)?.apply {
            visibility = if (albums.isEmpty()) View.GONE else View.VISIBLE
            alpha = if (enabled) 1f else 0.22f
        }
        findViewById<View>(R.id.bt_play)?.apply {
            isEnabled = enabled
            setOnClickListener {
                if (enabled) {
                    onPlay(false)
                }
            }
        }
        findViewById<View>(R.id.bt_shuffle)?.apply {
            isEnabled = enabled
            setOnClickListener {
                if (enabled) {
                    onPlay(true)
                }
            }
        }
    }

    fun showModeImmediately(mode: AlbumViewMode) {
        listHost.visibility = if (mode == AlbumViewMode.List) View.VISIBLE else View.GONE
        listHost.alpha = 1f
        listView.alpha = 1f
        listView.visibility = View.VISIBLE
        gridView.alpha = 1f
        gridView.visibility = if (mode == AlbumViewMode.Tile) View.VISIBLE else View.GONE
    }

    fun bindFooter(albumCount: Int) {
        listFooterView.bind(
            albumCount = albumCount,
            visible = albumCount >= LegacyAlbumListFooterThreshold,
        )
    }

    override fun onDetachedFromWindow() {
        artworkLoader.clear()
        super.onDetachedFromWindow()
    }
}

private class LegacyAlbumListAdapter(
    private val artworkLoader: LegacyAlbumArtworkLoader,
) : BaseAdapter() {
    private var items: List<AlbumSummary> = emptyList()
    private var currentMediaId: String? = null
    private var editMode: Boolean = false
    private var selectedAlbumIds: Set<String> = emptySet()

    fun updateItems(
        nextItems: List<AlbumSummary>,
        nextCurrentMediaId: String?,
        nextEditMode: Boolean,
        nextSelectedAlbumIds: Set<String>,
    ): Boolean {
        val contentChanged = items != nextItems
        val stateChanged = currentMediaId != nextCurrentMediaId ||
            editMode != nextEditMode ||
            selectedAlbumIds != nextSelectedAlbumIds
        if (!contentChanged && !stateChanged) {
            return false
        }
        items = nextItems
        currentMediaId = nextCurrentMediaId
        editMode = nextEditMode
        selectedAlbumIds = nextSelectedAlbumIds
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun itemAt(position: Int): AlbumSummary? = items.getOrNull(position)

    fun updateVisibleRows(listView: ListView, animate: Boolean) {
        for (childIndex in 0 until listView.childCount) {
            val position = listView.firstVisiblePosition + childIndex
            val album = itemAt(position) ?: continue
            val child = listView.getChildAt(childIndex) ?: continue
            val selected = album.songs.any { it.mediaId == currentMediaId }
            child.findViewById<TextView>(R.id.listview_item_line_one)?.setTextColor(
                if (selected) LegacyAlbumSelectedTextColor else LegacyAlbumPrimaryTextColor,
            )
            (child as? EditableLayout)?.bindLegacyEditState(
                enabled = editMode,
                checked = album.id in selectedAlbumIds,
                animate = animate,
            )
        }
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_album_layout, parent, false)
        val album = items[position]
        view.tag = album.id
        view.findViewById<ImageView>(R.id.listview_item_image)?.apply {
            setTag(R.string.add_track, position)
            bindLegacyAlbumArtwork(
                album = album,
                fallbackRes = R.drawable.noalbumcover_120,
                sizePx = parent.resources.getDimensionPixelSize(R.dimen.album_list_item_image_width),
                artworkLoader = artworkLoader,
            )
        }
        view.findViewById<View>(R.id.iv_mask_albumcover)?.setBackgroundResource(R.drawable.mask_albumcover_list)
        val selected = album.songs.any { it.mediaId == currentMediaId }
        view.findViewById<TextView>(R.id.listview_item_line_one)?.apply {
            text = album.title
            setTextColor(if (selected) LegacyAlbumSelectedTextColor else LegacyAlbumPrimaryTextColor)
        }
        view.findViewById<TextView>(R.id.listview_item_line_two)?.apply {
            text = album.artist
            setTextColor(LegacyAlbumSecondaryTextColor)
        }
        (view as? EditableLayout)?.bindLegacyEditState(
            enabled = editMode,
            checked = album.id in selectedAlbumIds,
            animate = false,
        )
        return view
    }
}

private class LegacyAlbumGridAdapter(
    private val artworkLoader: LegacyAlbumArtworkLoader,
) : BaseAdapter() {
    private var items: List<AlbumSummary> = emptyList()
    private var currentMediaId: String? = null
    private var editMode: Boolean = false
    private var selectedAlbumIds: Set<String> = emptySet()

    fun updateItems(
        nextItems: List<AlbumSummary>,
        nextCurrentMediaId: String?,
        nextEditMode: Boolean,
        nextSelectedAlbumIds: Set<String>,
    ): Boolean {
        val contentChanged = items != nextItems
        val stateChanged = currentMediaId != nextCurrentMediaId ||
            editMode != nextEditMode ||
            selectedAlbumIds != nextSelectedAlbumIds
        if (!contentChanged && !stateChanged) {
            return false
        }
        items = nextItems
        currentMediaId = nextCurrentMediaId
        editMode = nextEditMode
        selectedAlbumIds = nextSelectedAlbumIds
        if (contentChanged) {
            notifyDataSetChanged()
        }
        return contentChanged
    }

    fun itemAt(position: Int): AlbumSummary? = items.getOrNull(position)

    fun updateVisibleRows(gridView: GridView) {
        for (childIndex in 0 until gridView.childCount) {
            val position = gridView.firstVisiblePosition + childIndex
            val album = itemAt(position) ?: continue
            val child = gridView.getChildAt(childIndex) ?: continue
            bindState(child, album)
        }
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: createGridItem(parent)
        val album = items[position]
        view.tag = album.id
        view.findViewById<LegacyAlbumTileImageView>(R.id.gridview_image)?.apply {
            setTag(R.string.add_track, position)
            setMaskEnabled(true)
            bindLegacyAlbumArtwork(
                album = album,
                fallbackRes = R.drawable.noalbumcover_220,
                sizePx = parent.resources.getDimensionPixelSize(R.dimen.gridview_item_ccontainer_height),
                artworkLoader = artworkLoader,
            )
        }
        bindState(view, album)
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        return view
    }

    private fun bindState(view: View, album: AlbumSummary) {
        view.findViewById<TextView>(R.id.tv_album_name)?.apply {
            text = album.title
            setTextColor(if (album.songs.any { it.mediaId == currentMediaId }) LegacyAlbumSelectedTextColor else Color.BLACK)
            visibility = View.VISIBLE
        }
        view.findViewById<ImageView>(R.id.empty_selected_view)?.apply {
            visibility = if (editMode) View.VISIBLE else View.GONE
            alpha = if (editMode) 1f else 0f
        }
        view.findViewById<ImageView>(R.id.check_view)?.apply {
            visibility = if (editMode) View.VISIBLE else View.GONE
            alpha = if (album.id in selectedAlbumIds) 1f else 0f
        }
    }

    private fun createGridItem(parent: ViewGroup): View {
        val context = parent.context
        val coverSize = parent.resources.getDimensionPixelSize(R.dimen.gridview_item_ccontainer_height)
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            isEnabled = true
            setDuplicateParentStateEnabled(true)
            setPadding(0, parent.resources.getDimensionPixelSize(R.dimen.gridview_padding_top2), 0, 0)
            layoutParams = AbsListView.LayoutParams(
                coverSize,
                AbsListView.LayoutParams.WRAP_CONTENT,
            )
            addView(
                FrameLayout(context).apply {
                    id = R.id.edit_zone
                    addView(
                        LegacyAlbumTileImageView(context).apply {
                            id = R.id.gridview_image
                        },
                        FrameLayout.LayoutParams(coverSize, coverSize),
                    )
                    addView(
                        ImageView(context).apply {
                            id = R.id.empty_selected_view
                            scaleType = ImageView.ScaleType.FIT_XY
                            setImageResource(R.drawable.albums_selected_large_empty)
                        },
                        FrameLayout.LayoutParams(coverSize, coverSize),
                    )
                    addView(
                        ImageView(context).apply {
                            id = R.id.check_view
                            scaleType = ImageView.ScaleType.FIT_XY
                            setImageResource(R.drawable.albums_selected_large)
                        },
                        FrameLayout.LayoutParams(coverSize, coverSize),
                    )
                },
                LinearLayout.LayoutParams(coverSize, coverSize),
            )
            addView(
                TextView(context).apply {
                    id = R.id.tv_album_name
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setSingleLine(true)
                    textSize = 13f
                    setTextColor(Color.BLACK)
                },
                LinearLayout.LayoutParams(coverSize, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }
    }

}

private class LegacyAlbumFooterView(context: Context) : LinearLayout(context) {
    private val content = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(LegacyAlbumFooterTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.footer_text_size))
        setBackgroundColor(Color.WHITE)
        setPadding(0, resources.getDimensionPixelSizeCompatFooterPadding(), 0, resources.getDimensionPixelSizeCompatFooterPadding())
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        bind(albumCount = 0, visible = false)
    }

    fun bind(albumCount: Int, visible: Boolean) {
        content.text = context.resources.getQuantityString(
            R.plurals.legacy_album_count,
            albumCount,
            albumCount,
        )
        content.visibility = if (visible) View.VISIBLE else View.GONE
    }
}

private class LegacyAlbumViewSwitchAnimator {
    private var animator: Animator? = null
    private val interpolator = DecelerateInterpolator()
    private var generation: Int = 0

    fun animate(
        root: LegacyAlbumRoot,
        from: AlbumViewMode,
        to: AlbumViewMode,
    ) {
        generation += 1
        val currentGen = generation
        animator?.cancel()
        animator = null
        if (from == AlbumViewMode.List && to == AlbumViewMode.Tile) {
            animateListToGrid(root, currentGen)
        } else if (from == AlbumViewMode.Tile && to == AlbumViewMode.List) {
            animateGridToList(root, currentGen)
        } else {
            root.showModeImmediately(to)
        }
    }

    private fun animateListToGrid(root: LegacyAlbumRoot, gen: Int) {
        val listHost = root.listHost
        val listView = root.listView
        val gridView = root.gridView
        val firstVisiblePosition = listView.firstVisiblePosition
        listHost.animate().cancel()
        listHost.visibility = View.GONE
        listHost.alpha = 1f
        gridView.alpha = 1f
        gridView.layoutAnimation = legacyAlbumGridFadeLayoutAnimation()
        gridView.setSelection(firstVisiblePosition)
        gridView.visibility = View.VISIBLE
        gridView.post {
            if (gen != generation) return@post
            resetGridChildren(gridView)
            gridView.scheduleLayoutAnimation()
        }
    }

    private fun legacyAlbumGridFadeLayoutAnimation(): LayoutAnimationController {
        val animation = AnimationSet(true).apply {
            addAnimation(
                AlphaAnimation(0f, 1f).apply {
                    duration = 300L
                },
            )
        }
        return LayoutAnimationController(animation, 0.133f).apply {
            order = LayoutAnimationController.ORDER_NORMAL
        }
    }

    private fun animateGridToList(root: LegacyAlbumRoot, gen: Int) {
        val listHost = root.listHost
        val listView = root.listView
        val gridView = root.gridView
        val firstVisiblePosition = gridView.firstVisiblePosition
        listHost.animate().cancel()
        listHost.alpha = 0f
        listHost.visibility = View.VISIBLE
        listView.alpha = 1f
        listView.visibility = View.VISIBLE
        afterNextLayout(listView, gen) {
            if (gen != generation) return@afterNextLayout
            val animators = mutableListOf<Animator>()
            val hiddenListTargets = mutableSetOf<View>()
            val gridFirstVisiblePosition = gridView.firstVisiblePosition
            val listFirstPosition = listView.firstVisiblePosition
            val listLastPosition = listView.lastVisiblePosition
            for (index in 0 until gridView.childCount) {
                val gridChild = gridView.getChildAt(index) ?: continue
                val gridCover = gridChild.findViewById<View>(R.id.gridview_image) ?: continue
                val position = gridCover.getTag(R.string.add_track) as? Int ?: continue
                val animationOrder = gridAnimationOrder(
                    position = position,
                    firstVisiblePosition = gridFirstVisiblePosition,
                    fallbackIndex = index,
                )
                val listCover = listView.findCoverByPosition(position)
                gridChild.prepareForAlbumSwitch()
                val target = gridChild.gridToListTarget(
                    listView = listView,
                    listCover = listCover,
                    gridView = gridView,
                    gridCover = gridCover,
                    targetPosition = position,
                    listFirstPosition = listFirstPosition,
                    listLastPosition = listLastPosition,
                )
                animators += ObjectAnimator.ofPropertyValuesHolder(
                    gridChild,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, target.translationX),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, target.translationY),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, target.scaleX),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, target.scaleY),
                ).apply {
                    duration = AlbumSwitchBaseDurationMillis
                    startDelay = animationOrder * AlbumSwitchStaggerMillis
                    interpolator = this@LegacyAlbumViewSwitchAnimator.interpolator
                    addListener(
                        object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                listCover?.parentView()?.let { listTarget ->
                                    listTarget.visibility = View.INVISIBLE
                                    hiddenListTargets += listTarget
                                }
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                listCover?.parentView()?.visibility = View.VISIBLE
                            }

                            override fun onAnimationCancel(animation: Animator) {
                                listCover?.parentView()?.visibility = View.VISIBLE
                            }
                        },
                    )
                }
            }
            if (animators.isEmpty()) {
                listHost.alpha = 1f
                gridView.visibility = View.GONE
                resetGridChildren(gridView)
                return@afterNextLayout
            }
            val nextAnimator = AnimatorSet().apply {
                playTogether(animators)
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            finishGridToList(listHost, gridView, hiddenListTargets)
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            finishGridToList(listHost, gridView, hiddenListTargets)
                        }
                    },
                )
            }
            animator = nextAnimator
            nextAnimator.start()
        }
        listView.setSelectionFromTop(firstVisiblePosition, 0)
        listView.requestLayout()
    }

    private fun afterNextLayout(
        view: View,
        gen: Int,
        block: () -> Unit,
    ) {
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
                if (gen == generation) {
                    block()
                }
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun finishGridToList(
        listHost: View,
        gridView: GridView,
        hiddenListTargets: Set<View>,
    ) {
        hiddenListTargets.forEach { target ->
            target.visibility = View.VISIBLE
        }
        listHost.animate().cancel()
        listHost.alpha = 1f
        gridView.visibility = View.GONE
        resetGridChildren(gridView)
    }

    private fun resetGridChildren(gridView: GridView) {
        for (index in 0 until gridView.childCount) {
            gridView.getChildAt(index)?.apply {
                translationX = 0f
                translationY = 0f
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                setLayerType(View.LAYER_TYPE_NONE, null)
                findViewById<View>(R.id.tv_album_name)?.visibility = View.VISIBLE
            }
        }
    }
}

private fun gridAnimationOrder(
    position: Int,
    firstVisiblePosition: Int,
    fallbackIndex: Int,
): Long {
    val order = position - firstVisiblePosition
    return if (order >= 0) order.toLong() else fallbackIndex.toLong()
}

private fun ListView.findCoverByPosition(position: Int): View? {
    for (index in 0 until childCount) {
        val child = getChildAt(index) ?: continue
        val cover = child.findViewById<View>(R.id.listview_item_image) ?: continue
        if (cover.getTag(R.string.add_track) == position) {
            return cover
        }
    }
    return null
}

private fun ListView.legacyAlbumListAdapter(): LegacyAlbumListAdapter? {
    return when (val currentAdapter = adapter) {
        is LegacyAlbumListAdapter -> currentAdapter
        is HeaderViewListAdapter -> currentAdapter.wrappedAdapter as? LegacyAlbumListAdapter
        else -> null
    }
}

private fun View.prepareForAlbumSwitch() {
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    pivotX = 0f
    pivotY = 0f
    findViewById<View>(R.id.tv_album_name)?.visibility = View.INVISIBLE
}

private data class AlbumSwitchTarget(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
)

private fun View.gridToListTarget(
    listView: ListView,
    listCover: View?,
    gridView: GridView,
    gridCover: View,
    targetPosition: Int,
    listFirstPosition: Int,
    listLastPosition: Int,
): AlbumSwitchTarget {
    val gridInnerWidth = (gridCover.width - gridCover.paddingTop * 2).coerceAtLeast(1)
    val gridInnerHeight = (gridCover.height - gridCover.paddingTop * 2).coerceAtLeast(1)
    val sampleListCover = listCover ?: listView.getChildAt(0)?.findViewById<View>(R.id.listview_item_image)
    val scaleX = (sampleListCover?.width ?: gridInnerWidth).toFloat() / gridInnerWidth
    val scaleY = (sampleListCover?.height ?: gridInnerHeight).toFloat() / gridInnerHeight
    if (listCover != null && targetPosition in listFirstPosition..listLastPosition) {
        val gridChildX = leftRelativeTo(gridView)
        val gridChildY = topRelativeTo(gridView)
        val gridCoverX = gridCover.leftRelativeTo(gridView) + gridCover.paddingTop
        val gridCoverY = gridCover.topRelativeTo(gridView) + gridCover.paddingTop
        val listCoverX = listCover.leftRelativeTo(listView)
        val listCoverY = listCover.topRelativeTo(listView)
        return AlbumSwitchTarget(
            translationX = listCoverX - (gridChildX + (gridCoverX - gridChildX) * scaleX),
            translationY = listCoverY - (gridChildY + (gridCoverY - gridChildY) * scaleY),
            scaleX = scaleX,
            scaleY = scaleY,
        )
    }
    return AlbumSwitchTarget(
        translationX = -left.toFloat(),
        translationY = when {
            targetPosition > listLastPosition -> listView.lastChildTop().toFloat()
            targetPosition < listFirstPosition -> -listView.lastChildTop() / 2f
            else -> -top.toFloat()
        },
        scaleX = scaleX,
        scaleY = scaleY,
    )
}

private fun ListView.lastChildTop(): Int {
    return getChildAt(childCount - 1)?.top ?: 0
}

private fun View.parentView(): View? = parent as? View

private fun View.leftRelativeTo(ancestor: View): Float {
    var result = left.toFloat()
    var current = parent as? View
    while (current != null && current !== ancestor) {
        result += current.left - current.scrollX
        current = current.parent as? View
    }
    return result
}

private fun View.topRelativeTo(ancestor: View): Float {
    var result = top.toFloat()
    var current = parent as? View
    while (current != null && current !== ancestor) {
        result += current.top - current.scrollY
        current = current.parent as? View
    }
    return result
}
