package com.smartisan.music.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import com.smartisan.music.R
import com.smartisan.music.playback.PlaybackSleepTimerState

@Composable
internal fun PlaybackMoreActionOverlays(
    showMorePanel: Boolean,
    favoriteEnabled: Boolean,
    currentVisualPage: PlaybackVisualPage,
    scratchEnabled: Boolean,
    sleepTimerActive: Boolean,
    addToPlaylistEnabled: Boolean,
    showSleepTimerDialog: Boolean,
    sleepTimerState: PlaybackSleepTimerState,
    bottomInsetPx: Int,
    showSetRingtoneDialog: Boolean,
    showWriteSettingsDialog: Boolean,
    onAddToPlaylistClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSetRingtoneClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onLyricsToggle: () -> Unit,
    onScratchToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismissMorePanel: () -> Unit,
    onSleepTimerDismiss: () -> Unit,
    onSleepTimerDurationSelected: (Long) -> Unit,
    onSetRingtoneConfirm: () -> Unit,
    onSetRingtoneDismiss: () -> Unit,
    onWriteSettingsConfirm: () -> Unit,
    onWriteSettingsDismiss: () -> Unit,
) {
    LegacyPlaybackMoreActionsOverlay(
        visible = showMorePanel,
        favoriteEnabled = favoriteEnabled,
        visualPage = currentVisualPage,
        scratchEnabled = scratchEnabled,
        sleepTimerActive = sleepTimerActive,
        addToPlaylistEnabled = addToPlaylistEnabled,
        callbacks = LegacyPlaybackMoreActionCallbacks(
            onAddToPlaylistClick = onAddToPlaylistClick,
            onAddToQueueClick = onAddToQueueClick,
            onFavoriteToggle = onFavoriteToggle,
            onLyricsToggle = onLyricsToggle,
            onSleepTimerClick = onSleepTimerClick,
            onSetRingtoneClick = onSetRingtoneClick,
            onScratchToggle = onScratchToggle,
            onDeleteClick = onDeleteClick,
            onDismissRequest = onDismissMorePanel,
        ),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(8f),
    )

    LegacyPlaybackSleepTimerDialog(
        visible = showSleepTimerDialog,
        state = sleepTimerState,
        bottomInsetPx = bottomInsetPx,
        onDismissRequest = onSleepTimerDismiss,
        onDurationSelected = onSleepTimerDurationSelected,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(9f),
    )

    if (showSetRingtoneDialog) {
        PlaybackConfirmDialog(
            title = stringResource(R.string.set_ringtone),
            message = stringResource(R.string.choise_seting_name),
            confirmText = stringResource(R.string.done),
            dismissText = stringResource(R.string.cancel),
            onConfirm = onSetRingtoneConfirm,
            onDismiss = onSetRingtoneDismiss,
        )
    }

    if (showWriteSettingsDialog) {
        PlaybackConfirmDialog(
            title = stringResource(R.string.set_ringtone),
            message = stringResource(R.string.ringtone_permission_message),
            confirmText = stringResource(R.string.continue_action),
            dismissText = stringResource(R.string.cancel),
            onConfirm = onWriteSettingsConfirm,
            onDismiss = onWriteSettingsDismiss,
        )
    }
}
