package com.smartisan.music.ui.library

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
internal fun rememberLibraryMediaState(libraryRefreshVersion: Int = 0): LibraryMediaState {
    val context = LocalContext.current
    val browser = LocalPlaybackBrowser.current
    val libraryChildrenVersion = LocalPlaybackLibraryChildrenVersion.current
    val hasPermission = hasAudioPermission(context)
    var state by remember(browser) { mutableStateOf(LibraryMediaState()) }

    // 主壳建立后立即预热完整资料库，避免首次进入专辑等页面时先绘制空数据帧。
    LaunchedEffect(browser, hasPermission, libraryRefreshVersion, libraryChildrenVersion) {
        val playbackBrowser =
            browser
                ?: run {
                    state = LibraryMediaState(loaded = true)
                    return@LaunchedEffect
                }
        if (!hasPermission) {
            state = LibraryMediaState(loaded = true)
            return@LaunchedEffect
        }
        val rootItem =
            playbackBrowser.getLibraryRoot(null).await(context).value
                ?: run {
                    state = LibraryMediaState(loaded = true)
                    return@LaunchedEffect
                }
        state =
            LibraryMediaState(
                items =
                    playbackBrowser
                        .getChildren(rootItem.mediaId, 0, Int.MAX_VALUE, null)
                        .await(context)
                        .value
                        ?.toList()
                        .orEmpty(),
                loaded = true,
            )
    }

    return state
}

internal data class LibraryMediaState(
    val items: List<MediaItem> = emptyList(),
    val loaded: Boolean = false,
)
