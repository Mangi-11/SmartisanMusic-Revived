package com.smartisan.music.ui.shell.titlebar

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import kotlin.math.cos

private const val TitleBarTransitionMillis = 300
private val TitleBarLeftSlotWidth = 74.dp
private val TitleBarSlotSlideDistance = 38.dp
private const val TitleBarLeftExitFraction = 0.5f
private const val TitleBarLeftEnterStartFraction = 1f / 3f
private const val TitleBarLeftEnterDurationFraction = 0.5f
private const val TitleBarLeftPushSlideDurationFraction = 2f / 3f

private fun titleBarAccelerateDecelerate(fraction: Float): Float {
    return ((cos((fraction + 1f) * Math.PI) / 2.0) + 0.5).toFloat()
}

private fun titleBarDecelerate(fraction: Float): Float {
    val inverse = 1f - fraction
    return 1f - (inverse * inverse)
}

internal enum class TitleBarReplacementDirection {
    Push,
    Pop,
}

internal data class TitleBarLeftSlotMotion(
    val primaryAlpha: Float,
    val secondaryAlpha: Float,
    val primaryTranslationX: Float,
    val secondaryTranslationX: Float,
)

internal data class TitleBarLayerAlphas(
    val primaryAlpha: Float,
    val secondaryAlpha: Float,
)

internal fun titleBarLayerAlphas(
    progress: Float,
    direction: TitleBarReplacementDirection,
): TitleBarLayerAlphas {
    val titleProgress = progress.coerceIn(0f, 1f)
    val transitionFraction =
        when (direction) {
            TitleBarReplacementDirection.Push -> titleProgress
            TitleBarReplacementDirection.Pop -> 1f - titleProgress
        }
    return when (direction) {
        TitleBarReplacementDirection.Push ->
            TitleBarLayerAlphas(
                primaryAlpha =
                    1f -
                        titleBarAccelerateDecelerate(
                            titleBarWindowFraction(
                                fraction = transitionFraction,
                                start = 0f,
                                duration = TitleBarLeftExitFraction,
                            )
                        ),
                secondaryAlpha =
                    titleBarAccelerateDecelerate(
                        titleBarWindowFraction(
                            fraction = transitionFraction,
                            start = 0.5f,
                            duration = 0.5f,
                        )
                    ),
            )
        TitleBarReplacementDirection.Pop ->
            TitleBarLayerAlphas(
                primaryAlpha =
                    titleBarAccelerateDecelerate(
                        titleBarWindowFraction(
                            fraction = transitionFraction,
                            start = 0.5f,
                            duration = 0.5f,
                        )
                    ),
                secondaryAlpha =
                    1f -
                        titleBarDecelerate(
                            titleBarWindowFraction(
                                fraction = transitionFraction,
                                start = 0f,
                                duration = TitleBarLeftExitFraction,
                            )
                        ),
            )
    }
}

internal fun titleBarLeftSlotMotion(
    progress: Float,
    direction: TitleBarReplacementDirection,
    slidePx: Float,
): TitleBarLeftSlotMotion {
    val titleProgress = progress.coerceIn(0f, 1f)
    val transitionFraction =
        when (direction) {
            TitleBarReplacementDirection.Push -> titleProgress
            TitleBarReplacementDirection.Pop -> 1f - titleProgress
        }
    return when (direction) {
        TitleBarReplacementDirection.Push ->
            TitleBarLeftSlotMotion(
                primaryAlpha =
                    1f -
                        titleBarAccelerateDecelerate(
                            titleBarWindowFraction(
                                fraction = transitionFraction,
                                start = 0f,
                                duration = TitleBarLeftExitFraction,
                            )
                        ),
                secondaryAlpha =
                    titleBarAccelerateDecelerate(
                        titleBarWindowFraction(
                            fraction = transitionFraction,
                            start = TitleBarLeftEnterStartFraction,
                            duration = TitleBarLeftEnterDurationFraction,
                        )
                    ),
                primaryTranslationX = 0f,
                secondaryTranslationX =
                    slidePx *
                        (1f -
                            titleBarAccelerateDecelerate(
                                titleBarWindowFraction(
                                    fraction = transitionFraction,
                                    start = TitleBarLeftEnterStartFraction,
                                    duration = TitleBarLeftPushSlideDurationFraction,
                                )
                            )),
            )
        TitleBarReplacementDirection.Pop ->
            TitleBarLeftSlotMotion(
                primaryAlpha =
                    titleBarAccelerateDecelerate(
                        titleBarWindowFraction(
                            fraction = transitionFraction,
                            start = 0.5f,
                            duration = 0.5f,
                        )
                    ),
                secondaryAlpha =
                    1f -
                        titleBarDecelerate(
                            titleBarWindowFraction(
                                fraction = transitionFraction,
                                start = 0f,
                                duration = TitleBarLeftExitFraction,
                            )
                        ),
                primaryTranslationX = 0f,
                secondaryTranslationX = slidePx * titleBarDecelerate(transitionFraction),
            )
    }
}

private fun titleBarWindowFraction(
    fraction: Float,
    start: Float,
    duration: Float,
): Float {
    return ((fraction - start) / duration).coerceIn(0f, 1f)
}

@Composable
internal fun <T : Any> TitleBarTransition(
    secondaryKey: T?,
    modifier: Modifier = Modifier,
    label: String = "title bar transition",
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    primaryLeftContent: (@Composable () -> Unit)? = null,
    secondaryLeftContent: (@Composable (T) -> Unit)? = null,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable (T) -> Unit,
) {
    val progress = remember { Animatable(if (secondaryKey != null) 1f else 0f) }
    var retainedSecondaryKey by remember { mutableStateOf<T?>(secondaryKey) }
    var direction by remember {
        mutableStateOf(
            if (secondaryKey != null) {
                TitleBarReplacementDirection.Push
            } else {
                TitleBarReplacementDirection.Pop
            }
        )
    }

    LaunchedEffect(secondaryKey) {
        if (secondaryKey != null) {
            onPredictiveBackExitConsumedReset?.invoke()
            retainedSecondaryKey = secondaryKey
            direction = TitleBarReplacementDirection.Push
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = TitleBarTransitionMillis,
                        easing = LinearEasing,
                    ),
            )
        } else if (retainedSecondaryKey != null) {
            direction = TitleBarReplacementDirection.Pop
            progress.animateTo(
                targetValue = 0f,
                animationSpec =
                    tween(
                        durationMillis = TitleBarTransitionMillis,
                        easing = LinearEasing,
                    ),
            )
            retainedSecondaryKey = null
        }
    }

    val titleProgress =
        when {
            predictiveBackExitConsumed -> 0f
            predictiveBackProgress != null -> 1f - predictiveBackProgress.coerceIn(0f, 1f)
            else -> progress.value.coerceIn(0f, 1f)
        }
    val activeDirection =
        if (predictiveBackProgress != null || predictiveBackExitConsumed) {
            TitleBarReplacementDirection.Pop
        } else {
            direction
        }
    val layerAlphas =
        titleBarLayerAlphas(
            progress = titleProgress,
            direction = activeDirection,
        )
    val contentKey = secondaryKey ?: retainedSecondaryKey
    val slidePx =
        with(LocalDensity.current) {
            TitleBarSlotSlideDistance.toPx()
        }
    val leftMotion =
        titleBarLeftSlotMotion(
            progress = titleProgress,
            direction = activeDirection,
            slidePx = slidePx,
        )

    TitleBarReplacementLayers(
        modifier = modifier.clipToBounds(),
        primaryAlpha = layerAlphas.primaryAlpha,
        secondaryAlpha = layerAlphas.secondaryAlpha,
        primaryLeftAlpha = leftMotion.primaryAlpha,
        secondaryLeftAlpha = leftMotion.secondaryAlpha,
        primaryLeftTranslationX = leftMotion.primaryTranslationX,
        secondaryLeftTranslationX = leftMotion.secondaryTranslationX,
        primaryLeftContent = primaryLeftContent,
        secondaryLeftContent =
            contentKey?.let { key ->
                secondaryLeftContent?.let { content ->
                    {
                        content(key)
                    }
                }
            },
        primaryContent = primaryContent,
        secondaryContent =
            contentKey?.let { key ->
                {
                    secondaryContent(key)
                }
            },
    )
}

@Composable
internal fun TitleBarReplacementLayers(
    primaryAlpha: Float,
    secondaryAlpha: Float,
    modifier: Modifier = Modifier,
    primaryLeftAlpha: Float = primaryAlpha,
    secondaryLeftAlpha: Float = secondaryAlpha,
    primaryLeftTranslationX: Float,
    secondaryLeftTranslationX: Float,
    leftSlotWidth: Dp = TitleBarLeftSlotWidth,
    primaryLeftContent: (@Composable () -> Unit)? = null,
    secondaryLeftContent: (@Composable () -> Unit)? = null,
    primaryContent: @Composable () -> Unit,
    secondaryContent: (@Composable () -> Unit)?,
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds().background(colorResource(R.color.title_bar_background))
    ) {
        fun Modifier.titleLayer(alpha: Float): Modifier {
            return fillMaxSize().graphicsLayer {
                this.alpha = alpha
            }
        }

        Box(modifier = Modifier.titleLayer(primaryAlpha)) {
            primaryContent()
        }
        if (secondaryContent != null) {
            Box(modifier = Modifier.titleLayer(secondaryAlpha)) {
                secondaryContent()
            }
        }

        if (primaryLeftContent != null || secondaryLeftContent != null) {
            Box(
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .width(leftSlotWidth)
                        .fillMaxHeight()
                        .background(colorResource(R.color.title_bar_background))
            )

            fun Modifier.leftSlotLayer(alpha: Float, translationX: Float): Modifier {
                return width(leftSlotWidth).fillMaxHeight().graphicsLayer {
                    this.alpha = alpha
                    this.translationX = translationX
                }
            }

            Box(
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .width(leftSlotWidth)
                        .fillMaxHeight()
                        .clipToBounds()
            ) {
                if (primaryLeftContent != null) {
                    Box(
                        modifier = Modifier.leftSlotLayer(primaryLeftAlpha, primaryLeftTranslationX)
                    ) {
                        primaryLeftContent()
                    }
                }
                if (secondaryLeftContent != null) {
                    Box(
                        modifier =
                            Modifier.leftSlotLayer(secondaryLeftAlpha, secondaryLeftTranslationX)
                    ) {
                        secondaryLeftContent()
                    }
                }
            }
        }
    }
}

@Composable
internal fun TitleBarLeftIcon(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val pressed by interaction.collectIsPressedAsState()
    androidx.compose.foundation.Image(
        com.smartisan.music.ui.components.rememberSmartisanDrawablePainter(
            iconRes,
            pressed = pressed,
        ),
        androidx.compose.ui.res.stringResource(R.string.back),
        modifier
            .width(TitleBarLeftSlotWidth)
            .fillMaxHeight()
            .clickable(
                interaction,
                null,
                onClick = com.smartisan.music.ui.components.smartisanClick(onClick),
            ),
        contentScale = androidx.compose.ui.layout.ContentScale.None,
    )
}
