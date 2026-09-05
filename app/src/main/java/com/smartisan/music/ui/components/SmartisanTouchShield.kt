package com.smartisan.music.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Place before interactive siblings to block the surface behind their unoccupied areas. */
@Composable
internal fun BoxScope.SmartisanTouchShield() {
    Spacer(
        Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        }
    )
}
