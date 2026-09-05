package com.smartisan.music.ui.playback

internal fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
