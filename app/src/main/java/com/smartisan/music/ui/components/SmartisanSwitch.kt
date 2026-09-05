package com.smartisan.music.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared by the switch and its enclosing preference row so one intent produces one callback. */
@Stable
internal class SmartisanSwitchState
internal constructor(
    checked: Boolean,
    private val scope: CoroutineScope,
    private val currentChecked: () -> Boolean,
    private val currentEnabled: () -> Boolean,
    private val onCheckedChange: (Boolean) -> Unit,
    private val haptic: () -> Unit,
) {
    var position by mutableFloatStateOf(if (checked) 1f else 0f)
        private set

    var shadowAlpha by mutableFloatStateOf(0f)
        private set

    var pressed by mutableStateOf(false)
        private set

    private var settling = false
    private var disposed = false
    private var pendingTarget: Boolean? = null
    private var generation = 0
    private var animationJob: Job? = null
    private var callbackJob: Job? = null
    private var shadowJob: Job? = null

    fun begin(): Boolean {
        if (disposed || settling || !currentEnabled()) return false
        shadowJob?.cancel()
        shadowAlpha = 1f
        pressed = true
        return true
    }

    fun dragTo(fraction: Float) {
        position = fraction.coerceIn(0f, 1f)
    }

    fun toggle() {
        if (!disposed && currentEnabled()) finish(!currentChecked(), fadeShadow = false)
    }

    fun finish(target: Boolean, fadeShadow: Boolean = true) {
        pressed = false
        if (disposed || !currentEnabled()) return
        if (fadeShadow) {
            shadowJob?.cancel()
            shadowAlpha = 1f
            shadowJob = scope.launch {
                Animatable(1f).animateTo(0f, tween(200, easing = SwitchShadowEasing)) {
                    shadowAlpha = value
                }
            }
        }
        val currentGeneration = ++generation
        pendingTarget = target
        animationJob?.cancel()
        val targetPosition = if (target) 1f else 0f
        val duration = smartisanSwitchSettleMillis(position, targetPosition)
        settling = true
        animationJob = scope.launch {
            try {
                Animatable(position).animateTo(
                    targetPosition,
                    tween(duration, easing = LinearEasing),
                ) {
                    position = value
                }
            } finally {
                if (generation == currentGeneration) {
                    settling = false
                    if (pendingTarget == null) synchronize(currentChecked())
                }
            }
        }
        callbackJob?.cancel()
        callbackJob = scope.launch {
            try {
                delay(20)
                if (target != currentChecked()) {
                    onCheckedChange(target)
                    haptic()
                }
                // Allow the owner to publish its value before ending the temporary visual state.
                withFrameNanos {}
            } finally {
                if (generation == currentGeneration) {
                    pendingTarget = null
                    synchronize(currentChecked())
                }
            }
        }
    }

    fun synchronize(checked: Boolean) {
        if (!pressed && !settling && pendingTarget == null) position = if (checked) 1f else 0f
    }

    fun dispose() {
        generation++
        disposed = true
        animationJob?.cancel()
        callbackJob?.cancel()
        shadowJob?.cancel()
    }
}

@Composable
internal fun rememberSmartisanSwitchState(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): SmartisanSwitchState {
    val scope = rememberCoroutineScope()
    val latestChecked = rememberUpdatedState(checked)
    val latestEnabled = rememberUpdatedState(enabled)
    val callback = rememberUpdatedState(onCheckedChange)
    val host = LocalView.current
    val state =
        remember(scope, host) {
            SmartisanSwitchState(
                checked,
                scope,
                { latestChecked.value },
                { latestEnabled.value },
                { callback.value(it) },
                { host.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) },
            )
        }
    LaunchedEffect(checked) { state.synchronize(checked) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}

/** SwitchEx's existing 198 × 144 mask and 286 × 144 sliding artwork, rendered at /3 dp. */
@Composable
internal fun SmartisanSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SmartisanSwitchState = rememberSmartisanSwitchState(checked, enabled, onCheckedChange),
) {
    val bottom = ImageBitmap.imageResource(R.drawable.switch_ex_bottom)
    val mask = ImageBitmap.imageResource(R.drawable.switch_ex_mask)
    val frame = ImageBitmap.imageResource(R.drawable.switch_ex_frame)
    val framePressed = ImageBitmap.imageResource(R.drawable.switch_ex_frame_pressed)
    val button = ImageBitmap.imageResource(R.drawable.switch_ex_unpressed)
    val buttonPressed = ImageBitmap.imageResource(R.drawable.switch_ex_pressed)
    val currentChecked by rememberUpdatedState(checked)
    Canvas(
        modifier
            .size(66.dp, 52.dp)
            .clipToBounds()
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!enabled) disabled()
                onClick {
                    if (enabled) state.toggle()
                    enabled
                }
            }
            .pointerInput(enabled, state) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    if (!state.begin()) return@awaitEachGesture
                    val initial = if (currentChecked) 1f else 0f
                    var last = down
                    try {
                        do {
                            val event = awaitPointerEvent()
                            last = event.changes.firstOrNull { it.id == down.id } ?: break
                            state.dragTo(
                                initial + (last.position.x - down.position.x) / (88f / 3f).dp.toPx()
                            )
                            last.consume()
                        } while (last.pressed)
                    } finally {
                        // SwitchEx treats ACTION_CANCEL like release, including a
                        // threshold-crossing change.
                        state.finish(
                            smartisanSwitchTarget(
                                currentChecked,
                                state.position,
                                last.position - down.position,
                                last.uptimeMillis - down.uptimeMillis,
                                viewConfiguration.touchSlop,
                            )
                        )
                    }
                }
            }
    ) {
        val h = 48.dp.toPx().roundToInt()
        val w = 66.dp.toPx().roundToInt()
        val slideWidth = (286f / 3f).dp.toPx().roundToInt()
        val y = 2.dp.toPx().roundToInt()
        val shift = (-(88f / 3f).dp.toPx() * (1f - state.position)).roundToInt()
        val alpha = if (enabled) 1f else 191f / 255f
        drawIntoCanvas {
            it.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
        }
        drawImage(mask, dstOffset = IntOffset(0, y), dstSize = IntSize(w, h), alpha = alpha)
        drawImage(
            bottom,
            dstOffset = IntOffset(shift, y),
            dstSize = IntSize(slideWidth, h),
            alpha = alpha,
            blendMode = BlendMode.SrcIn,
        )
        drawImage(frame, dstOffset = IntOffset(0, y), dstSize = IntSize(w, h), alpha = alpha)
        if (state.shadowAlpha > 0f)
            drawImage(
                framePressed,
                dstOffset = IntOffset(0, y),
                dstSize = IntSize(w, h),
                alpha = state.shadowAlpha,
            )
        drawImage(
            if (state.shadowAlpha == 1f) buttonPressed else button,
            dstOffset = IntOffset(shift, y),
            dstSize = IntSize(slideWidth, h),
            alpha = alpha,
        )
        drawIntoCanvas { it.restore() }
    }
}

internal fun smartisanSwitchTarget(
    checked: Boolean,
    position: Float,
    delta: Offset,
    elapsedMillis: Long,
    touchSlop: Float,
): Boolean =
    if (abs(delta.x) >= touchSlop || abs(delta.y) >= touchSlop || elapsedMillis >= 300L)
        position > .5f
    else !checked

internal fun smartisanSwitchSettleMillis(position: Float, target: Float): Int =
    (abs(target - position) * (88f / 3f) / 350f * 1000f).roundToInt()

private val SwitchShadowEasing = Easing { ((cos((it + 1) * PI) / 2.0) + .5).toFloat() }
