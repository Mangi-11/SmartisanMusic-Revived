package com.smartisan.music.ui.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.media3.common.MediaItem
import com.smartisan.music.playback.LocalAudioLibrary
import com.smartisan.music.playback.loadArtworkUriBitmap
import com.smartisan.music.ui.album.AlbumSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

internal class AlbumArtworkLoader(context: Context) {
    private val appContext = context.applicationContext
    private val audioLibrary = LocalAudioLibrary(appContext)
    private var scope = newScope()
    private val bitmapRequests = mutableMapOf<String, Deferred<Bitmap?>>()

    fun cached(album: AlbumSummary): Bitmap? =
        buildArtworkRequest(album, 1)?.let { cache.get(it.cacheKey) }

    fun cached(mediaItem: MediaItem): Bitmap? =
        buildArtworkRequest(mediaItem, 1)?.let { cache.get(it.cacheKey) }

    suspend fun load(album: AlbumSummary, sizePx: Int): Bitmap? =
        loadRequest(buildArtworkRequest(album, sizePx))

    suspend fun load(mediaItem: MediaItem, sizePx: Int): Bitmap? =
        loadRequest(buildArtworkRequest(mediaItem, sizePx))

    private suspend fun loadRequest(request: AlbumArtworkRequest?): Bitmap? =
        withContext(Dispatchers.Main.immediate) {
            if (request == null) return@withContext null
            cache
                .get(request.cacheKey)
                ?.takeIf {
                    it.width >= request.sizePx && it.height >= request.sizePx
                }
                ?.let {
                    return@withContext it
                }
            val task =
                bitmapRequests.getOrPut(request.jobKey) {
                    ensureScope()
                        .async(start = CoroutineStart.LAZY) {
                            val bitmap = withContext(Dispatchers.IO) { loadBitmap(request) }
                            if (bitmap != null) {
                                bitmap.prepareToDraw()
                                val cached = cache.get(request.cacheKey)
                                if (
                                    cached == null ||
                                        cached.width < bitmap.width ||
                                        cached.height < bitmap.height
                                ) {
                                    cache.put(request.cacheKey, bitmap)
                                }
                            }
                            bitmap
                        }
                        .also { deferred ->
                            deferred.invokeOnCompletion {
                                bitmapRequests.remove(request.jobKey, deferred)
                            }
                        }
                }
            task.await()
        }

    fun clear() {
        scope.cancel()
        bitmapRequests.clear()
    }

    private fun ensureScope(): CoroutineScope {
        if (scope.coroutineContext[Job]?.isActive != true) {
            scope = newScope()
        }
        return scope
    }

    private fun loadBitmap(request: AlbumArtworkRequest): Bitmap? {
        val size = Size(request.sizePx, request.sizePx)
        return request.artworkData?.let { artworkData ->
            runCatching {
                decodeByteArraySampled(artworkData, size)
            }
                .getOrNull()
        }
            ?: request.artworkUri?.let { artworkUri ->
                loadBitmapFromUri(artworkUri, size)
            }
            ?: request.trackArtworkUri?.let { trackArtworkUri ->
                loadBitmapFromUri(trackArtworkUri, size)
            }
            ?: request.mediaUri?.let { mediaUri ->
                loadBitmapFromUri(mediaUri, size)
            }
            ?: loadEmbeddedPicture(request.mediaItem, size)
            ?: loadResolvedMediaItemBitmap(request, size)
    }

    private fun loadBitmapFromUri(uri: Uri, size: Size): Bitmap? {
        return loadArtworkUriBitmap(appContext, uri, size)
    }

    private fun loadEmbeddedPicture(mediaItem: MediaItem, size: Size): Bitmap? {
        return runCatching {
            val mediaUri = mediaItem.localConfiguration?.uri ?: return@runCatching null
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, mediaUri)
                retriever.embeddedPicture?.let { bytes ->
                    decodeByteArraySampled(bytes, size)
                }
            } finally {
                retriever.release()
            }
        }
            .getOrNull()
    }

    private fun loadResolvedMediaItemBitmap(
        request: AlbumArtworkRequest,
        size: Size,
    ): Bitmap? {
        val mediaId = request.mediaId ?: return null
        val resolvedItem = audioLibrary.getItem(mediaId.toString()) ?: return null
        val metadata = resolvedItem.mediaMetadata
        return metadata.artworkData?.let { artworkData ->
            runCatching {
                decodeByteArraySampled(artworkData, size)
            }
                .getOrNull()
        }
            ?: metadata.artworkUri?.let { artworkUri ->
                loadBitmapFromUri(artworkUri, size)
            }
            ?: resolvedItem.localConfiguration?.uri?.let { mediaUri ->
                loadBitmapFromUri(mediaUri, size)
            }
            ?: loadEmbeddedPicture(resolvedItem, size)
    }

    private fun buildArtworkRequest(
        album: AlbumSummary,
        sizePx: Int,
    ): AlbumArtworkRequest? {
        val representative = album.representative
        val metadata = representative.mediaMetadata
        val mediaId = representative.mediaId.toLongOrNull()
        val albumId =
            metadata.extras?.getLong(LocalAudioLibrary.AlbumIdExtraKey)?.takeIf { it > 0L }
        val artworkUri = metadata.artworkUri ?: albumId?.let(LocalAudioLibrary::albumArtworkUri)
        val trackArtworkUri = mediaId?.let(LocalAudioLibrary::trackArtworkUri)
        val mediaUri = representative.localConfiguration?.uri
        val artworkData = metadata.artworkData
        if (
            artworkUri == null && trackArtworkUri == null && mediaUri == null && artworkData == null
        ) {
            return null
        }
        return AlbumArtworkRequest(
            mediaItem = representative,
            mediaId = mediaId,
            artworkUri = artworkUri,
            trackArtworkUri = trackArtworkUri,
            mediaUri = mediaUri,
            artworkData = artworkData,
            identity =
                albumId?.let { resolvedAlbumId -> "album:$resolvedAlbumId" }
                    ?: mediaId?.let { resolvedMediaId -> "track:$resolvedMediaId" }
                    ?: album.id,
            sizePx = sizePx,
        )
    }

    private fun buildArtworkRequest(
        mediaItem: MediaItem,
        sizePx: Int,
    ): AlbumArtworkRequest? {
        val metadata = mediaItem.mediaMetadata
        val mediaId = mediaItem.mediaId.toLongOrNull()
        val albumId =
            metadata.extras?.getLong(LocalAudioLibrary.AlbumIdExtraKey)?.takeIf { it > 0L }
        val artworkUri = metadata.artworkUri ?: albumId?.let(LocalAudioLibrary::albumArtworkUri)
        val trackArtworkUri = mediaId?.let(LocalAudioLibrary::trackArtworkUri)
        val mediaUri = mediaItem.localConfiguration?.uri
        val artworkData = metadata.artworkData
        if (
            artworkUri == null && trackArtworkUri == null && mediaUri == null && artworkData == null
        ) {
            return null
        }
        return AlbumArtworkRequest(
            mediaItem = mediaItem,
            mediaId = mediaId,
            artworkUri = artworkUri,
            trackArtworkUri = trackArtworkUri,
            mediaUri = mediaUri,
            artworkData = artworkData,
            identity =
                albumId?.let { resolvedAlbumId -> "album:$resolvedAlbumId" }
                    ?: mediaId?.let { resolvedMediaId -> "track:$resolvedMediaId" }
                    ?: mediaItem.localConfiguration?.uri?.toString()
                    ?: mediaItem.mediaId,
            sizePx = sizePx,
        )
    }

    private fun newScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private companion object {
        val cache =
            object : LruCache<String, Bitmap>(albumArtworkCacheSizeKb()) {
                override fun sizeOf(key: String, value: Bitmap): Int {
                    return value.byteCount / 1024
                }
            }
    }
}

private data class AlbumArtworkRequest(
    val mediaItem: MediaItem,
    val mediaId: Long?,
    val artworkUri: Uri?,
    val trackArtworkUri: Uri?,
    val mediaUri: Uri?,
    val artworkData: ByteArray?,
    val identity: String,
    val sizePx: Int,
) {
    val cacheKey: String = identity
    val jobKey: String = "$identity@$sizePx"
}

private fun decodeByteArraySampled(
    bytes: ByteArray,
    size: Size,
): Bitmap? {
    val boundsOptions =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    val sampleOptions =
        BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, size)
        }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampleOptions)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    size: Size,
): Int {
    val rawHeight = options.outHeight
    val rawWidth = options.outWidth
    val requestedHeight = size.height.coerceAtLeast(1)
    val requestedWidth = size.width.coerceAtLeast(1)
    var inSampleSize = 1

    if (rawHeight > requestedHeight || rawWidth > requestedWidth) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while (
            halfHeight / inSampleSize >= requestedHeight &&
                halfWidth / inSampleSize >= requestedWidth
        ) {
            inSampleSize *= 2
        }
    }

    return inSampleSize.coerceAtLeast(1)
}

private fun albumArtworkCacheSizeKb(): Int {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    return (maxMemoryKb / 16).coerceAtLeast(4 * 1024)
}
