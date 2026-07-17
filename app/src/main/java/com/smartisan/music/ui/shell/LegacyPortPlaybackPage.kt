package com.smartisan.music.ui.shell

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.RatingBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.SessionResult
import com.smartisan.music.R
import com.smartisan.music.data.favorite.FavoriteSongsRepository
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.playback.LocalPlaybackController
import com.smartisan.music.playback.await
import com.smartisan.music.playback.setTrackRating
import com.smartisan.music.ui.playback.PlaybackScreen
import com.smartisan.music.ui.shell.songs.legacyRating
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.smartisan.music.ui.widgets.legacy.TitleBar

@Composable
internal fun LegacyPortPlaybackPage(
    playbackSettings: PlaybackSettings,
    ratingOverrides: Map<String, Int>,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onTrackRatingChanged: (String, Int) -> Unit,
    onFavoriteToggle: (MediaItem) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current
    val titleState = rememberLegacyPlaybackTitleState()
    val favoriteRepository = remember(context.applicationContext) {
        FavoriteSongsRepository.getInstance(context.applicationContext)
    }
    val favoriteIds by favoriteRepository.observeFavoriteIds().collectAsState(initial = emptySet())
    var pendingRatingRequests by remember { mutableStateOf(emptyMap<String, Int>()) }
    var queueSnapshot by remember(controller, context, favoriteIds, ratingOverrides) {
        mutableStateOf(
            controller.toLegacyPlaybackQueueSnapshot(
                context = context,
                favoriteIds = favoriteIds,
                ratingOverrides = ratingOverrides,
            ),
        )
    }
    var queueVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val titleContentHeight = dimensionResource(R.dimen.titlebar_height)
    val density = LocalDensity.current
    val statusBarHeight = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val titleTopPadding = statusBarHeight + titleContentHeight

    DisposableEffect(controller, context, favoriteIds, ratingOverrides) {
        val playbackController = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                queueSnapshot = player.toLegacyPlaybackQueueSnapshot(
                    context = context,
                    favoriteIds = favoriteIds,
                    ratingOverrides = ratingOverrides,
                )
            }
        }
        playbackController.addListener(listener)
        queueSnapshot = playbackController.toLegacyPlaybackQueueSnapshot(
            context = context,
            favoriteIds = favoriteIds,
            ratingOverrides = ratingOverrides,
        )
        onDispose {
            playbackController.removeListener(listener)
        }
    }

    BackHandler(enabled = queueVisible) {
        queueVisible = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        val screenHeightPx = with(density) { maxHeight.roundToPx() }
        val playerOffsetY by animateFloatAsState(
            targetValue = if (queueVisible) screenHeightPx.toFloat() else 0f,
            animationSpec = tween(
                durationMillis = LegacyQueueRevealDurationMillis,
                easing = { fraction ->
                    val inverse = 1f - fraction
                    1f - inverse * inverse * inverse
                },
            ),
            label = "legacy playback queue reveal",
        )

        LegacyPlaybackQueueLayer(
            snapshot = queueSnapshot,
            onItemClick = { queueIndex ->
                controller?.seekToDefaultPosition(queueIndex)
            },
            onFavoriteCurrentClick = {
                queueSnapshot.current?.mediaItem?.let(onFavoriteToggle)
            },
            onCurrentRatingChanged = { mediaId, score ->
                if (mediaId.isNotBlank()) {
                    val playbackController = controller
                    val previousScore = queueSnapshot.current
                        ?.takeIf { current -> current.mediaId == mediaId }
                        ?.score
                        ?: ratingOverrides[mediaId]
                        ?: 0
                    if (playbackController != null) {
                        pendingRatingRequests = pendingRatingRequests + (mediaId to score)
                        onTrackRatingChanged(mediaId, score)
                        queueSnapshot = queueSnapshot.withCurrentRating(mediaId, score)
                        scope.launch {
                            val successful = runCatching {
                                playbackController
                                    .setTrackRating(mediaId, score)
                                    .await(context)
                                    .resultCode == SessionResult.RESULT_SUCCESS
                            }.getOrDefault(false)
                            if (pendingRatingRequests[mediaId] != score) {
                                return@launch
                            }
                            pendingRatingRequests = pendingRatingRequests - mediaId
                            if (!successful) {
                                onTrackRatingChanged(mediaId, previousScore)
                                queueSnapshot = queueSnapshot.withCurrentRating(mediaId, previousScore)
                            }
                        }
                    }
                }
            },
            onClearUpcomingClick = {
                val playbackController = controller
                if (playbackController != null) {
                    val currentIndex = playbackController.currentMediaItemIndex
                    val upcomingIndexes = playbackController
                        .toLegacyPlaybackQueueSnapshot(
                            context = context,
                            favoriteIds = favoriteIds,
                            ratingOverrides = ratingOverrides,
                        )
                        .upcoming
                        .map { track -> track.queueIndex }
                        .filter { index -> index >= 0 && index != currentIndex }
                        .distinct()
                        .sortedDescending()
                    upcomingIndexes.forEach { index ->
                        if (index in 0 until playbackController.mediaItemCount) {
                            playbackController.removeMediaItem(index)
                        }
                    }
                }
            },
            onMoveRequest = { fromIndex, toIndex ->
                val playbackController = controller
                if (
                    playbackController != null &&
                    fromIndex != toIndex &&
                    playbackController.canReorderUpcomingQueue
                ) {
                    val itemCount = playbackController.mediaItemCount
                    if (fromIndex in 0 until itemCount && toIndex in 0 until itemCount) {
                        playbackController.moveMediaItem(fromIndex, toIndex)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarHeight)
                .zIndex(0f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .graphicsLayer {
                    translationY = playerOffsetY
                },
        ) {
            PlaybackScreen(
                playbackSettings = playbackSettings,
                onRequestAddToPlaylist = onRequestAddToPlaylist,
                onRequestAddToQueue = onRequestAddToQueue,
                onScratchEnabledChange = onScratchEnabledChange,
                onFavoriteToggle = onFavoriteToggle,
                onCollapse = onCollapse,
                showTopBar = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = titleTopPadding),
            )
        }
        LegacyPortTitleBarShadow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = titleTopPadding)
                .height(dimensionResource(R.dimen.title_bar_shadow_height))
                .zIndex(2f),
        )
        LegacyPortPlaybackTitleBar(
            title = titleState.title,
            artist = titleState.artist,
            queueVisible = queueVisible,
            onCollapse = onCollapse,
            onQueueClick = {
                queueVisible = !queueVisible
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(3f),
        )
    }
}

@Composable
private fun LegacyPortPlaybackTitleBar(
    title: String,
    artist: String,
    queueVisible: Boolean,
    onCollapse: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleContentHeight = dimensionResource(R.dimen.titlebar_height)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ComposeColor.White),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars),
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleContentHeight),
            factory = { context ->
                TitleBar(context).apply {
                    setTitleBarHeight(resources.getDimensionPixelSize(R.dimen.titlebar_height))
                    setShadowVisible(false)
                    setBackgroundColor(Color.WHITE)
                }
            },
            update = { titleBar ->
                titleBar.setupLegacyPlaybackTitleBar(
                    title = title,
                    artist = artist,
                    queueVisible = queueVisible,
                    onCollapse = onCollapse,
                    onQueueClick = onQueueClick,
                )
            },
        )
    }
}

private fun TitleBar.setupLegacyPlaybackTitleBar(
    title: String,
    artist: String,
    queueVisible: Boolean,
    onCollapse: () -> Unit,
    onQueueClick: () -> Unit,
) {
    setTitleBarHeight(resources.getDimensionPixelSize(R.dimen.titlebar_height))
    setShadowVisible(false)
    setBackgroundColor(Color.WHITE)
    removeAllLeftViews()
    removeAllRightViews()
    if (queueVisible) {
        setCenterText(context.getString(R.string.playlist_title))
    } else {
        setCenterView(
            PlaybackCenterTitle(context).apply {
                setTitle(title)
                setSubTitle(artist)
            },
        )
    }
    addLeftImageView(R.drawable.btn_current_playing_back_selector).apply {
        contentDescription = context.getString(R.string.playback_left_btn_content_description)
        setOnClickListener {
            onCollapse()
        }
    }
    addRightImageView(R.drawable.btn_current_playing_check_selector).apply {
        contentDescription = context.getString(
            if (queueVisible) {
                R.string.playqueue_btn_hide_content_description
            } else {
                R.string.playqueue_btn_show_content_description
            },
        )
        setOnClickListener {
            onQueueClick()
        }
    }
}

@Composable
private fun LegacyPlaybackQueueLayer(
    snapshot: LegacyPlaybackQueueSnapshot,
    onItemClick: (Int) -> Unit,
    onFavoriteCurrentClick: () -> Unit,
    onCurrentRatingChanged: (String, Int) -> Unit,
    onClearUpcomingClick: () -> Unit,
    onMoveRequest: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LegacyPlaybackQueueView(context)
        },
        update = { view ->
            view.bind(
                snapshot = snapshot,
                callbacks = LegacyPlaybackQueueCallbacks(
                    onItemClick = onItemClick,
                    onFavoriteCurrentClick = onFavoriteCurrentClick,
                    onCurrentRatingChanged = onCurrentRatingChanged,
                    onClearUpcomingClick = onClearUpcomingClick,
                    onMoveRequest = onMoveRequest,
                ),
            )
        },
    )
}

@Composable
private fun rememberLegacyPlaybackTitleState(): LegacyPlaybackTitleState {
    val context = LocalContext.current
    val controller = LocalPlaybackController.current
    var titleState by remember(controller, context) {
        mutableStateOf(controller.toLegacyPlaybackTitleState(context))
    }

    DisposableEffect(controller, context) {
        val playbackController = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                titleState = player.toLegacyPlaybackTitleState(context)
            }
        }
        playbackController.addListener(listener)
        titleState = playbackController.toLegacyPlaybackTitleState(context)
        onDispose {
            playbackController.removeListener(listener)
        }
    }

    return titleState
}

private data class LegacyPlaybackTitleState(
    val title: String,
    val artist: String,
)

private data class LegacyPlaybackQueueSnapshot(
    val history: List<LegacyPlaybackQueueTrack> = emptyList(),
    val current: LegacyPlaybackQueueTrack? = null,
    val upcoming: List<LegacyPlaybackQueueTrack> = emptyList(),
    val isCurrentFavorite: Boolean = false,
    val reorderEnabled: Boolean = true,
)

private data class LegacyPlaybackQueueTrack(
    val queueIndex: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val score: Int,
    val mediaItem: MediaItem,
)

private data class LegacyPlaybackQueueCallbacks(
    val onItemClick: (Int) -> Unit,
    val onFavoriteCurrentClick: () -> Unit,
    val onCurrentRatingChanged: (String, Int) -> Unit,
    val onClearUpcomingClick: () -> Unit,
    val onMoveRequest: (Int, Int) -> Unit,
)

private fun Player?.toLegacyPlaybackTitleState(context: Context): LegacyPlaybackTitleState {
    val metadata = this?.currentMediaItem?.mediaMetadata
    val title = metadata?.displayTitle?.toString()?.takeIf(String::isNotBlank)
        ?: metadata?.title?.toString()?.takeIf(String::isNotBlank)
        ?: context.getString(R.string.unknown_song_title)
    val artist = metadata?.artist?.toString()
        ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        ?: metadata?.albumArtist?.toString()
            ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        ?: ""
    return LegacyPlaybackTitleState(title = title, artist = artist)
}

private fun Player?.toLegacyPlaybackQueueSnapshot(
    context: Context,
    favoriteIds: Set<String>,
    ratingOverrides: Map<String, Int>,
): LegacyPlaybackQueueSnapshot {
    val player = this ?: return LegacyPlaybackQueueSnapshot()
    val itemCount = player.mediaItemCount
    if (itemCount <= 0) {
        val current = player.currentMediaItem?.toLegacyPlaybackQueueTrack(
            context = context,
            queueIndex = 0,
            ratingOverrides = ratingOverrides,
        )
        return LegacyPlaybackQueueSnapshot(
            current = current,
            isCurrentFavorite = current?.mediaId?.let(favoriteIds::contains) == true,
        )
    }
    val currentIndex = player.currentMediaItemIndex.takeIf { it in 0 until itemCount } ?: -1
    val tracks = (0 until itemCount).mapNotNull { index ->
        runCatching {
            player.getMediaItemAt(index).toLegacyPlaybackQueueTrack(
                context = context,
                queueIndex = index,
                ratingOverrides = ratingOverrides,
            )
        }.getOrNull()
    }
    val current = tracks.firstOrNull { it.queueIndex == currentIndex }
        ?: player.currentMediaItem?.toLegacyPlaybackQueueTrack(
            context = context,
            queueIndex = currentIndex.coerceAtLeast(0),
            ratingOverrides = ratingOverrides,
        )
    return LegacyPlaybackQueueSnapshot(
        history = tracks.filter { it.queueIndex < currentIndex }.takeLast(LegacyQueueHistoryLimit),
        current = current,
        upcoming = player.toLegacyUpcomingQueueTracks(
            context = context,
            currentIndex = currentIndex,
            ratingOverrides = ratingOverrides,
        ),
        isCurrentFavorite = current?.mediaId?.let(favoriteIds::contains) == true,
        reorderEnabled = player.canReorderUpcomingQueue,
    )
}

private val Player.canReorderUpcomingQueue: Boolean
    get() = !shuffleModeEnabled && repeatMode != Player.REPEAT_MODE_ALL

private fun Player.toLegacyUpcomingQueueTracks(
    context: Context,
    currentIndex: Int,
    ratingOverrides: Map<String, Int>,
): List<LegacyPlaybackQueueTrack> {
    if (currentIndex !in 0 until mediaItemCount || mediaItemCount <= 1 || currentTimeline.isEmpty) {
        return emptyList()
    }

    val timeline = currentTimeline
    val effectiveRepeatMode = repeatMode.takeUnless { it == Player.REPEAT_MODE_ONE }
        ?: Player.REPEAT_MODE_OFF
    val visitedIndexes = mutableSetOf(currentIndex)
    return buildList {
        var nextIndex = timeline.getNextWindowIndex(
            currentIndex,
            effectiveRepeatMode,
            shuffleModeEnabled,
        )
        while (
            nextIndex != C.INDEX_UNSET &&
            nextIndex in 0 until mediaItemCount &&
            nextIndex !in visitedIndexes
        ) {
            visitedIndexes += nextIndex
            val track = runCatching {
                getMediaItemAt(nextIndex).toLegacyPlaybackQueueTrack(
                    context = context,
                    queueIndex = nextIndex,
                    ratingOverrides = ratingOverrides,
                )
            }.getOrNull()
            if (track != null) {
                add(track)
            }
            nextIndex = timeline.getNextWindowIndex(
                nextIndex,
                effectiveRepeatMode,
                shuffleModeEnabled,
            )
        }
    }
}

private fun MediaItem.toLegacyPlaybackQueueTrack(
    context: Context,
    queueIndex: Int,
    ratingOverrides: Map<String, Int>,
): LegacyPlaybackQueueTrack {
    val metadata = mediaMetadata
    val title = metadata.displayTitle?.toString()?.takeIf(String::isNotBlank)
        ?: metadata.title?.toString()?.takeIf(String::isNotBlank)
        ?: context.getString(R.string.unknown_song_title)
    val artist = metadata.artist?.toString()
        ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        ?: metadata.albumArtist?.toString()
            ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        ?: ""
    return LegacyPlaybackQueueTrack(
        queueIndex = queueIndex,
        mediaId = mediaId,
        title = title,
        artist = artist,
        score = ratingOverrides[mediaId] ?: legacyRating().toInt(),
        mediaItem = this,
    )
}

private fun LegacyPlaybackQueueSnapshot.withCurrentRating(
    mediaId: String,
    score: Int,
): LegacyPlaybackQueueSnapshot {
    val currentTrack = current ?: return this
    if (currentTrack.mediaId != mediaId) {
        return this
    }
    return copy(
        current = currentTrack.copy(score = score.coerceIn(0, 5)),
    )
}

private class LegacyPlaybackQueueView(context: Context) : FrameLayout(context) {
    private val queueAdapter = LegacyPlaybackQueueAdapter(context)
    private val listView: ListView
    private val dragController: LegacyListDragController<LegacyPlaybackQueueTrack>
    private var callbacks = LegacyPlaybackQueueCallbacks(
        onItemClick = {},
        onFavoriteCurrentClick = {},
        onCurrentRatingChanged = { _, _ -> },
        onClearUpcomingClick = {},
        onMoveRequest = { _, _ -> },
    )

    init {
        clipChildren = false
        clipToPadding = false
        LayoutInflater.from(context).inflate(R.layout.legacy_now_playing_layout, this, true)
        listView = findViewById<ListView>(R.id.list).apply {
            clipChildren = false
            clipToPadding = false
            adapter = queueAdapter
            setOnTouchListener { _, event ->
                dragController.handleListTouch(event)
            }
            setOnItemClickListener { _, _, position, _ ->
                queueAdapter.trackAt(position)?.let { track ->
                    this@LegacyPlaybackQueueView.callbacks.onItemClick(track.queueIndex)
                }
            }
        }
        dragController = LegacyListDragController(
            context = context,
            hostView = this,
            listView = listView,
            adapter = queueAdapter,
            onMoveCommitted = { source, target, _, _ ->
                callbacks.onMoveRequest(source.queueIndex, target.queueIndex)
            },
        )
    }

    fun bind(
        snapshot: LegacyPlaybackQueueSnapshot,
        callbacks: LegacyPlaybackQueueCallbacks,
    ) {
        this.callbacks = callbacks
        queueAdapter.bind(snapshot, callbacks)
    }

    override fun onDetachedFromWindow() {
        queueAdapter.clear()
        super.onDetachedFromWindow()
    }

}

private class LegacyPlaybackQueueAdapter(
    context: Context,
) : BaseAdapter(), LegacyListDragAdapter<LegacyPlaybackQueueTrack> {
    private val inflater = LayoutInflater.from(context)
    private val appContext = context.applicationContext
    private val artworkLoader = LegacyAlbumArtworkLoader(context)
    private var rows: List<LegacyPlaybackQueueRow> = emptyList()
    private var callbacks = LegacyPlaybackQueueCallbacks(
        onItemClick = {},
        onFavoriteCurrentClick = {},
        onCurrentRatingChanged = { _, _ -> },
        onClearUpcomingClick = {},
        onMoveRequest = { _, _ -> },
    )
    private var currentFavorite = false
    private var reorderEnabled = true

    fun bind(
        snapshot: LegacyPlaybackQueueSnapshot,
        callbacks: LegacyPlaybackQueueCallbacks,
    ) {
        this.callbacks = callbacks
        currentFavorite = snapshot.isCurrentFavorite
        reorderEnabled = snapshot.reorderEnabled
        rows = buildList {
            if (snapshot.history.isNotEmpty()) {
                add(LegacyPlaybackQueueRow.Header(appContext.getString(R.string.history_title), false))
                snapshot.history.forEach { track ->
                    add(LegacyPlaybackQueueRow.Track(track, LegacyPlaybackQueueSection.History))
                }
            }
            add(LegacyPlaybackQueueRow.Header(appContext.getString(R.string.playing_title), false))
            snapshot.current?.let { current ->
                add(LegacyPlaybackQueueRow.Track(current, LegacyPlaybackQueueSection.Current))
            }
            add(LegacyPlaybackQueueRow.Header(appContext.getString(R.string.orginal_title), snapshot.upcoming.isNotEmpty()))
            snapshot.upcoming.forEach { track ->
                add(LegacyPlaybackQueueRow.Track(track, LegacyPlaybackQueueSection.Upcoming))
            }
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = rows.size

    override fun getItem(position: Int): Any = rows[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 3

    override fun getItemViewType(position: Int): Int {
        return when (val row = rows[position]) {
            is LegacyPlaybackQueueRow.Header -> 0
            is LegacyPlaybackQueueRow.Track -> if (row.section == LegacyPlaybackQueueSection.Current) 1 else 2
        }
    }

    override fun isEnabled(position: Int): Boolean {
        return rows[position] is LegacyPlaybackQueueRow.Track
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return when (val row = rows[position]) {
            is LegacyPlaybackQueueRow.Header -> getHeaderView(row, convertView, parent)
            is LegacyPlaybackQueueRow.Track -> {
                if (row.section == LegacyPlaybackQueueSection.Current) {
                    getCurrentTrackView(row.track, convertView, parent)
                } else {
                    getNormalTrackView(row, convertView, parent)
                }
            }
        }
    }

    fun clear() {
        artworkLoader.clear()
    }

    override fun reorderableItemAt(position: Int): LegacyPlaybackQueueTrack? {
        if (!reorderEnabled) {
            return null
        }
        return (rows.getOrNull(position) as? LegacyPlaybackQueueRow.Track)
            ?.takeIf { it.section == LegacyPlaybackQueueSection.Upcoming }
            ?.track
    }

    fun reorderableTrackAt(position: Int): LegacyPlaybackQueueTrack? = reorderableItemAt(position)

    fun trackAt(position: Int): LegacyPlaybackQueueTrack? {
        return (rows.getOrNull(position) as? LegacyPlaybackQueueRow.Track)?.track
    }

    fun firstReorderableTrack(): LegacyPlaybackQueueTrack? {
        return rows.asSequence()
            .filterIsInstance<LegacyPlaybackQueueRow.Track>()
            .firstOrNull { it.section == LegacyPlaybackQueueSection.Upcoming }
            ?.track
    }

    fun lastReorderableTrack(): LegacyPlaybackQueueTrack? {
        return rows.asReversed().asSequence()
            .filterIsInstance<LegacyPlaybackQueueRow.Track>()
            .firstOrNull { it.section == LegacyPlaybackQueueSection.Upcoming }
            ?.track
    }

    override fun firstReorderableAdapterPosition(): Int {
        return rows.indexOfFirst { row ->
            row is LegacyPlaybackQueueRow.Track && row.section == LegacyPlaybackQueueSection.Upcoming
        }.takeIf { it >= 0 } ?: ListView.INVALID_POSITION
    }

    override fun lastReorderableAdapterPosition(): Int {
        return rows.indexOfLast { row ->
            row is LegacyPlaybackQueueRow.Track && row.section == LegacyPlaybackQueueSection.Upcoming
        }.takeIf { it >= 0 } ?: ListView.INVALID_POSITION
    }

    override fun movePreviewRow(fromPosition: Int, toPosition: Int) {
        if (
            fromPosition == toPosition ||
            reorderableItemAt(fromPosition) == null ||
            reorderableItemAt(toPosition) == null
        ) {
            return
        }
        rows = rows.toMutableList().apply {
            val row = removeAt(fromPosition)
            add(toPosition, row)
        }
        notifyDataSetChanged()
    }

    private fun getHeaderView(
        row: LegacyPlaybackQueueRow.Header,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val view = convertView ?: inflater.inflate(R.layout.legacy_now_playing_header_layout, parent, false)
        view.layoutParams = AbsListView.LayoutParams(
            AbsListView.LayoutParams.MATCH_PARENT,
            view.resources.getDimensionPixelSize(R.dimen.now_playing_header_height),
        )
        view.setBackgroundResource(R.drawable.list_title_bg)
        view.findViewById<TextView>(R.id.header_name).text = row.title
        view.findViewById<ImageButton>(R.id.clear_btn).apply {
            visibility = if (row.clearable) View.VISIBLE else View.GONE
            setOnClickListener {
                callbacks.onClearUpcomingClick()
            }
        }
        return view
    }

    private fun getCurrentTrackView(
        track: LegacyPlaybackQueueTrack,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val view = convertView ?: inflater.inflate(R.layout.legacy_playing_now_item, parent, false)
        view.setBackgroundResource(R.drawable.playing_queue_item_selector)
        bindTrackText(view, track)
        bindArtwork(
            imageView = view.findViewById(R.id.album_cover),
            track = track,
            fallbackRes = R.drawable.playing_cover_lp,
            sizePx = view.resources.getDimensionPixelSize(R.dimen.album_cover_zone_width),
        )
        view.findViewById<RatingBar>(R.id.rb_score).apply {
            setOnRatingBarChangeListener(null)
            rating = track.score.toFloat()
            setIsIndicator(false)
            isClickable = true
            isFocusable = false
            setOnRatingBarChangeListener { _, nextRating, fromUser ->
                if (fromUser) {
                    callbacks.onCurrentRatingChanged(
                        track.mediaId,
                        nextRating.roundToInt().coerceIn(0, 5),
                    )
                }
            }
        }
        view.findViewById<CheckBox>(R.id.favorite).apply {
            isChecked = currentFavorite
            setOnClickListener {
                callbacks.onFavoriteCurrentClick()
            }
        }
        return view
    }

    private fun getNormalTrackView(
        row: LegacyPlaybackQueueRow.Track,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val track = row.track
        val view = convertView ?: inflater.inflate(R.layout.legacy_playlist_normal_item, parent, false)
        view.setBackgroundResource(R.drawable.playing_queue_item_selector)
        bindTrackText(view, track)
        bindArtwork(
            imageView = view.findViewById(R.id.album_cover),
            track = track,
            fallbackRes = R.drawable.noalbumcover_120,
            sizePx = view.resources.getDimensionPixelSize(R.dimen.listview_item_image_width),
        )
        view.isClickable = false
        view.isFocusable = false
        view.findViewById<View>(R.id.iv_right).apply {
            if (row.section == LegacyPlaybackQueueSection.Upcoming && reorderEnabled) {
                visibility = View.VISIBLE
            } else {
                visibility = View.INVISIBLE
            }
            isClickable = false
            isFocusable = false
        }
        return view
    }

    private fun bindTrackText(view: View, track: LegacyPlaybackQueueTrack) {
        view.findViewById<TextView>(R.id.first_line_view).text = track.title
        view.findViewById<TextView>(R.id.second_line_view).text = track.artist
    }

    private fun bindArtwork(
        imageView: ImageView,
        track: LegacyPlaybackQueueTrack,
        fallbackRes: Int,
        sizePx: Int,
    ) {
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        artworkLoader.bind(
            imageView = imageView,
            mediaItem = track.mediaItem,
            fallbackRes = fallbackRes,
            sizePx = sizePx,
        )
    }
}

private sealed interface LegacyPlaybackQueueRow {
    data class Header(
        val title: String,
        val clearable: Boolean,
    ) : LegacyPlaybackQueueRow

    data class Track(
        val track: LegacyPlaybackQueueTrack,
        val section: LegacyPlaybackQueueSection,
    ) : LegacyPlaybackQueueRow
}

private enum class LegacyPlaybackQueueSection {
    History,
    Current,
    Upcoming,
}

private class PlaybackCenterTitle(context: Context) : RelativeLayout(context) {
    private val titleView: TextView
    private val subTitleView: TextView

    init {
        gravity = Gravity.CENTER
        titleView = TextView(context).apply {
            id = R.id.title_view
            ellipsize = TextUtils.TruncateAt.MARQUEE
            gravity = Gravity.CENTER
            isSelected = true
            marqueeRepeatLimit = -1
            setSingleLine(true)
            setHorizontallyScrolling(true)
            setTextColor(context.getColor(R.color.title_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, PlaybackTitleTextMaxSp.toFloat())
            setAutoSizeTextTypeUniformWithConfiguration(
                PlaybackTitleTextMinSp,
                PlaybackTitleTextMaxSp,
                PlaybackTitleTextStepSp,
                TypedValue.COMPLEX_UNIT_SP,
            )
            typeface = Typeface.create(paint.typeface, Typeface.BOLD)
        }
        addView(
            titleView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(CENTER_IN_PARENT)
            },
        )

        subTitleView = TextView(context).apply {
            gravity = Gravity.CENTER
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            setHorizontallyScrolling(false)
            setTextColor(context.getColor(R.color.sub_title_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            visibility = View.GONE
        }
        addView(
            subTitleView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(BELOW, R.id.title_view)
                addRule(CENTER_HORIZONTAL)
            },
        )
    }

    fun setTitle(title: CharSequence?) {
        titleView.text = title ?: ""
    }

    fun setSubTitle(subTitle: CharSequence?) {
        val hasSubTitle = !subTitle.isNullOrBlank()
        subTitleView.text = subTitle ?: ""
        subTitleView.visibility = if (hasSubTitle) View.VISIBLE else View.GONE
        val params = titleView.layoutParams as LayoutParams
        params.removeRule(CENTER_IN_PARENT)
        params.removeRule(CENTER_VERTICAL)
        params.addRule(CENTER_HORIZONTAL)
        if (!hasSubTitle) {
            params.addRule(CENTER_VERTICAL)
        }
        titleView.layoutParams = params
    }
}

private const val LegacyQueueRevealDurationMillis = 300
private const val LegacyQueueHistoryLimit = 2
private const val PlaybackTitleTextMinSp = 16
private const val PlaybackTitleTextMaxSp = 20
private const val PlaybackTitleTextStepSp = 1
