package com.smartisan.music.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.ui.playback.PlaybackPage
import kotlin.math.roundToInt

private const val PlaybackTransitionDurationMillis = 300
private const val PlaybackExitOffsetMultiplier = 1.09f

private val PlaybackOverlayEasing = Easing { fraction ->
    1f - (1f - fraction) * (1f - fraction)
}

@Composable
internal fun PlaybackOverlay(
    visible: Boolean,
    playbackSettings: PlaybackSettings,
    ratingOverrides: Map<String, Int>,
    onRequestAddToPlaylist: (List<MediaItem>) -> Unit,
    onRequestAddToQueue: (List<MediaItem>) -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onTrackRatingChanged: (String, Int) -> Unit,
    onFavoriteToggle: (MediaItem) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = playbackEnterTransition(),
        exit = playbackExitTransition(),
    ) {
        PlaybackPage(
            playbackSettings = playbackSettings,
            ratingOverrides = ratingOverrides,
            onRequestAddToPlaylist = onRequestAddToPlaylist,
            onRequestAddToQueue = onRequestAddToQueue,
            onScratchEnabledChange = onScratchEnabledChange,
            onTrackRatingChanged = onTrackRatingChanged,
            onFavoriteToggle = onFavoriteToggle,
            onCollapse = onCollapse,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun playbackEnterTransition(): EnterTransition {
    return slideInVertically(
        animationSpec =
            tween(
                durationMillis = PlaybackTransitionDurationMillis,
                easing = PlaybackOverlayEasing,
            ),
        initialOffsetY = { fullHeight -> fullHeight },
    )
}

private fun playbackExitTransition(): ExitTransition {
    return slideOutVertically(
        animationSpec =
            tween(
                durationMillis = PlaybackTransitionDurationMillis,
                easing = PlaybackOverlayEasing,
            ),
        targetOffsetY = { fullHeight ->
            (fullHeight * PlaybackExitOffsetMultiplier).roundToInt()
        },
    )
}
