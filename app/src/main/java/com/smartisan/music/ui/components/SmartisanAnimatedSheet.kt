package com.smartisan.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal fun SmartisanAnimatedSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showDuration: Int = 300,
    hideDuration: Int = showDuration,
    scrim: Color = Color(0x99000000),
    hiddenPanelAlpha: Float = 1f,
    easing: Easing = Easing { 1f - (1f - it) * (1f - it) },
    scrimEasing: Easing = easing,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(visible, onDismiss)
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    AnimatedVisibility(
        transition,
        modifier,
        enter = fadeIn(tween(showDuration, easing = scrimEasing)),
        exit = fadeOut(tween(hideDuration, easing = scrimEasing)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(scrim)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
        ) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter =
                            slideInVertically(tween(showDuration, easing = easing)) { it } +
                                fadeIn(
                                    tween(showDuration, easing = easing),
                                    initialAlpha = hiddenPanelAlpha,
                                ),
                        exit =
                            slideOutVertically(tween(hideDuration, easing = easing)) { it } +
                                fadeOut(
                                    tween(hideDuration, easing = easing),
                                    targetAlpha = hiddenPanelAlpha,
                                ),
                    )
                    .clickable(remember { MutableInteractionSource() }, null, onClick = {}),
                content = content,
            )
        }
    }
}
