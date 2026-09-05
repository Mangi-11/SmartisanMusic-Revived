package com.smartisan.music.ui.songs

import kotlin.math.abs
import kotlin.math.ceil

/** Pixel geometry deliberately retains the calibrated strip's full-height touch mapping. */
internal object QuickBarGeometry {
    fun letterAt(y: Float, height: Int, count: Int = 27): Int =
        ((y / height.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)

    fun visibleLetters(height: Int, margin: Int, minimumHeight: Int, count: Int = 27): List<Int> {
        if (count <= 1) return listOf(0)
        val available = (height - margin * 2).coerceAtLeast(1)
        val minimum = minimumHeight.coerceAtLeast(1)
        val step =
            if (available / count < minimum) {
                val slots = (available / minimum).coerceAtLeast(1)
                // Two slots made the View's division overflow and its loop run indefinitely.
                if (slots == 2) return listOf(0, count - 1)
                ceil(count / (slots.toFloat() - 2f)).toInt().coerceAtLeast(2) * 2
            } else 1
        return buildList {
            add(0)
            if (step < count / 2) {
                var index = step
                while (index < count - 1) {
                    add(index)
                    index += step
                }
            }
            if (step < count) add(count - 1)
        }
            .distinct()
    }

    fun startsDrag(dx: Float, dy: Float, minimumDistance: Int, hidden: Boolean): Boolean =
        abs(dx) >= minimumDistance && abs(dx) >= abs(dy) * .5f && (!hidden || dx < 0f)

    // The calibrated release threshold is 150 physical pixels, independent of density.
    fun expandsOnRelease(visibleWidth: Float, opening: Float, lastMoveDx: Float): Boolean =
        lastMoveDx <= 0f && visibleWidth + opening > 150f

    fun cellHeight(position: Int, height: Int, columns: Int, spacing: Int, count: Int = 27): Int {
        val rows = (count + columns - 1) / columns
        val available = (height - (rows - 1) * spacing).coerceAtLeast(0)
        val base = available / rows
        val remainder = available - base * rows
        return (base + if (position >= count - columns) remainder else 0).coerceAtLeast(1)
    }
}
