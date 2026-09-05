package com.smartisan.music.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.painter.Painter

/** A drawable background fills the measured bounds without contributing layout constraints. */
internal fun Modifier.smartisanPainterBackground(painter: Painter): Modifier = drawBehind {
    with(painter) { draw(size = size) }
}
