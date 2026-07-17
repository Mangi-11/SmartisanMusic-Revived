package com.smartisan.music.playback

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

val LocalPlaybackBrowser = staticCompositionLocalOf<MediaBrowser?> { null }
val LocalPlaybackController = staticCompositionLocalOf<MediaController?> { null }
val LocalPlaybackLibraryChildrenVersion = staticCompositionLocalOf { 0 }

@Composable
fun ProvidePlaybackController(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val sessionToken = remember(context) {
        SessionToken(context, ComponentName(context, PlaybackService::class.java))
    }
    var libraryChildrenVersion by remember(sessionToken) {
        mutableIntStateOf(0)
    }
    val browserListener = remember(sessionToken) {
        object : MediaBrowser.Listener {
            override fun onChildrenChanged(
                browser: MediaBrowser,
                parentId: String,
                itemCount: Int,
                params: androidx.media3.session.MediaLibraryService.LibraryParams?,
            ) {
                if (parentId == LocalAudioLibrary.ROOT_ID) {
                    libraryChildrenVersion += 1
                }
            }
        }
    }
    val controllerFuture = remember(sessionToken, browserListener) {
        MediaBrowser.Builder(context, sessionToken)
            .setListener(browserListener)
            .buildAsync()
    }
    var browser by remember(controllerFuture) {
        mutableStateOf<MediaBrowser?>(null)
    }

    DisposableEffect(controllerFuture, context) {
        controllerFuture.addListener(
            {
                browser = runCatching { controllerFuture.get() }
                    .getOrNull()
                    ?.also { mediaBrowser ->
                        mediaBrowser.subscribe(LocalAudioLibrary.ROOT_ID, null)
                    }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            browser = null
            MediaController.releaseFuture(controllerFuture)
        }
    }

    CompositionLocalProvider(
        LocalPlaybackBrowser provides browser,
        LocalPlaybackController provides browser,
        LocalPlaybackLibraryChildrenVersion provides libraryChildrenVersion,
        content = content,
    )
}
