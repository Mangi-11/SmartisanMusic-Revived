package com.smartisan.music.ui.shell.songs

import kotlin.math.abs

/** Resolves one touch stream before either the list or swipe-delete claims it. */
internal object LegacySongSwipeDeleteMotion {
    enum class Gesture {
        PENDING,
        SWIPE_DELETE,
        LIST_SCROLL,
    }

    fun resolve(
        current: Gesture,
        deltaX: Float,
        deltaY: Float,
        touchSlop: Float,
    ): Gesture {
        if (current != Gesture.PENDING) {
            return current
        }
        if (maxOf(abs(deltaX), abs(deltaY)) <= touchSlop) {
            return Gesture.PENDING
        }
        return if (deltaX > touchSlop && abs(deltaX) > abs(deltaY)) {
            Gesture.SWIPE_DELETE
        } else {
            Gesture.LIST_SCROLL
        }
    }
}
