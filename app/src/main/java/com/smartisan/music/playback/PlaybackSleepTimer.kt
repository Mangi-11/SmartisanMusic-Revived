package com.smartisan.music.playback

import android.os.CountDownTimer
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackSleepTimerState(
    val durationMs: Long = 0L,
    val startedAtElapsedRealtimeMs: Long = 0L,
    val endsAtElapsedRealtimeMs: Long = 0L,
    val nowElapsedRealtimeMs: Long = 0L,
) {
    val remainingMs: Long
        get() = if (endsAtElapsedRealtimeMs > 0L) {
            (endsAtElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
        } else {
            0L
        }

    val isActive: Boolean
        get() = remainingMs > 0L
}

internal object PlaybackSleepTimer {
    private val mutableState = MutableStateFlow(PlaybackSleepTimerState())

    val state: StateFlow<PlaybackSleepTimerState> = mutableState.asStateFlow()

    private var countDownTimer: CountDownTimer? = null
    private var onFinishAction: (() -> Unit)? = null

    fun start(
        durationMs: Long,
        onFinish: () -> Unit,
    ) {
        if (durationMs <= 0L) {
            cancel()
            return
        }
        countDownTimer?.cancel()
        onFinishAction = onFinish
        val startedAtElapsedRealtimeMs = elapsedRealtime()
        val endsAtElapsedRealtimeMs = startedAtElapsedRealtimeMs + durationMs
        mutableState.value = createPlaybackSleepTimerState(
            durationMs = durationMs,
            startedAtElapsedRealtimeMs = startedAtElapsedRealtimeMs,
            endsAtElapsedRealtimeMs = endsAtElapsedRealtimeMs,
            nowElapsedRealtimeMs = startedAtElapsedRealtimeMs,
        )
        countDownTimer = object : CountDownTimer(durationMs, SleepTimerTickIntervalMs) {
            override fun onTick(millisUntilFinished: Long) {
                val refreshedState = createPlaybackSleepTimerState(
                    durationMs = durationMs,
                    startedAtElapsedRealtimeMs = startedAtElapsedRealtimeMs,
                    endsAtElapsedRealtimeMs = endsAtElapsedRealtimeMs,
                    nowElapsedRealtimeMs = elapsedRealtime(),
                )
                if (refreshedState.isActive) {
                    mutableState.value = refreshedState
                } else {
                    finish()
                }
            }

            override fun onFinish() {
                finish()
            }
        }.start()
    }

    fun refresh() {
        val currentState = mutableState.value
        if (currentState.endsAtElapsedRealtimeMs <= 0L) {
            return
        }
        val refreshedState = currentState.copy(nowElapsedRealtimeMs = elapsedRealtime())
        if (refreshedState.isActive) {
            mutableState.value = refreshedState
        } else {
            finish()
        }
    }

    fun cancel() {
        countDownTimer?.cancel()
        countDownTimer = null
        onFinishAction = null
        mutableState.value = PlaybackSleepTimerState()
    }

    private fun finish() {
        countDownTimer?.cancel()
        countDownTimer = null
        val finishAction = onFinishAction
        onFinishAction = null
        mutableState.value = PlaybackSleepTimerState()
        finishAction?.invoke()
    }

    private fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    private const val SleepTimerTickIntervalMs = 1_000L
}

internal fun createPlaybackSleepTimerState(
    durationMs: Long,
    startedAtElapsedRealtimeMs: Long,
    endsAtElapsedRealtimeMs: Long,
    nowElapsedRealtimeMs: Long,
): PlaybackSleepTimerState {
    return PlaybackSleepTimerState(
        durationMs = durationMs.coerceAtLeast(0L),
        startedAtElapsedRealtimeMs = startedAtElapsedRealtimeMs,
        endsAtElapsedRealtimeMs = endsAtElapsedRealtimeMs,
        nowElapsedRealtimeMs = nowElapsedRealtimeMs,
    )
}
