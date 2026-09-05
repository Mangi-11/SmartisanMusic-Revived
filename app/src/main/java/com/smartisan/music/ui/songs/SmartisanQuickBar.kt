package com.smartisan.music.ui.songs

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.smartisan.music.R
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanTextSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class QuickBarPhase {
    Hidden,
    Dragging,
    Expanded,
}

private val QuickBarLetters = ('A'..'Z').map(Char::toString) + "#"
private val QuickBarEasing = Easing { ((cos((it + 1f) * PI) / 2.0) + .5).toFloat() }

/**
 * The strip and its pull-out grid share one drag owner; the surrounding song list stays scrollable.
 */
@Composable
internal fun SmartisanQuickBar(onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val density = LocalDensity.current
    val shadow = rememberSmartisanDrawablePainter(R.drawable.letters_bar_background_shadow)
    val background = rememberSmartisanDrawablePainter(R.drawable.letters_bar_background)
    val shadowWidth = shadow.intrinsicSize.width.roundToInt().coerceAtLeast(0)
    val stripWidth = background.intrinsicSize.width.roundToInt().coerceAtLeast(1)
    val columns = resources.getInteger(R.integer.smartisan_letterbar_gridview_column_num)
    val columnWidth = resources.getDimensionPixelSize(R.dimen.quickbar_ex_grid_column_width)
    val spacing = resources.getDimensionPixelSize(R.dimen.smartisan_quickbar_grid_item_space)
    val gridWidth = columns * columnWidth + (columns - 1) * spacing
    val minimumDrag = resources.getDimensionPixelSize(R.dimen.smartisan_quickbar_min_distance)
    val latestLetter by rememberUpdatedState(onLetter)
    val scope = rememberCoroutineScope()
    var opening by remember(gridWidth) { mutableFloatStateOf(0f) }
    var phase by remember(gridWidth) { mutableStateOf(QuickBarPhase.Hidden) }
    var touchedLetter by remember { mutableIntStateOf(-1) }
    var settling by remember { mutableStateOf<Job?>(null) }
    var inputOrigin by remember { mutableStateOf(Offset.Zero) }
    val showBackground = phase != QuickBarPhase.Hidden || touchedLetter >= 0
    val moveTo: (Boolean) -> Unit = { expand ->
        settling?.cancel()
        touchedLetter = -1
        phase = QuickBarPhase.Dragging
        settling = scope.launch {
            val target = if (expand) gridWidth.toFloat() else 0f
            Animatable(opening).animateTo(target, tween(200, easing = QuickBarEasing)) {
                opening = value
            }
            opening = target
            phase = if (expand) QuickBarPhase.Expanded else QuickBarPhase.Hidden
        }
    }
    val latestMoveTo by rememberUpdatedState(moveTo)
    val expandLabel = stringResource(R.string.quickbar_expand)
    val collapseLabel = stringResource(R.string.quickbar_collapse)
    val description = stringResource(R.string.quickbar_description)

    // Only the revealed portion participates in hit testing. In particular, the collapsed
    // shadow is decoration, so a song under that shadow still receives its normal tap.
    Layout(
        modifier =
            modifier
                .width(with(density) { (shadowWidth + stripWidth + opening).toDp() })
                .clipToBounds(),
        content = {
            Canvas(Modifier.fillMaxHeight()) {
                if (showBackground) with(shadow) { draw(size) }
            }
            Layout(
                modifier =
                    Modifier.fillMaxHeight()
                        .clipToBounds()
                        .onGloballyPositioned { inputOrigin = it.positionInRoot() }
                        .pointerInput(gridWidth, stripWidth, minimumDrag) {
                            awaitEachGesture {
                                val down =
                                    awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                val downRaw = down.position + inputOrigin
                                val openingAtDown = opening
                                val hiddenAtDown = phase == QuickBarPhase.Hidden
                                var trackingStrip = down.position.x < stripWidth
                                var dragging = false
                                var previousMoveX = downRaw.x
                                var currentMoveX = downRaw.x
                                var released = false
                                if (trackingStrip) {
                                    touchedLetter =
                                        QuickBarGeometry.letterAt(down.position.y, size.height)
                                    latestLetter(QuickBarLetters[touchedLetter])
                                    down.consume()
                                }
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change =
                                            event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            released = true
                                            if (dragging) {
                                                change.consume()
                                                latestMoveTo(
                                                    QuickBarGeometry.expandsOnRelease(
                                                        (shadowWidth + stripWidth).toFloat(),
                                                        opening,
                                                        currentMoveX - previousMoveX,
                                                    )
                                                )
                                            } else if (trackingStrip) change.consume()
                                            break
                                        }
                                        val raw = change.position + inputOrigin
                                        val dx = raw.x - downRaw.x
                                        val dy = raw.y - downRaw.y
                                        if (event.type == PointerEventType.Move) {
                                            previousMoveX = currentMoveX
                                            currentMoveX = raw.x
                                        }
                                        if (
                                            !dragging &&
                                                QuickBarGeometry.startsDrag(
                                                    dx,
                                                    dy,
                                                    minimumDrag,
                                                    hiddenAtDown,
                                                )
                                        ) {
                                            settling?.cancel()
                                            touchedLetter = -1
                                            trackingStrip = false
                                            dragging = true
                                            phase = QuickBarPhase.Dragging
                                        }
                                        if (dragging) {
                                            opening =
                                                (openingAtDown - dx).coerceIn(
                                                    0f,
                                                    gridWidth.toFloat(),
                                                )
                                            change.consume()
                                        } else if (trackingStrip) {
                                            val index =
                                                QuickBarGeometry.letterAt(
                                                    change.position.y,
                                                    size.height,
                                                )
                                            if (index != touchedLetter) {
                                                touchedLetter = index
                                                latestLetter(QuickBarLetters[index])
                                            }
                                            change.consume()
                                        }
                                    }
                                } finally {
                                    touchedLetter = -1
                                    if (dragging && !released) latestMoveTo(false)
                                }
                            }
                        },
                content = {
                    QuickBarStrip(
                        background,
                        showBackground,
                        touchedLetter,
                        Modifier.semantics {
                            contentDescription = description
                            role = Role.Button
                            onClick(
                                if (phase == QuickBarPhase.Expanded) collapseLabel else expandLabel
                            ) {
                                latestMoveTo(phase != QuickBarPhase.Expanded)
                                true
                            }
                            customActions = QuickBarLetters.map { letter ->
                                CustomAccessibilityAction(letter) {
                                    latestLetter(letter)
                                    true
                                }
                            }
                        },
                    )
                    QuickBarGrid(
                        columns,
                        columnWidth,
                        spacing,
                        enabled = phase != QuickBarPhase.Hidden,
                        onClick = { index ->
                            if (index < 26) latestLetter(QuickBarLetters[index])
                            latestMoveTo(false)
                        },
                    )
                },
            ) { measurables, constraints ->
                val height = constraints.maxHeight
                val strip = measurables[0].measure(Constraints.fixed(stripWidth, height))
                val grid = measurables[1].measure(Constraints.fixed(gridWidth, height))
                layout(constraints.maxWidth, height) {
                    strip.place(0, 0)
                    grid.place(stripWidth, 0)
                }
            }
        },
    ) { measurables, constraints ->
        val height = constraints.maxHeight
        val shadowPlaceable = measurables[0].measure(Constraints.fixed(shadowWidth, height))
        val input =
            measurables[1].measure(
                Constraints.fixed((constraints.maxWidth - shadowWidth).coerceAtLeast(1), height)
            )
        layout(constraints.maxWidth, height) {
            shadowPlaceable.place(0, 0)
            input.place(shadowWidth, 0)
        }
    }
}

@Composable
private fun QuickBarStrip(
    background: Painter,
    showBackground: Boolean,
    touched: Int,
    modifier: Modifier,
) {
    val resources = LocalResources.current
    val margin = resources.getDimensionPixelSize(R.dimen.smartisan_quickbar_letterbar_margin)
    val minimumHeight =
        resources.getDimensionPixelSize(R.dimen.letters_bar_single_letter_min_height)
    val fontSize = resources.getDimension(R.dimen.letters_bar_letter_font_size)
    val noChosen = colorResource(R.color.no_chosen_letter_font_color)
    val chosen = colorResource(R.color.has_chosen_letter_font_color)
    val highlight = rememberSmartisanDrawablePainter(R.drawable.letters_bar_highlight_icon)
    val textPaint =
        remember(fontSize) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = fontSize
            }
        }
    Canvas(modifier.fillMaxSize()) {
        if (showBackground) with(background) { draw(size) }
        val indexes =
            QuickBarGeometry.visibleLetters(size.height.roundToInt(), margin, minimumHeight)
        val available = (size.height.roundToInt() - margin * 2).coerceAtLeast(1)
        val letterHeight = max(available.toFloat() / indexes.size, minimumHeight.toFloat())
        indexes.forEachIndexed { visibleIndex, letterIndex ->
            val centerY = margin + visibleIndex * letterHeight + letterHeight / 2f
            if (letterIndex == touched) {
                val intrinsic = highlight.intrinsicSize
                translate((size.width - intrinsic.width) / 2f, centerY - intrinsic.height / 2f) {
                    with(highlight) { draw(intrinsic) }
                }
            }
            textPaint.color =
                when {
                    letterIndex == touched -> Color.White
                    showBackground -> chosen
                    else -> noChosen
                }.toArgb()
            textPaint.isFakeBoldText = letterIndex == touched
            val metrics = textPaint.fontMetricsInt
            val baseline = centerY - (metrics.bottom - metrics.top) / 2f - metrics.top
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    QuickBarLetters[letterIndex],
                    size.width / 2f,
                    baseline,
                    textPaint,
                )
            }
        }
    }
}

@Composable
private fun QuickBarGrid(
    columns: Int,
    columnWidth: Int,
    spacing: Int,
    enabled: Boolean,
    onClick: (Int) -> Unit,
) {
    val collapseDescription = stringResource(R.string.quickbar_collapse)
    Layout(
        modifier =
            Modifier.background(colorResource(R.color.quickbar_grid_background))
                .then(if (enabled) Modifier else Modifier.clearAndSetSemantics {}),
        content = {
            repeat(27) { index ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier.smartisanPainterBackground(
                            rememberSmartisanDrawablePainter(
                                if ((index / columns) % 2 == 0)
                                    R.drawable.quickbar_ex_alphabet_text_light_colorlist
                                else R.drawable.quickbar_ex_alphabet_text_dark_colorlist,
                                pressed = pressed,
                            )
                        )
                        .clickable(
                            interaction,
                            null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = smartisanClick { onClick(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index == 26)
                        Image(
                            rememberSmartisanDrawablePainter(R.drawable.letters_bar_arrow),
                            collapseDescription,
                        )
                    else
                        BasicText(
                            QuickBarLetters[index],
                            style =
                                TextStyle(
                                    color = colorResource(R.color.setting_item_summary_text_color),
                                    fontSize =
                                        smartisanTextSize(
                                            R.dimen.smartisan_quickbarex_gridview_font_size
                                        ),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                        )
                }
            }
        },
    ) { measurables, constraints ->
        val height = constraints.maxHeight
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(
                Constraints.fixed(
                    columnWidth,
                    QuickBarGeometry.cellHeight(index, height, columns, spacing),
                )
            )
        }
        val rowHeight = QuickBarGeometry.cellHeight(0, height, columns, spacing)
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(
                    (index % columns) * (columnWidth + spacing),
                    (index / columns) * (rowHeight + spacing),
                )
            }
        }
    }
}
