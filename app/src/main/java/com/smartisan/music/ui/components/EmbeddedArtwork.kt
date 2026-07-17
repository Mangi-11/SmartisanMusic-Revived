package com.smartisan.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MediaItem
import com.smartisan.music.playback.NowPlayingArtworkRepository

suspend fun loadEmbeddedArtwork(
    context: Context,
    mediaItem: MediaItem,
): ImageBitmap? = loadArtworkThumbnail(context, mediaItem, ArtworkThumbnailSize)

suspend fun loadArtwork(
    context: Context,
    mediaItem: MediaItem,
): ImageBitmap? = NowPlayingArtworkRepository.load(context, mediaItem, ArtworkFullSize)
    ?.toPreparedImageBitmap()

suspend fun loadArtworkThumbnail(
    context: Context,
    mediaItem: MediaItem,
    size: Size,
): ImageBitmap? = NowPlayingArtworkRepository.load(context, mediaItem, size)
    ?.toPreparedImageBitmap()

fun peekArtworkThumbnail(
    mediaItem: MediaItem,
    size: Size = ArtworkThumbnailSize,
): ImageBitmap? = NowPlayingArtworkRepository.peek(mediaItem, size)
    ?.toPreparedImageBitmap()

private fun Bitmap.toPreparedImageBitmap(): ImageBitmap {
    return asImageBitmap().also { image ->
        image.prepareToDraw()
    }
}

private val ArtworkThumbnailSize = Size(512, 512)
private val ArtworkFullSize = Size(1024, 1024)
