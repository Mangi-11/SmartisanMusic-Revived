package com.smartisan.music.data.genre

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import com.smartisan.music.platform.media.audioMediaCollectionUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GenreTagRepository(
    private val mediaStoreVersionProvider: () -> String,
    private val mediaStoreGenreReader: () -> Map<String, String?>,
    private val embeddedGenreReader: (MediaItem) -> String?,
) {

    constructor(context: Context) : this(
        mediaStoreVersionProvider = {
            MediaStore.getVersion(context)
        },
        mediaStoreGenreReader = {
            queryMediaStoreGenres(context)
        },
        embeddedGenreReader = { mediaItem ->
            readEmbeddedGenre(context, mediaItem)
        },
    )

    suspend fun loadGenres(mediaItems: List<MediaItem>): Map<String, String?> = withContext(Dispatchers.IO) {
        val mediaStoreVersion = mediaStoreVersionProvider()
        ensureCacheVersion(mediaStoreVersion)
        primeCacheFromMediaStore(mediaStoreVersion)

        buildMap(mediaItems.size) {
            mediaItems.forEach { item ->
                put(item.mediaId, loadGenre(mediaStoreVersion, item))
            }
        }
    }

    private fun loadGenre(
        mediaStoreVersion: String,
        mediaItem: MediaItem,
    ): String? {
        synchronized(CacheLock) {
            if (genreCache[mediaItem.mediaId] != null || mediaItem.mediaId in retrieverResolvedIds) {
                return genreCache[mediaItem.mediaId]
            }
        }

        val genre = embeddedGenreReader(mediaItem)

        synchronized(CacheLock) {
            ensureCacheVersion(mediaStoreVersion)
            genreCache[mediaItem.mediaId] = genre
            retrieverResolvedIds += mediaItem.mediaId
        }
        return genre
    }

    private fun primeCacheFromMediaStore(mediaStoreVersion: String) {
        synchronized(CacheLock) {
            if (mediaStoreCachePrimed) {
                return
            }
        }

        val resolvedGenres = runCatching {
            mediaStoreGenreReader()
        }.getOrNull() ?: return

        synchronized(CacheLock) {
            ensureCacheVersion(mediaStoreVersion)
            genreCache.putAll(resolvedGenres)
            mediaStoreCachePrimed = true
        }
    }

    private fun ensureCacheVersion(mediaStoreVersion: String) {
        synchronized(CacheLock) {
            if (cachedMediaStoreVersion == mediaStoreVersion) {
                return
            }
            cachedMediaStoreVersion = mediaStoreVersion
            genreCache.clear()
            retrieverResolvedIds.clear()
            mediaStoreCachePrimed = false
        }
    }

    companion object {
        private val CacheLock = Any()
        private var cachedMediaStoreVersion: String? = null
        private val genreCache = mutableMapOf<String, String?>()
        private val retrieverResolvedIds = mutableSetOf<String>()
        private var mediaStoreCachePrimed = false

        internal fun resetCacheForTest() {
            synchronized(CacheLock) {
                cachedMediaStoreVersion = null
                genreCache.clear()
                retrieverResolvedIds.clear()
                mediaStoreCachePrimed = false
            }
        }
    }
}

private fun queryMediaStoreGenres(context: Context): Map<String, String?> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Api30.queryGenres(context)
    } else {
        queryLegacyMediaStoreGenres(context)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private object Api30 {
    fun queryGenres(context: Context): Map<String, String?> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.AudioColumns.GENRE,
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} > 0")
        }

        val cursor = context.contentResolver.query(
            audioMediaCollectionUri(),
            projection,
            selection,
            null,
            null,
        ) ?: throw IllegalStateException("MediaStore genre query returned null cursor")

        cursor.use {
            val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val genreColumn = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.GENRE)
            if (idColumn == -1 || genreColumn == -1) {
                return emptyMap()
            }

            return buildMap {
                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idColumn).toString()
                    val genre = extractPrimaryGenre(cursor.getString(genreColumn))
                    put(mediaId, genre)
                }
            }
        }
    }
}

private fun queryLegacyMediaStoreGenres(context: Context): Map<String, String?> {
    val genres = context.contentResolver.query(
        MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Audio.Genres._ID,
            MediaStore.Audio.Genres.NAME,
        ),
        null,
        null,
        null,
    ) ?: throw IllegalStateException("MediaStore genre query returned null cursor")
    val resolvedGenres = linkedMapOf<String, String?>()

    genres.use { genreCursor ->
        val genreIdColumn = genreCursor.getColumnIndex(MediaStore.Audio.Genres._ID)
        val genreNameColumn = genreCursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
        if (genreIdColumn == -1 || genreNameColumn == -1) {
            return emptyMap()
        }

        while (genreCursor.moveToNext()) {
            val genreId = genreCursor.getLong(genreIdColumn)
            val genreName = extractPrimaryGenre(genreCursor.getString(genreNameColumn))
            val members = context.contentResolver.query(
                MediaStore.Audio.Genres.Members.getContentUri(LegacyExternalVolume, genreId),
                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                null,
                null,
                null,
            ) ?: continue
            members.use { memberCursor ->
                val audioIdColumn = memberCursor.getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID)
                if (audioIdColumn != -1) {
                    while (memberCursor.moveToNext()) {
                        resolvedGenres.putIfAbsent(
                            memberCursor.getLong(audioIdColumn).toString(),
                            genreName,
                        )
                    }
                }
            }
        }
    }

    return resolvedGenres
}

private const val LegacyExternalVolume = "external"

private fun readEmbeddedGenre(
    context: Context,
    mediaItem: MediaItem,
): String? {
    return mediaItem.localConfiguration?.uri?.let { mediaUri ->
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaUri)
                extractPrimaryGenre(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }
}

internal fun resetGenreTagRepositoryCacheForTest() {
    GenreTagRepository.resetCacheForTest()
}

internal fun extractPrimaryGenre(rawGenre: String?): String? {
    if (rawGenre.isNullOrBlank()) {
        return null
    }

    return rawGenre
        .split(';', '；', ',', '，', '|')
        .asSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
}
