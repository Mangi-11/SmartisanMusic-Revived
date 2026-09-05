package com.smartisan.music.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.smartisan.music.playback.PlaybackSleepTimerState

@Composable
internal fun PlaybackMoreActionOverlays(
    showMorePanel: Boolean,
    favoriteEnabled: Boolean,
    currentVisualPage: PlaybackVisualPage,
    scratchEnabled: Boolean,
    sleepTimerActive: Boolean,
    addToPlaylistEnabled: Boolean,
    shareEnabled: Boolean,
    showSleepTimerDialog: Boolean,
    sleepTimerState: PlaybackSleepTimerState,
    bottomInsetPx: Int,
    onAddToPlaylistClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onLyricsToggle: () -> Unit,
    onScratchToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismissMorePanel: () -> Unit,
    onSleepTimerDismiss: () -> Unit,
    onSleepTimerDurationSelected: (Long) -> Unit,
) {
    PlaybackMoreActionsOverlay(
        visible = showMorePanel,
        favoriteEnabled = favoriteEnabled,
        visualPage = currentVisualPage,
        scratchEnabled = scratchEnabled,
        sleepTimerActive = sleepTimerActive,
        addToPlaylistEnabled = addToPlaylistEnabled,
        shareEnabled = shareEnabled,
        callbacks =
            PlaybackMoreActionCallbacks(
                onAddToPlaylistClick = onAddToPlaylistClick,
                onAddToQueueClick = onAddToQueueClick,
                onFavoriteToggle = onFavoriteToggle,
                onShareClick = onShareClick,
                onLyricsToggle = onLyricsToggle,
                onSleepTimerClick = onSleepTimerClick,
                onScratchToggle = onScratchToggle,
                onDeleteClick = onDeleteClick,
                onDismissRequest = onDismissMorePanel,
            ),
        modifier = Modifier.fillMaxSize().zIndex(8f),
    )

    PlaybackSleepTimerDialog(
        visible = showSleepTimerDialog,
        state = sleepTimerState,
        bottomInsetPx = bottomInsetPx,
        onDismissRequest = onSleepTimerDismiss,
        onDurationSelected = onSleepTimerDurationSelected,
        modifier = Modifier.fillMaxSize().zIndex(9f),
    )
}
