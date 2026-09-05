package com.smartisan.music.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal const val PageStackSlideMillis = 300
private val PageStackPushEasing = Easing { fraction ->
    ((cos((fraction + 1f) * Math.PI) / 2.0) + 0.5).toFloat()
}
private val PageStackPopEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - (inverse * inverse)
}
private val PageStackVerticalEasing = Easing { fraction ->
    val inverse = 1f - fraction
    1f - (inverse * inverse * inverse)
}

internal enum class PageStackAxis {
    Horizontal,
    VerticalPush,
}

internal class PredictiveBackState internal constructor() {
    var progress: Float? by mutableStateOf(null)
        private set

    var exitConsumed: Boolean by mutableStateOf(false)
        private set

    internal fun update(progress: Float?) {
        exitConsumed = false
        this.progress = progress?.coerceIn(0f, 1f)
    }

    internal fun consumeExit() {
        progress = null
        exitConsumed = true
    }

    internal fun reset() {
        progress = null
        exitConsumed = false
    }
}

@Composable
internal fun rememberPredictiveBackState(): PredictiveBackState {
    return remember { PredictiveBackState() }
}

@Composable
internal fun PredictiveBackHandler(
    enabled: Boolean,
    state: PredictiveBackState,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled) {
        state.reset()
        onBack()
    }
}

@Composable
internal fun <T : Any> PageStackTransition(
    secondaryKey: T?,
    modifier: Modifier = Modifier,
    label: String = "page stack transition",
    axis: PageStackAxis = PageStackAxis.Horizontal,
    axisForKey: (T) -> PageStackAxis = { axis },
    secondaryDepthForKey: (T) -> Int = { 1 },
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    primaryContent: @Composable () -> Unit,
    popPrimaryContent: (@Composable (T) -> Unit)? = null,
    secondaryContent: @Composable (T) -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(false)
    }
    val hasSecondary = secondaryKey != null
    visibleState.targetState = hasSecondary
    val horizontalProgress = remember {
        Animatable(if (hasSecondary) 1f else 0f)
    }

    var retainedSecondaryKey by remember {
        mutableStateOf<T?>(secondaryKey)
    }
    var retainedAxis by remember {
        mutableStateOf(axis)
    }
    var retainedSecondaryDepth by remember {
        mutableStateOf(secondaryKey?.let(secondaryDepthForKey) ?: 0)
    }
    LaunchedEffect(secondaryKey, predictiveBackExitConsumed) {
        val effectAxis = secondaryKey?.let(axisForKey) ?: retainedAxis
        if (secondaryKey != null) {
            val nextAxis = axisForKey(secondaryKey)
            val nextDepth = secondaryDepthForKey(secondaryKey)
            val previousSecondaryKey = retainedSecondaryKey
            val isReplacingWithPop =
                previousSecondaryKey != null &&
                    previousSecondaryKey != secondaryKey &&
                    retainedAxis == PageStackAxis.Horizontal &&
                    nextDepth < retainedSecondaryDepth
            onPredictiveBackExitConsumedReset?.invoke()
            if (isReplacingWithPop && predictiveBackExitConsumed) {
                retainedSecondaryKey = secondaryKey
                retainedSecondaryDepth = nextDepth
                retainedAxis = nextAxis
                horizontalProgress.snapTo(1f)
            } else if (isReplacingWithPop) {
                horizontalProgress.snapTo(1f)
                horizontalProgress.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = PageStackSlideMillis,
                            easing = PageStackPopEasing,
                        ),
                )
                retainedSecondaryKey = secondaryKey
                retainedSecondaryDepth = nextDepth
                retainedAxis = nextAxis
                horizontalProgress.snapTo(1f)
            } else {
                retainedSecondaryKey = secondaryKey
                retainedSecondaryDepth = nextDepth
                retainedAxis = nextAxis
            }
            if (!isReplacingWithPop && retainedAxis == PageStackAxis.Horizontal) {
                horizontalProgress.snapTo(0f)
                horizontalProgress.animateTo(
                    targetValue = 1f,
                    animationSpec =
                        tween(
                            durationMillis = PageStackSlideMillis,
                            easing = PageStackPushEasing,
                        ),
                )
            }
        } else if (retainedSecondaryKey != null && effectAxis == PageStackAxis.Horizontal) {
            if (predictiveBackExitConsumed) {
                horizontalProgress.snapTo(0f)
            } else {
                horizontalProgress.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = PageStackSlideMillis,
                            easing = PageStackPopEasing,
                        ),
                )
            }
            retainedSecondaryKey = null
            retainedSecondaryDepth = 0
        } else if (retainedSecondaryKey != null) {
            delay(PageStackSlideMillis.toLong())
            retainedSecondaryKey = null
            retainedSecondaryDepth = 0
        }
    }
    LaunchedEffect(predictiveBackExitConsumed, hasSecondary) {
        if (!hasSecondary && predictiveBackExitConsumed) {
            retainedSecondaryKey = null
            retainedSecondaryDepth = 0
        }
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val nextKey = secondaryKey
        val nextAxis = nextKey?.let(axisForKey)
        val retainedKey = retainedSecondaryKey
        val replacingWithPop =
            nextKey != null &&
                retainedKey != null &&
                nextKey != retainedKey &&
                retainedAxis == PageStackAxis.Horizontal &&
                secondaryDepthForKey(nextKey) < retainedSecondaryDepth
        val activeAxis =
            if (replacingWithPop) {
                retainedAxis
            } else {
                nextAxis ?: retainedAxis
            }
        val predictiveReplacementExitConsumed = replacingWithPop && predictiveBackExitConsumed
        val activeBackProgress =
            predictiveBackProgress
                ?.takeIf { activeAxis == PageStackAxis.Horizontal }
                ?.coerceIn(0f, 1f)
        val predictiveExitConsumed = !hasSecondary && predictiveBackExitConsumed
        val enteringNewSecondary =
            hasSecondary &&
                secondaryKey != retainedSecondaryKey &&
                !replacingWithPop &&
                activeAxis == PageStackAxis.Horizontal
        val visibleProgress =
            when {
                predictiveExitConsumed -> 0f
                predictiveReplacementExitConsumed -> 1f
                activeBackProgress != null -> 1f - activeBackProgress
                replacingWithPop -> horizontalProgress.value.coerceIn(0f, 1f)
                enteringNewSecondary -> 0f
                activeAxis == PageStackAxis.Horizontal -> horizontalProgress.value.coerceIn(0f, 1f)
                else -> 0f
            }
        val primaryOffsetX =
            when {
                predictiveExitConsumed -> 0
                activeAxis == PageStackAxis.Horizontal -> (-widthPx * visibleProgress).roundToInt()
                else -> 0
            }
        val secondaryOffsetX =
            when {
                predictiveExitConsumed && activeAxis == PageStackAxis.Horizontal -> widthPx
                activeAxis == PageStackAxis.Horizontal ->
                    (widthPx * (1f - visibleProgress)).roundToInt()
                else -> 0
            }

        Box(
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    translationX = primaryOffsetX.toFloat()
                }
        ) {
            val popTargetKey = nextKey.takeIf {
                replacingWithPop && !predictiveReplacementExitConsumed
            }
            val popContent = popPrimaryContent
            if (popTargetKey != null && popContent != null) {
                popContent(popTargetKey)
            } else {
                primaryContent()
            }
        }

        val contentKey =
            when {
                predictiveExitConsumed && activeAxis == PageStackAxis.Horizontal -> null
                predictiveReplacementExitConsumed -> secondaryKey
                replacingWithPop -> retainedKey
                else -> secondaryKey ?: retainedSecondaryKey
            }
        if (contentKey != null) {
            if (activeAxis == PageStackAxis.Horizontal) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                translationX = secondaryOffsetX.toFloat()
                            }
                            .zIndex(1f)
                ) {
                    secondaryContent(contentKey)
                }
            } else {
                AnimatedVisibility(
                    visibleState = visibleState,
                    modifier = Modifier.fillMaxSize().zIndex(1f),
                    enter =
                        slideInVertically(
                            animationSpec =
                                tween(
                                    durationMillis = PageStackSlideMillis,
                                    easing = PageStackVerticalEasing,
                                ),
                            initialOffsetY = { it },
                        ),
                    exit =
                        if (predictiveExitConsumed) {
                            ExitTransition.None
                        } else {
                            slideOutVertically(
                                animationSpec =
                                    tween(
                                        durationMillis = PageStackSlideMillis,
                                        easing = PageStackVerticalEasing,
                                    ),
                                targetOffsetY = { it },
                            )
                        },
                ) {
                    secondaryContent(contentKey)
                }
            }
        }
    }
}
