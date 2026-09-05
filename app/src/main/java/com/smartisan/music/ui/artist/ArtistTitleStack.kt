package com.smartisan.music.ui.artist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smartisan.music.ui.album.AlbumSummary
import com.smartisan.music.ui.shell.titlebar.TitleBarTransition

internal data class SelectedArtistState(
    val artist: ArtistSummary,
    val target: ArtistTarget,
    val albums: List<AlbumSummary>,
)

@Composable
internal fun ArtistTitleStack(
    selectedTarget: ArtistTarget?,
    modifier: Modifier = Modifier,
    rootPredictiveBackProgress: Float? = null,
    rootPredictiveBackExitConsumed: Boolean = false,
    onRootPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    nestedPredictiveBackProgress: Float? = null,
    nestedPredictiveBackExitConsumed: Boolean = false,
    onNestedPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    content: @Composable (ArtistTarget?, Modifier) -> Unit,
) {
    val titleEntry = selectedTarget?.toTitleStackEntry()
    TitleBarTransition(
        secondaryKey = titleEntry,
        modifier = modifier,
        label = "artist title transition",
        predictiveBackProgress = rootPredictiveBackProgress,
        predictiveBackExitConsumed = rootPredictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onRootPredictiveBackExitConsumedReset,
        primaryContent = {
            content(null, Modifier.fillMaxSize())
        },
        secondaryContent = { entry ->
            when (entry) {
                is ArtistTitleStackEntry.Direct -> {
                    content(entry.target, Modifier.fillMaxSize())
                }
                is ArtistTitleStackEntry.ArtistRoot -> {
                    val nestedTarget = selectedTarget?.takeIf { target ->
                        target.artistId == entry.artistId && target !is ArtistTarget.Albums
                    }
                    TitleBarTransition(
                        secondaryKey = nestedTarget,
                        modifier = Modifier.fillMaxSize(),
                        label = "artist nested title transition",
                        predictiveBackProgress = nestedPredictiveBackProgress,
                        predictiveBackExitConsumed = nestedPredictiveBackExitConsumed,
                        onPredictiveBackExitConsumedReset = onNestedPredictiveBackExitConsumedReset,
                        primaryContent = {
                            content(
                                ArtistTarget.Albums(
                                    artistId = entry.artistId,
                                    artistName = entry.artistName,
                                ),
                                Modifier.fillMaxSize(),
                            )
                        },
                        secondaryContent = { target ->
                            content(target, Modifier.fillMaxSize())
                        },
                    )
                }
            }
        },
    )
}

private sealed interface ArtistTitleStackEntry {
    data class ArtistRoot(
        val artistId: String,
        val artistName: String,
    ) : ArtistTitleStackEntry

    data class Direct(val target: ArtistTarget.Album) : ArtistTitleStackEntry
}

private fun ArtistTarget.toTitleStackEntry(): ArtistTitleStackEntry {
    return when (this) {
        is ArtistTarget.Album ->
            if (fromArtistAlbums) {
                ArtistTitleStackEntry.ArtistRoot(
                    artistId = artistId,
                    artistName = artistName,
                )
            } else {
                ArtistTitleStackEntry.Direct(this)
            }
        else ->
            ArtistTitleStackEntry.ArtistRoot(
                artistId = artistId,
                artistName = artistName,
            )
    }
}
