package com.smartisan.music.ui.playback

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisan.music.R
import com.smartisan.music.playback.PlaybackSleepTimerState
import com.smartisan.music.ui.components.SmartisanMenuTitleBar
import com.smartisan.music.ui.components.SmartisanTouchShield
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground

internal data class PlaybackMoreActionCallbacks(
    val onAddToPlaylistClick: () -> Unit,
    val onAddToQueueClick: () -> Unit,
    val onFavoriteToggle: () -> Unit,
    val onShareClick: () -> Unit,
    val onLyricsToggle: () -> Unit,
    val onSleepTimerClick: () -> Unit,
    val onScratchToggle: () -> Unit,
    val onDeleteClick: () -> Unit,
    val onDismissRequest: () -> Unit,
)

@Composable
internal fun PlaybackMoreActionsOverlay(
    visible: Boolean,
    favoriteEnabled: Boolean,
    visualPage: PlaybackVisualPage,
    scratchEnabled: Boolean,
    sleepTimerActive: Boolean,
    addToPlaylistEnabled: Boolean,
    shareEnabled: Boolean,
    callbacks: PlaybackMoreActionCallbacks,
    modifier: Modifier = Modifier,
) {
    PlaybackBottomPanel(visible, callbacks.onDismissRequest, modifier) {
        SmartisanMenuTitleBar(stringResource(R.string.select_action), callbacks.onDismissRequest)
        val items =
            buildMoreActions(
                favoriteEnabled,
                visualPage,
                scratchEnabled,
                sleepTimerActive,
                addToPlaylistEnabled,
                shareEnabled,
                callbacks,
            )
        items.chunked(MoreActionColumnCount).forEach { row ->
            Row(Modifier.fillMaxWidth().height(74.dp)) {
                row.forEach { item -> MoreActionCell(item, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
        Spacer(
            Modifier.fillMaxWidth()
                .height(with(LocalDensity.current) { 1.toDp() })
                .background(colorResource(R.color.bottom_line))
        )
    }
}

@Composable
internal fun PlaybackSleepTimerDialog(
    visible: Boolean,
    state: PlaybackSleepTimerState,
    bottomInsetPx: Int,
    onDismissRequest: () -> Unit,
    onDurationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackBottomPanel(visible, onDismissRequest, modifier) {
        key(state.isActive) {
            var selected by remember { mutableIntStateOf(1) }
            val title =
                if (state.isActive) {
                    "${stringResource(R.string.remain_time)} ${formatSleepTimerRemaining(state.remainingMs)}"
                } else stringResource(R.string.setting_stop_time)
            SmartisanMenuTitleBar(
                title,
                onDismissRequest,
                onConfirm = { onDurationSelected(mapSleepTimerDuration(state.isActive, selected)) },
                confirmEnabled = !(state.isActive && selected == 1),
            )
            val labels = buildList {
                if (state.isActive) add(stringResource(R.string.time_countdown))
                add(stringResource(R.string.time_no))
                add(stringResource(R.string.time_15m))
                add(stringResource(R.string.time_30m))
                add(stringResource(R.string.time_1h))
                add(stringResource(R.string.time_1_5h))
                add(stringResource(R.string.time_2h))
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(208.dp)
                    .smartisanPainterBackground(
                        rememberSmartisanDrawablePainter(R.drawable.time_picker_widget_bg)
                    )
                    .padding(horizontal = 15.dp)
            ) {
                PlaybackSleepTimerPicker(labels, { selected = it }, Modifier.fillMaxSize())
            }
            val bottom = rememberSmartisanDrawablePainter(R.drawable.time_picker_widget_bottom)
            Image(
                bottom,
                null,
                Modifier.fillMaxWidth()
                    .height(with(LocalDensity.current) { bottom.intrinsicSize.height.toDp() }),
                contentScale = ContentScale.FillBounds,
            )
        }
        Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { bottomInsetPx.toDp() }))
    }
}

@Composable
private fun PlaybackBottomPanel(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var rendered by remember { mutableStateOf(visible) }
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) rendered = true
        fraction.animateTo(
            if (visible) 1f else 0f,
            tween(300, easing = { 1f - (1f - it) * (1f - it) }),
        )
        if (!visible) rendered = false
    }
    if (rendered) {
        Box(modifier.graphicsLayer { alpha = fraction.value }) {
            Box(
                Modifier.matchParentSize()
                    .background(colorResource(R.color.transparent_black))
                    .clickable(
                        remember { MutableInteractionSource() },
                        null,
                        onClick = smartisanClick(onDismissRequest),
                    )
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer {
                    translationY = size.height * (1f - fraction.value)
                    alpha = 0.92f + 0.08f * fraction.value
                }
            ) {
                SmartisanTouchShield()
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

@Composable
private fun MoreActionCell(item: MoreActionItem, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .graphicsLayer { alpha = if (item.enabled) 1f else DisabledAlpha }
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    R.drawable.menu_item_selector,
                    enabled = item.enabled,
                    pressed = pressed,
                )
            )
            .clickable(
                interaction,
                null,
                enabled = item.enabled,
                onClick = smartisanClick(item.onClick),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                rememberSmartisanDrawablePainter(
                    if (pressed) item.pressedIconRes else item.iconRes,
                    enabled = item.enabled,
                    pressed = pressed,
                ),
                null,
            )
            BasicText(
                stringResource(item.labelRes),
                Modifier.padding(top = 1.dp, start = 7.dp, end = 7.dp),
                style =
                    TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            colorResource(
                                if (item.selected) R.color.btn_text_color_blue
                                else R.color.add_nav_text_color
                            ),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private data class MoreActionItem(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val pressedIconRes: Int = iconRes,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private fun buildMoreActions(
    favoriteEnabled: Boolean,
    visualPage: PlaybackVisualPage,
    scratchEnabled: Boolean,
    sleepTimerActive: Boolean,
    addToPlaylistEnabled: Boolean,
    shareEnabled: Boolean,
    callbacks: PlaybackMoreActionCallbacks,
): List<MoreActionItem> {
    return listOf(
        MoreActionItem(
            labelRes = R.string.add_to_playlist,
            iconRes = R.drawable.more_select_icon_addlist,
            pressedIconRes = R.drawable.more_select_icon_addlist_down,
            enabled = addToPlaylistEnabled,
            onClick = callbacks.onAddToPlaylistClick,
        ),
        MoreActionItem(
            labelRes = R.string.add_to_queue,
            iconRes = R.drawable.more_select_icon_addplay,
            pressedIconRes = R.drawable.more_select_icon_addplay_down,
            onClick = callbacks.onAddToQueueClick,
        ),
        MoreActionItem(
            labelRes = if (favoriteEnabled) R.string.cancel_love else R.string.love,
            iconRes =
                if (favoriteEnabled) {
                    R.drawable.more_select_icon_favorite_cancel
                } else {
                    R.drawable.more_select_icon_favorite_add
                },
            pressedIconRes =
                if (favoriteEnabled) {
                    R.drawable.more_select_icon_favorite_cancel_down
                } else {
                    R.drawable.more_select_icon_favorite_add_down
                },
            enabled = addToPlaylistEnabled,
            onClick = callbacks.onFavoriteToggle,
        ),
        MoreActionItem(
            labelRes = R.string.lyrics,
            iconRes = R.drawable.more_select_icon_lyric,
            selected = visualPage == PlaybackVisualPage.Lyrics,
            onClick = callbacks.onLyricsToggle,
        ),
        MoreActionItem(
            labelRes = R.string.sleep_timer,
            iconRes = R.drawable.more_select_icon_timer,
            selected = sleepTimerActive,
            onClick = callbacks.onSleepTimerClick,
        ),
        MoreActionItem(
            labelRes = R.string.djing,
            iconRes =
                if (scratchEnabled) {
                    R.drawable.more_select_icon_djing_on
                } else {
                    R.drawable.more_select_icon_djing
                },
            selected = scratchEnabled,
            onClick = callbacks.onScratchToggle,
        ),
        MoreActionItem(
            labelRes = R.string.delete,
            iconRes = R.drawable.more_select_icon_delete,
            onClick = callbacks.onDeleteClick,
        ),
        MoreActionItem(
            labelRes = R.string.share,
            iconRes = R.drawable.more_select_icon_share,
            pressedIconRes = R.drawable.more_select_icon_share_down,
            enabled = shareEnabled,
            onClick = callbacks.onShareClick,
        ),
    )
}

internal fun mapSleepTimerDuration(
    active: Boolean,
    value: Int,
): Long {
    val originalValue = if (active) value else value + 1
    return when (originalValue) {
        3 -> 15L * MinuteMs
        4 -> 30L * MinuteMs
        5 -> 60L * MinuteMs
        6 -> 90L * MinuteMs
        7 -> 120L * MinuteMs
        else -> 0L
    }
}

private const val MoreActionColumnCount = 4
private const val DisabledAlpha = 0.35f
private const val MinuteMs = 60_000L
