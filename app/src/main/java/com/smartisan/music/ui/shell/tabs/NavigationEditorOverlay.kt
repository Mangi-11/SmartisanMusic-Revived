package com.smartisan.music.ui.shell.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.navigation.*
import kotlin.math.roundToInt

@Composable
internal fun NavigationEditorOverlay(
    visible: Boolean,
    layout: NavigationLayout,
    selectedDestination: MusicDestination,
    onDismissRequest: () -> Unit,
    onCommit: (NavigationLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(layout) }
    var dragging by remember { mutableStateOf<MusicDestination?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var dragAnchor by remember { mutableStateOf(Offset.Zero) }
    var announcement by remember { mutableStateOf("") }
    val bounds = remember { mutableStateMapOf<MusicDestination, Rect>() }
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(visible) { if (visible) draft = layout else dragging = null }
    fun swap(first: MusicDestination, second: MusicDestination) {
        val next = draft.swap(first, second)
        if (next != draft) {
            draft = next
            announcement =
                resources.getString(
                    R.string.navigation_editor_items_swapped,
                    resources.getString(first.labelRes),
                    resources.getString(second.labelRes),
                )
        }
    }
    Box(modifier.fillMaxSize()) {
        SmartisanAnimatedSheet(
            visible,
            onDismissRequest,
            showDuration = 220,
            hideDuration = 180,
            scrim = colorResource(R.color.transparent_black),
            easing =
                androidx.compose.animation.core.Easing {
                    ((kotlin.math.cos((it + 1) * kotlin.math.PI) / 2.0) + .5).toFloat()
                },
        ) {
            SmartisanMenuTitleBar(
                stringResource(R.string.navigation_editor_title),
                onDismissRequest,
                { onCommit(draft) },
            )
            @Composable
            fun RowScope.item(destination: MusicDestination, overflow: Boolean) {
                val index = draft.orderedDestinations.indexOf(destination)
                val start = if (overflow) draft.bottomCount else 0
                val end =
                    if (overflow) draft.orderedDestinations.lastIndex else draft.bottomCount - 1
                val actions = buildList {
                    if (destination.movable && index > start)
                        add(
                            CustomAccessibilityAction(
                                resources.getString(R.string.navigation_editor_move_earlier)
                            ) {
                                draft = draft.move(destination, -1)
                                announcement =
                                    resources.getString(
                                        R.string.navigation_editor_item_moved,
                                        resources.getString(destination.labelRes),
                                    )
                                true
                            }
                        )
                    if (destination.movable && index in 0 until end)
                        add(
                            CustomAccessibilityAction(
                                resources.getString(R.string.navigation_editor_move_later)
                            ) {
                                draft = draft.move(destination, 1)
                                announcement =
                                    resources.getString(
                                        R.string.navigation_editor_item_moved,
                                        resources.getString(destination.labelRes),
                                    )
                                true
                            }
                        )
                    if (destination.movable)
                        add(
                            CustomAccessibilityAction(
                                resources.getString(
                                    if (overflow) R.string.navigation_editor_pin_replacing
                                    else R.string.navigation_editor_unpin_replacing
                                )
                            ) {
                                val target =
                                    if (draft.isPinned(destination))
                                        draft.overflowDestinations.firstOrNull()
                                    else draft.bottomDestinations.dropLast(1).lastOrNull()
                                if (target != null) swap(destination, target)
                                target != null
                            }
                        )
                }
                val description =
                    stringResource(
                        if (overflow) R.string.navigation_editor_overflow_item_description
                        else R.string.navigation_editor_bottom_item_description,
                        stringResource(destination.labelRes),
                    )
                val checked =
                    !overflow &&
                        destination ==
                            (selectedDestination.takeIf(draft::isPinned) ?: MusicDestination.More)
                SmartisanBottomTabItem(
                    destination,
                    checked,
                    {},
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { bounds[destination] = it.boundsInRoot() }
                        .graphicsLayer { alpha = if (dragging == destination) 0.35f else 1f }
                        .clearAndSetSemantics {
                            contentDescription = description
                            customActions = actions
                        }
                        .then(
                            if (destination.movable)
                                Modifier.pointerInput(destination) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { local ->
                                            dragging = destination
                                            dragAnchor = local
                                            pointer =
                                                (bounds[destination]?.topLeft ?: Offset.Zero) +
                                                    local
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            pointer += amount
                                        },
                                        onDragEnd = {
                                            bounds.entries
                                                .firstOrNull {
                                                    it.key.movable &&
                                                        it.key != destination &&
                                                        it.value.contains(pointer)
                                                }
                                                ?.let { swap(destination, it.key) }
                                            dragging = null
                                        },
                                        onDragCancel = { dragging = null },
                                    )
                                }
                            else Modifier
                        )
                        .padding(
                            top =
                                if (overflow)
                                    dimensionResource(R.dimen.smartisan_switch_bar_top_rg_padding)
                                else 0.dp
                        ),
                    inOverflow = overflow,
                )
            }
            Box(
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.navigation_editor_overflow_height))
                    .background(colorResource(R.color.tab_bar_top_background))
            ) {
                Row(
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                        )
                ) {
                    draft.overflowDestinations.forEach { item(it, true) }
                    repeat((5 - draft.overflowDestinations.size).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Image(
                    rememberSmartisanDrawablePainter(R.drawable.title_bar_shadow),
                    null,
                    Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    contentScale = ContentScale.FillWidth,
                )
            }
            Spacer(
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.nav_divider_height))
                    .background(colorResource(R.color.nav_list_line))
            )
            Row(
                Modifier.fillMaxWidth()
                    .smartisanPainterBackground(
                        rememberSmartisanDrawablePainter(R.drawable.sb_repeat_tabbar_bg)
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(dimensionResource(R.dimen.smartisan_tabswitch_tabbar_height))
            ) {
                draft.bottomDestinations.forEach { item(it, false) }
            }
        }
        val source = dragging
        val sourceBounds = source?.let(bounds::get)
        if (source != null && sourceBounds != null) {
            val density = LocalDensity.current
            SmartisanBottomTabItem(
                source,
                false,
                {},
                Modifier.offset {
                        IntOffset(
                            (pointer.x - dragAnchor.x).roundToInt(),
                            (pointer.y - dragAnchor.y).roundToInt(),
                        )
                    }
                    .size(
                        with(density) { sourceBounds.width.toDp() },
                        with(density) { sourceBounds.height.toDp() },
                    )
                    .graphicsLayer { alpha = 0.75f }
                    .clearAndSetSemantics {},
                inOverflow = !draft.isPinned(source),
            )
        }
        if (visible)
            BasicText(
                announcement,
                Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
            )
    }
}
