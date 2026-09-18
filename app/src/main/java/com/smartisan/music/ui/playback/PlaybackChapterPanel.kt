package com.smartisan.music.ui.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.smartisan.music.R
import com.smartisan.music.playback.NowPlayingChaptersRepository
import com.smartisan.music.playback.PlaybackChapter
import com.smartisan.music.playback.withResolvedEndTimes
import com.smartisan.music.ui.components.collectSmartisanPressedAsState
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.components.smartisanTextSize
import com.smartisan.music.ui.components.smartisanVerticalScrollbar
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect

/**
 * Chapters of the playing item, loaded off the main thread and cached with the other now-playing
 * metadata (artwork and lyrics).
 */
@Composable
internal fun rememberPlaybackChapters(mediaItem: MediaItem?): List<PlaybackChapter> {
    val context = LocalContext.current
    val mediaId = mediaItem?.mediaId
    val mediaUri = mediaItem?.localConfiguration?.uri
    val chaptersState =
        remember(mediaId, mediaUri) {
            mutableStateOf(mediaItem?.let(NowPlayingChaptersRepository::peek) ?: emptyList())
        }
    LaunchedEffect(mediaId, mediaUri) {
        val currentItem = mediaItem ?: return@LaunchedEffect
        chaptersState.value = NowPlayingChaptersRepository.peek(currentItem) ?: emptyList()
        chaptersState.value = NowPlayingChaptersRepository.load(context, currentItem)
    }
    return chaptersState.value
}

/**
 * Chapter surface placed directly under the artwork.
 *
 * The panel is a single [LazyColumn] whose height and row style are driven by two separate states
 * so the motion can be sequenced:
 * - [panelExpanded] drives the panel height, the anchored list scroll and the surrounding controls'
 *   scale. The stage above is a weighted sibling, so the panel taking room scales the turntable
 *   down and the freed space is exactly where the list appears.
 * - [rowExpanded] drives only the current row morph inside the row: the title slides right from the
 *   leading edge to the center and the duration slides left to the center while fading, both with
 *   [PlaybackChapterRowMorphMillis].
 *
 * The caller plays the two phases in order: expanding runs the panel first and the row morph
 * second, collapsing runs the row morph first and the panel second. Tapping the currently playing
 * row toggles the panel (collapsed expands, expanded collapses); tapping another row selects that
 * chapter and animates it into the vertical center.
 */
@Composable
internal fun PlaybackChapterPanel(
    chapters: List<PlaybackChapter>,
    currentIndex: Int,
    totalDurationMs: Long,
    panelExpanded: Boolean,
    rowExpanded: Boolean,
    rowsHidden: Boolean,
    onToggle: () -> Unit,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayChapters =
        remember(chapters, totalDurationMs) {
            chapters.withResolvedEndTimes(totalDurationMs)
        }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val panelRevealState =
        animateFloatAsState(
            targetValue = if (panelExpanded) 1f else 0f,
            animationSpec =
                tween(
                    durationMillis = PlaybackChapterPanelMotionMillis,
                    easing = FastOutSlowInEasing,
                ),
            label = "chapter panel reveal",
        )
    val panelReveal = panelRevealState.value
    val currentIndexState = rememberUpdatedState(currentIndex)
    val panelExpandedState = rememberUpdatedState(panelExpanded)
    val panelHeight =
        PlaybackChapterRowHeight +
            (PlaybackChapterExpandedHeight - PlaybackChapterRowHeight) * panelReveal
    val centeredSpacerHeight = PlaybackChapterCenteredPadding * panelReveal
    val finalCenteredOffsetPx = with(density) { PlaybackChapterCenteredPadding.roundToPx() }
    LaunchedEffect(listState, chapters) {
        var lastIndex = currentIndex
        snapshotFlow {
                ChapterScrollTarget(
                    expanded = panelExpandedState.value,
                    index = currentIndexState.value,
                    spacerPx =
                        with(density) {
                            (PlaybackChapterCenteredPadding * panelRevealState.value).roundToPx()
                        },
                )
            }
            .collect { target ->
                if (target.index !in chapters.indices) {
                    return@collect
                }
                val anchor = target.index + 1
                if (target.index != lastIndex) {
                    // Another chapter was selected: glide it to the center (or reposition without
                    // animation when the panel is collapsed and only the current row is visible).
                    if (target.expanded) {
                        listState.animateScrollToItem(anchor, -finalCenteredOffsetPx)
                    } else {
                        listState.scrollToItem(anchor, 0)
                    }
                } else {
                    // Panel reveal: glue the anchor to the very same animated spacer, so the row
                    // has exactly one vertical motion and never fights a second scroll animation.
                    listState.requestScrollToItem(anchor, -target.spacerPx)
                }
                lastIndex = target.index
            }
    }
    LazyColumn(
        modifier =
            modifier
                .fillMaxWidth()
                .height(panelHeight)
                .smartisanPainterBackground(
                    rememberSmartisanDrawablePainter(R.drawable.account_background)
                )
                .then(
                    if (panelExpanded) Modifier.smartisanVerticalScrollbar(listState) else Modifier
                ),
        state = listState,
        userScrollEnabled = panelExpanded,
    ) {
        item(key = PlaybackChapterTopSpacerKey) {
            Spacer(Modifier.height(centeredSpacerHeight))
        }
        itemsIndexed(
            displayChapters,
            key = { index, chapter -> "$index:${chapter.startTimeMs}" },
        ) { index, chapter ->
            PlaybackChapterRow(
                chapter = chapter,
                index = index,
                current = index == currentIndex,
                collapsedCentered = !rowExpanded && index == currentIndex,
                hidden = rowsHidden && index != currentIndex,
                onClick = {
                    if (!panelExpanded || index == currentIndex) {
                        onToggle()
                    } else {
                        onChapterClick(index)
                    }
                },
            )
            if (index < displayChapters.lastIndex) {
                val hideDivider = rowsHidden && index != currentIndex
                Spacer(
                    Modifier.fillMaxWidth()
                        .height(PlaybackChapterDividerHeight)
                        .graphicsLayer { alpha = if (hideDivider) 0f else 1f }
                        .background(colorResource(R.color.listview_divider_color))
                )
            }
        }
        item(key = PlaybackChapterBottomSpacerKey) {
            Spacer(Modifier.height(centeredSpacerHeight))
        }
    }
}

@Composable
private fun PlaybackChapterRow(
    chapter: PlaybackChapter,
    index: Int,
    current: Boolean,
    collapsedCentered: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectSmartisanPressedAsState()
    val title = chapter.title.ifBlank { stringResource(R.string.chapter_default_title, index + 1) }
    val density = LocalDensity.current
    val titleStartPx =
        with(density) {
            (
                    PlaybackChapterHorizontalPadding +
                        PlaybackChapterIndicatorWidth +
                        PlaybackChapterIndicatorSpacing
                )
                .roundToPx()
                .toFloat()
        }
    val endPaddingPx =
        with(density) { PlaybackChapterHorizontalPadding.roundToPx().toFloat() }
    var rowWidthPx by remember { mutableIntStateOf(0) }
    var titleWidthPx by remember { mutableIntStateOf(0) }
    var durationWidthPx by remember { mutableIntStateOf(0) }
    val titleShift = remember { Animatable(0f) }
    val durationShift = remember { Animatable(0f) }
    var titleShiftInitialized by remember { mutableStateOf(false) }
    var durationShiftInitialized by remember { mutableStateOf(false) }
    val centeredTitleShiftPx = ((rowWidthPx - titleWidthPx) / 2f) - titleStartPx
    val centeredDurationShiftPx = -(rowWidthPx / 2f) + endPaddingPx + (durationWidthPx / 2f)
    // Expanding targets are fixed, so the animation must not be restarted by the width
    // measurements that change while the duration slot grows in (that caused a double bounce).
    // Title and duration get their own effect so both start on the same frame instead of
    // running one after the other.
    LaunchedEffect(collapsedCentered) {
        if (collapsedCentered) {
            return@LaunchedEffect
        }
        titleShiftInitialized = true
        titleShift.animateTo(0f, chapterRowMotionSpec())
    }
    LaunchedEffect(collapsedCentered) {
        if (collapsedCentered) {
            return@LaunchedEffect
        }
        durationShiftInitialized = true
        durationShift.animateTo(0f, chapterRowMotionSpec())
    }
    LaunchedEffect(collapsedCentered, centeredTitleShiftPx) {
        if (!collapsedCentered || rowWidthPx <= 0 || titleWidthPx <= 0) {
            return@LaunchedEffect
        }
        if (titleShiftInitialized) {
            titleShift.animateTo(centeredTitleShiftPx, chapterRowMotionSpec())
        } else {
            titleShift.snapTo(centeredTitleShiftPx)
            titleShiftInitialized = true
        }
    }
    LaunchedEffect(collapsedCentered, centeredDurationShiftPx) {
        if (!collapsedCentered || rowWidthPx <= 0 || durationWidthPx <= 0) {
            return@LaunchedEffect
        }
        if (durationShiftInitialized) {
            durationShift.animateTo(centeredDurationShiftPx, chapterRowMotionSpec())
        } else {
            durationShift.snapTo(centeredDurationShiftPx)
            durationShiftInitialized = true
        }
    }
    val durationAlpha by
        animateFloatAsState(
            targetValue = if (collapsedCentered) 0f else 1f,
            animationSpec = chapterRowMotionSpec(),
            label = "chapter duration alpha",
        )
    val indicatorAlpha by
        animateFloatAsState(
            targetValue = if (current && !collapsedCentered) 1f else 0f,
            animationSpec = chapterRowMotionSpec(),
            label = "chapter indicator alpha",
        )
    val reservedEndPadding by
        animateDpAsState(
            targetValue =
                if (collapsedCentered) 0.dp else with(density) { durationWidthPx.toDp() },
            animationSpec = chapterRowDpMotionSpec(),
            label = "chapter duration reserve",
        )
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(PlaybackChapterRowHeight)
                .graphicsLayer { alpha = if (hidden) 0f else 1f }
                .onSizeChanged { rowWidthPx = it.width }
                .smartisanPainterBackground(
                    rememberSmartisanDrawablePainter(
                        R.drawable.playing_queue_item_selector,
                        pressed = pressed,
                    )
                )
                .clickable(
                    interaction,
                    null,
                    role = Role.Button,
                    enabled = !hidden,
                    onClick = smartisanClick(onClick),
                )
    ) {
        Row(
            modifier =
                Modifier.fillMaxSize().padding(horizontal = PlaybackChapterHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(PlaybackChapterIndicatorWidth)
                    .height(PlaybackChapterIndicatorHeight)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .background(colorResource(R.color.btn_text_color_blue))
            )
            Box(
                Modifier.weight(1f)
                    .padding(
                        start = PlaybackChapterIndicatorSpacing,
                        end = reservedEndPadding,
                    ),
            ) {
                BasicText(
                    title,
                    Modifier
                        .graphicsLayer { translationX = titleShift.value }
                        .onSizeChanged { titleWidthPx = it.width },
                    style =
                        TextStyle(
                            color =
                                if (current) {
                                    colorResource(R.color.btn_text_color_blue)
                                } else {
                                    smartisanStateColor(
                                        R.drawable.text_color_white_and_black_selector,
                                        pressed = pressed,
                                    )
                                },
                            fontSize = smartisanTextSize(R.dimen.text_size_small),
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BasicText(
            formatPlaybackTime(chapter.durationMs),
            Modifier.align(Alignment.CenterEnd)
                .padding(end = PlaybackChapterHorizontalPadding)
                .graphicsLayer {
                    translationX = durationShift.value
                    alpha = durationAlpha
                }
                .onSizeChanged { durationWidthPx = it.width },
            style =
                TextStyle(
                    color = colorResource(R.color.sub_title_text_color),
                    fontSize = smartisanTextSize(R.dimen.text_size_small),
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
        )
    }
}

private fun chapterRowMotionSpec() =
    tween<Float>(
        durationMillis = PlaybackChapterRowMorphMillis,
        easing = FastOutSlowInEasing,
    )

private data class ChapterScrollTarget(
    val expanded: Boolean,
    val index: Int,
    val spacerPx: Int,
)

private fun chapterRowDpMotionSpec() =
    tween<androidx.compose.ui.unit.Dp>(
        durationMillis = PlaybackChapterRowMorphMillis,
        easing = FastOutSlowInEasing,
    )

private val PlaybackChapterRowHeight = 48.dp
private val PlaybackChapterExpandedHeight = 320.dp
private val PlaybackChapterCenteredPadding =
    (PlaybackChapterExpandedHeight - PlaybackChapterRowHeight) / 2
private val PlaybackChapterHorizontalPadding = 12.dp
private val PlaybackChapterIndicatorWidth = 2.dp
private val PlaybackChapterIndicatorHeight = 18.dp
private val PlaybackChapterIndicatorSpacing = 8.dp
private val PlaybackChapterDividerHeight = 1.dp
private const val PlaybackChapterTopSpacerKey = "chapter-top-spacer"
private const val PlaybackChapterBottomSpacerKey = "chapter-bottom-spacer"
internal const val PlaybackChapterPanelMotionMillis = 300
internal const val PlaybackChapterRowMorphMillis = 100
