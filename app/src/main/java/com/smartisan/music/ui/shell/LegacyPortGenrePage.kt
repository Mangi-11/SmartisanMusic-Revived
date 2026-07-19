package com.smartisan.music.ui.shell

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toDrawable
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.data.genre.GenreTagRepository
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.ui.genre.GenreSummary
import com.smartisan.music.ui.genre.buildGenreSummaries
import com.smartisan.music.ui.shell.titlebar.LegacyPortSmartisanTitleBar
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarShadow
import com.smartisan.music.ui.shell.titlebar.LegacyPortTitleBarTransition
import com.smartisan.music.ui.widgets.legacy.TitleBar

@Composable
internal fun LegacyPortGenrePage(
    active: Boolean,
    mediaItems: List<MediaItem>,
    hiddenMediaIds: Set<String>,
    libraryLoaded: Boolean,
    onClose: (() -> Unit)?,
    closePredictiveBackState: LegacyPortPredictiveBackState?,
    onTrackMoreClick: (MediaItem) -> Unit,
    onLibraryNeeded: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val repository = remember(context.applicationContext) {
        GenreTagRepository(context.applicationContext)
    }
    val genreTitle = stringResource(R.string.tab_style)
    val unknownGenreTitle = stringResource(R.string.unknown_style)
    val visibleItems = remember(mediaItems, hiddenMediaIds) {
        mediaItems.filterNot { it.mediaId in hiddenMediaIds }
    }
    var genreMap by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var genreTagsLoaded by remember { mutableStateOf(false) }
    var selectedGenreId by remember { mutableStateOf<String?>(null) }
    val genres = remember(visibleItems, genreMap, genreTagsLoaded, unknownGenreTitle) {
        if (genreTagsLoaded) {
            buildGenreSummaries(
                mediaItems = visibleItems,
                genreMap = genreMap,
                unknownGenreTitle = unknownGenreTitle,
            )
        } else {
            emptyList()
        }
    }
    val selectedGenre = genres.firstOrNull { it.id == selectedGenreId }
    val detailPredictiveBackState = rememberLegacyPortPredictiveBackState()

    LaunchedEffect(active) {
        if (active) {
            onLibraryNeeded()
        }
    }
    LaunchedEffect(active, libraryLoaded, visibleItems) {
        if (!active) {
            return@LaunchedEffect
        }
        if (!libraryLoaded) {
            genreTagsLoaded = false
            genreMap = emptyMap()
            selectedGenreId = null
            return@LaunchedEffect
        }
        genreTagsLoaded = false
        genreMap = repository.loadGenres(visibleItems)
        genreTagsLoaded = true
    }
    LaunchedEffect(selectedGenreId, genres) {
        if (selectedGenreId != null && selectedGenre == null && genreTagsLoaded) {
            selectedGenreId = null
        }
    }

    LegacyPortPredictiveBackHandler(
        enabled = active && selectedGenre != null,
        state = detailPredictiveBackState,
    ) {
        selectedGenreId = null
    }
    if (closePredictiveBackState != null && onClose != null) {
        LegacyPortPredictiveBackHandler(
            enabled = active && selectedGenre == null,
            state = closePredictiveBackState,
            onBack = onClose,
        )
    } else if (onClose != null) {
        BackHandler(enabled = active && selectedGenre == null) {
            onClose()
        }
    }

    val titleAreaHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        dimensionResource(R.dimen.title_bar_height)
    val titleShadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LegacyPortTitleBarTransition(
                secondaryKey = selectedGenre,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(titleAreaHeight),
                label = "legacy genre title stack",
                predictiveBackProgress = detailPredictiveBackState.progress,
                predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                primaryContent = {
                    LegacyPortSmartisanTitleBar(modifier = Modifier.fillMaxSize()) { titleBar ->
                        titleBar.setupLegacyGenreTitleBar(
                            title = genreTitle,
                            onBack = onClose,
                            onSearchClick = onSearchClick,
                        )
                    }
                },
                secondaryContent = { genre ->
                    LegacyPortSmartisanTitleBar(modifier = Modifier.fillMaxSize()) { titleBar ->
                        titleBar.setupLegacyGenreTitleBar(
                            title = genre.name,
                            onBack = { selectedGenreId = null },
                            onSearchClick = null,
                        )
                    }
                },
            )
            LegacyPortPageStackTransition(
                secondaryKey = selectedGenre,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "legacy genre detail stack",
                predictiveBackProgress = detailPredictiveBackState.progress,
                predictiveBackExitConsumed = detailPredictiveBackState.exitConsumed,
                onPredictiveBackExitConsumedReset = detailPredictiveBackState::reset,
                primaryContent = {
                    LegacyGenreRootPage(
                        active = active,
                        genres = genres,
                        libraryLoaded = libraryLoaded && genreTagsLoaded,
                        onGenreSelected = { selectedGenreId = it.id },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                secondaryContent = { genre ->
                    LegacyFolderDetailPage(
                        active = active && selectedGenre == genre,
                        tracks = genre.songs,
                        browser = browser,
                        onTrackMoreClick = onTrackMoreClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
        LegacyPortTitleBarShadow(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = titleAreaHeight)
                .fillMaxWidth()
                .height(titleShadowHeight)
                .zIndex(1f),
        )
    }
}

private fun TitleBar.setupLegacyGenreTitleBar(
    title: String,
    onBack: (() -> Unit)?,
    onSearchClick: (() -> Unit)?,
) {
    removeAllLeftViews()
    removeAllRightViews()
    setShadowVisible(false)
    setCenterText(title)
    onBack?.let { action ->
        addLeftImageView(R.drawable.standard_icon_back_selector).setOnClickListener {
            action()
        }
    }
    onSearchClick?.let { action ->
        addRightImageView(R.drawable.search_btn_selector).setOnClickListener {
            action()
        }
    }
}

@Composable
private fun LegacyGenreRootPage(
    active: Boolean,
    genres: List<GenreSummary>,
    libraryLoaded: Boolean,
    onGenreSelected: (GenreSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> LegacyGenreRootView(context) },
        update = { root ->
            root.visibility = if (active) View.VISIBLE else View.INVISIBLE
            root.bind(
                genres = genres,
                loaded = libraryLoaded,
                onGenreSelected = onGenreSelected,
            )
        },
    )
}

private class LegacyGenreRootView(context: Context) : FrameLayout(context) {
    private val listView: ListView
    private val blankView: View

    init {
        setBackgroundResource(R.drawable.account_background)
        LayoutInflater.from(context).inflate(R.layout.f_genre_list, this, true)
        listView = findViewById<ListView>(android.R.id.list).apply {
            divider = context.getColor(R.color.listview_divider_color).toDrawable()
            dividerHeight = resources.getDimensionPixelSize(R.dimen.listview_dividerHeight)
            selector = context.getDrawable(R.drawable.listview_selector)
            cacheColorHint = Color.TRANSPARENT
            layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.list_anim_layout)
            addLegacyPortListFooter()
        }
        blankView = findViewById(R.id.fl_null_playlist)
    }

    fun bind(
        genres: List<GenreSummary>,
        loaded: Boolean,
        onGenreSelected: (GenreSummary) -> Unit,
    ) {
        val adapter = listView.legacyGenreAdapter() ?: LegacyGenreAdapter().also { nextAdapter ->
            listView.adapter = nextAdapter
            listView.scheduleLayoutAnimation()
        }
        val changed = adapter.submitList(genres)
        listView.setOnItemClickListener { _, _, position, _ ->
            adapter.itemAt(position)?.let(onGenreSelected)
        }
        listView.bindLegacyPortListFooter(
            pluralsRes = R.plurals.genre_count,
            count = genres.size,
        )
        val empty = loaded && genres.isEmpty()
        blankView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.INVISIBLE else View.VISIBLE
        if (changed) {
            listView.scheduleLayoutAnimation()
        }
    }
}

private fun ListView.legacyGenreAdapter(): LegacyGenreAdapter? {
    val current = adapter
    return when (current) {
        is LegacyGenreAdapter -> current
        is android.widget.HeaderViewListAdapter -> current.wrappedAdapter as? LegacyGenreAdapter
        else -> null
    }
}

private class LegacyGenreAdapter : BaseAdapter() {
    private var genres: List<GenreSummary> = emptyList()

    fun submitList(nextGenres: List<GenreSummary>): Boolean {
        if (genres == nextGenres) {
            return false
        }
        genres = nextGenres
        notifyDataSetChanged()
        return true
    }

    fun itemAt(position: Int): GenreSummary? = genres.getOrNull(position)

    override fun getCount(): Int = genres.size

    override fun getItem(position: Int): Any = genres[position]

    override fun getItemId(position: Int): Long = genres[position].id.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_listview, parent, false)
        val genre = genres[position]
        view.findViewById<RelativeLayout>(R.id.rl_list_item_parent)?.setPadding(
            parent.resources.getDimensionPixelSize(R.dimen.listview_items_margin_left),
            0,
            0,
            0,
        )
        view.findViewById<TextView>(R.id.listview_item_line_one)?.text = genre.name
        view.findViewById<TextView>(R.id.listview_item_line_two)?.text =
            parent.resources.getQuantityString(R.plurals.track_count, genre.trackCount, genre.trackCount)
        view.findViewById<CheckBox>(R.id.cb_del)?.visibility = View.GONE
        view.findViewById<ImageView>(R.id.iv_right_view)?.visibility = View.GONE
        view.findViewById<ImageView>(R.id.arrow)?.visibility = View.VISIBLE
        return view
    }
}
