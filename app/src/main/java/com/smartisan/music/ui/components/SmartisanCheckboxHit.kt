package com.smartisan.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Matches the calibrated checkbox hit rectangle, including the system touch-slop inset. */
internal fun smartisanCheckboxHit(point: Offset, bounds: Rect?, touchSlop: Float): Boolean {
    if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) return false
    val slop = touchSlop.coerceAtLeast(0f)
    return point.x >= bounds.left - slop &&
        point.x < bounds.right + slop &&
        point.y >= bounds.top - slop &&
        point.y < bounds.bottom + slop
}

/** Reports only the displayed checkbox, and removes its hit target when its composition leaves. */
@Composable
internal fun Modifier.smartisanCheckboxBounds(onBounds: (Rect?) -> Unit): Modifier {
    val latestBounds by rememberUpdatedState(onBounds)
    DisposableEffect(Unit) { onDispose { latestBounds(null) } }
    return onGloballyPositioned { latestBounds(it.boundsInRoot()) }
}
