package com.smartisan.music.ui.shell.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smartisan.music.R
import com.smartisan.music.ui.components.SmartisanDeleteConfirmation

@Composable
internal fun SongDeleteConfirmOverlay(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartisanDeleteConfirmation(
        stringResource(R.string.delete_song_title_text),
        onDismiss,
        onConfirm,
        modifier,
    )
}
