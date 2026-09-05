package com.smartisan.music.ui.songs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.smartisan.music.R
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.library.libraryTexture
import kotlinx.coroutines.launch

@Composable
internal fun SmartisanSwipeDeleteRow(
    key: String,
    enabled: Boolean,
    openKey: String?,
    onOpenChange: (String?) -> Unit,
    onDelete: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeActivity: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val shift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val resources = LocalResources.current
    val deleteImage = rememberSmartisanDrawablePainter(R.drawable.compose_quicktext_delete)
    val width =
        deleteImage.intrinsicSize.width +
            resources.getInteger(R.integer.delete_button_padding_left) * 2
    val shadow = with(density) { dimensionResource(R.dimen.lv_item_shadow_width).roundToPx() }
    val currentOpen by rememberUpdatedState(openKey)
    val openChange by rememberUpdatedState(onOpenChange)
    val activity by rememberUpdatedState(onSwipeActivity)
    fun close() {
        onOpenChange(null)
        onSwipeActivity(false)
    }
    LaunchedEffect(openKey, enabled) {
        if (!enabled || openKey != key)
            shift.animateTo(
                0f,
                tween(200, easing = com.smartisan.music.ui.components.SmartisanEaseInOut),
            )
    }
    Box(
        modifier.fillMaxWidth().clipToBounds().libraryTexture().pointerInput(key, enabled, width) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (currentOpen != null) {
                    if (currentOpen != key || down.position.x > width) {
                        down.consume()
                        openChange(null)
                        activity(false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                    return@awaitEachGesture
                }
                var gesture = SongSwipeDeleteMotion.Gesture.PENDING
                var distance = 0f
                var finished = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        val delta = change.position - down.position
                        gesture =
                            SongSwipeDeleteMotion.resolve(
                                gesture,
                                delta.x,
                                delta.y,
                                viewConfiguration.touchSlop,
                            )
                        if (gesture == SongSwipeDeleteMotion.Gesture.LIST_SCROLL) break
                        if (gesture == SongSwipeDeleteMotion.Gesture.SWIPE_DELETE) {
                            change.consume()
                            activity(true)
                            distance = delta.x.coerceAtLeast(0f)
                            val resisted =
                                when {
                                    distance < width -> distance
                                    distance <= width * 8 -> width + (distance - width) / 7
                                    else -> width + width * 8 / 7
                                }
                            scope.launch { shift.snapTo(resisted + shadow) }
                        }
                        if (!change.pressed) {
                            if (gesture == SongSwipeDeleteMotion.Gesture.SWIPE_DELETE) {
                                val opened = distance > width / 4
                                openChange(if (opened) key else null)
                                activity(opened)
                                scope.launch {
                                    shift.animateTo(
                                        if (opened) width + shadow else 0f,
                                        tween(
                                            if (!opened || distance > width) 200 else 100,
                                            easing =
                                                com.smartisan.music.ui.components
                                                    .SmartisanEaseInOut,
                                        ),
                                    )
                                }
                            }
                            finished = true
                            break
                        }
                    }
                } finally {
                    if (!finished && gesture == SongSwipeDeleteMotion.Gesture.SWIPE_DELETE) {
                        openChange(null)
                        activity(false)
                        scope.launch {
                            shift.animateTo(
                                0f,
                                tween(
                                    200,
                                    easing = com.smartisan.music.ui.components.SmartisanEaseInOut,
                                ),
                            )
                        }
                    }
                }
            }
        }
    ) {
        if (shift.value > 0f) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Box(Modifier.matchParentSize()) {
                Image(
                    rememberSmartisanDrawablePainter(
                        R.drawable.remove_playlist_selector,
                        pressed = pressed,
                    ),
                    stringResource(R.string.delete),
                    Modifier.align(androidx.compose.ui.AbsoluteAlignment.CenterLeft)
                        .width(with(density) { width.toDp() })
                        .fillMaxHeight()
                        .clickable(
                            interaction,
                            null,
                            enabled = openKey == key,
                            role = Role.Button,
                            onClick = smartisanClick { onDelete(::close) },
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.None,
                )
            }
        }
        Box(
            Modifier.layout { measurable, constraints ->
                    val child =
                        measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minWidth + shadow,
                                maxWidth = constraints.maxWidth + shadow,
                            )
                        )
                    layout(constraints.maxWidth, child.height) { child.place(-shadow, 0) }
                }
                .graphicsLayer { translationX = shift.value }
        ) {
            content()
        }
    }
}
