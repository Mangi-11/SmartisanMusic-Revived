package com.smartisan.music.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.integerResource
import com.smartisan.music.R

@Stable
internal class LibraryListEntrance(val duration: Int) {
    val elapsed = Animatable(0f)
    var complete by mutableStateOf(true)
}

internal fun Modifier.libraryListEntrance(state: LibraryListEntrance, order: () -> Int): Modifier =
    graphicsLayer {
        val fraction =
            if (state.complete) 1f
            else
                ((state.elapsed.value - order().coerceAtLeast(0) * state.duration * .2f) /
                        state.duration)
                    .coerceIn(0f, 1f)
        alpha = fraction * fraction
    }

/** The existing list_anim_layout/fade_in sequence: 30 ms, 20% stagger, quadratic acceleration. */
@Composable
internal fun rememberLibraryListEntrance(
    contentKey: Any?,
    active: Boolean,
    visibleCount: () -> Int,
): LibraryListEntrance {
    val duration = integerResource(R.integer.item_flip)
    val state = remember(duration) { LibraryListEntrance(duration) }
    val preview = LocalInspectionMode.current
    var lastKey by remember { mutableStateOf<Any?>(null) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(contentKey, active) {
        if (!active || preview || (initialized && contentKey == lastKey && state.complete))
            return@LaunchedEffect
        state.complete = false
        state.elapsed.snapTo(0f)
        withFrameNanos {}
        val total = duration + (visibleCount() - 1).coerceAtLeast(0) * (duration * .2f).toInt()
        state.elapsed.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
        state.complete = true
        lastKey = contentKey
        initialized = true
    }
    return state
}
