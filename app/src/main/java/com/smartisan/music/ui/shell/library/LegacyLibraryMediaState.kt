package com.smartisan.music.ui.shell.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import com.smartisan.music.playback.LocalPlaybackBrowser
import com.smartisan.music.playback.LocalPlaybackLibraryChildrenVersion
import com.smartisan.music.playback.await
import com.smartisan.music.ui.components.hasAudioPermission

@Composable
internal fun rememberLegacyLibraryMediaState(
    loadRequested: Boolean,
    libraryRefreshVersion: Int = 0,
): LegacyLibraryMediaState {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val libraryChildrenVersion = LocalPlaybackLibraryChildrenVersion.current
    val hasPermission = hasAudioPermission(context)
    var state by remember(browser) { mutableStateOf(LegacyLibraryMediaState()) }

    LaunchedEffect(browser, hasPermission, loadRequested, libraryRefreshVersion, libraryChildrenVersion) {
        if (!loadRequested) {
            return@LaunchedEffect
        }
        val playbackBrowser = browser ?: run {
            state = LegacyLibraryMediaState(loaded = true)
            return@LaunchedEffect
        }
        if (!hasPermission) {
            state = LegacyLibraryMediaState(loaded = true)
            return@LaunchedEffect
        }
        val rootItem = playbackBrowser.getLibraryRoot(null).await(context).value ?: run {
            state = LegacyLibraryMediaState(loaded = true)
            return@LaunchedEffect
        }
        state = LegacyLibraryMediaState(
            items = playbackBrowser.getChildren(rootItem.mediaId, 0, Int.MAX_VALUE, null)
                .await(context)
                .value
                ?.toList()
                .orEmpty(),
            loaded = true,
        )
    }

    return state
}

internal data class LegacyLibraryMediaState(
    val items: List<MediaItem> = emptyList(),
    val loaded: Boolean = false,
)
