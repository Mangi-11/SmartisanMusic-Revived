package com.smartisan.music.playback

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import android.util.Size
import androidx.media3.common.MediaItem
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

internal object NowPlayingArtworkRepository {
    private val cache = object : LruCache<ArtworkCacheKey, Bitmap>(artworkCacheSizeKb()) {
        override fun sizeOf(key: ArtworkCacheKey, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }
    private val missingIdentities = LruCache<ArtworkRequestKey, Long>(MissingArtworkIdentityCacheSize)
    private val inFlightLoads = ConcurrentHashMap<ArtworkCacheKey, Deferred<Bitmap?>>()
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun peek(
        mediaItem: MediaItem,
        size: Size,
        allowAnySize: Boolean = true,
    ): Bitmap? {
        val identity = mediaItem.artworkRequestKey()
        val exactKey = ArtworkCacheKey(identity, size.width, size.height)
        cache.get(exactKey)?.let { return it }
        if (!allowAnySize) {
            return null
        }
        return cache.snapshot()
            .asSequence()
            .filter { (key, _) -> key.identity == identity }
            .map { (_, bitmap) -> bitmap }
            .maxByOrNull { bitmap -> bitmap.width * bitmap.height }
    }

    suspend fun load(
        context: Context,
        mediaItem: MediaItem,
        size: Size,
        rememberMissing: Boolean = true,
    ): Bitmap? {
        val appContext = context.applicationContext
        val identity = mediaItem.artworkRequestKey()
        val cacheKey = ArtworkCacheKey(identity, size.width, size.height)
        cache.get(cacheKey)?.let { return it }
        val reusableBitmap = peek(mediaItem, size)
        if (isRecentlyMissing(identity)) {
            return reusableBitmap
        }

        val bitmap = loadCoalesced(cacheKey) {
            loadBitmap(appContext, mediaItem, size)
        }?.also { loaded ->
            loaded.prepareToDraw()
            cache.put(cacheKey, loaded)
            missingIdentities.remove(identity)
        }
        if (bitmap == null && rememberMissing) {
            missingIdentities.put(identity, SystemClock.elapsedRealtime())
        }
        return bitmap ?: reusableBitmap
    }

    suspend fun preload(context: Context, mediaItem: MediaItem) {
        for (size in NowPlayingArtworkPreloadSizes) {
            load(
                context = context,
                mediaItem = mediaItem,
                size = size,
                rememberMissing = false,
            )
        }
    }

    private suspend fun loadBitmap(
        context: Context,
        mediaItem: MediaItem,
        size: Size,
    ): Bitmap? {
        val metadata = mediaItem.mediaMetadata
        return decodeArtworkData(metadata.artworkData, size)
            ?: loadArtworkBitmapSync(context, mediaItem, size)
    }

    private fun isRecentlyMissing(identity: ArtworkRequestKey): Boolean {
        val missingAtMs = missingIdentities.get(identity) ?: return false
        return SystemClock.elapsedRealtime() - missingAtMs < MissingArtworkCooldownMs
    }

    private suspend fun loadCoalesced(
        cacheKey: ArtworkCacheKey,
        loader: suspend () -> Bitmap?,
    ): Bitmap? {
        val newLoad = loadScope.async(start = CoroutineStart.LAZY) {
            cache.get(cacheKey) ?: loader()
        }
        val activeLoad = inFlightLoads.putIfAbsent(cacheKey, newLoad)
        val load = activeLoad ?: newLoad.also { pendingLoad ->
            pendingLoad.invokeOnCompletion {
                inFlightLoads.remove(cacheKey, pendingLoad)
            }
            pendingLoad.start()
        }
        if (activeLoad != null) {
            newLoad.cancel()
        }
        return load.await()
    }
}

private data class ArtworkCacheKey(
    val identity: ArtworkRequestKey,
    val width: Int,
    val height: Int,
)

private fun artworkCacheSizeKb(): Int {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    return (maxMemoryKb / 16).coerceAtLeast(4 * 1024)
}

private val NowPlayingArtworkPreloadSizes = listOf(
    Size(512, 512),
    Size(128, 128),
)
private const val MissingArtworkIdentityCacheSize = 128
private const val MissingArtworkCooldownMs = 5 * 60 * 1000L
