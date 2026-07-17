package com.smartisan.music.playback

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisan.music.R
import com.smartisan.music.data.library.LibraryIndexDatabase
import com.smartisan.music.data.library.LibraryIndexEntity
import com.smartisan.music.data.library.LibraryIndexSnapshotEntity
import com.smartisan.music.data.playback.PlaybackStatsRecord
import com.smartisan.music.platform.media.audioMediaCollectionUri
import com.smartisan.music.platform.text.HanLatinTransliterator
import java.io.File
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class LocalAudioLibrary(
    private val context: Context,
    private val playbackStatsProvider: () -> Map<String, PlaybackStatsRecord> = { emptyMap() },
    private val playbackStatsByIdsProvider: (Set<String>) -> Map<String, PlaybackStatsRecord> = { mediaIds ->
        playbackStatsProvider().filterKeys(mediaIds::contains)
    },
    private val libraryIndexDatabase: LibraryIndexDatabase = LibraryIndexDatabase.getInstance(context),
) {

    private val audioCacheLock = Any()
    private val libraryIndexDao = libraryIndexDatabase.libraryIndexDao()
    @Volatile private var audioCache = AudioCache()

    fun getRootItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(context.getString(R.string.library_root))
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build()

        return MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(metadata)
            .build()
    }

    fun getAudioItems(forceRefresh: Boolean = false): List<MediaItem> {
        val currentSnapshot = currentMediaStoreSnapshot()
        synchronized(audioCacheLock) {
            val cache = audioCache
            // generation 负责日常增量变化，version 负责更大范围的媒体库重建。
            if (!forceRefresh && cache.snapshot == currentSnapshot && cache.items.isNotEmpty()) {
                return cache.items
            }
        }

        if (!forceRefresh) {
            val indexedItems = getAudioItemsFromIndexIfFresh(currentSnapshot)
            if (indexedItems.isNotEmpty()) {
                synchronized(audioCacheLock) {
                    audioCache = AudioCache(
                        snapshot = currentSnapshot,
                        items = indexedItems,
                    )
                }
                return indexedItems
            }
        }

        return reconcileAudioIndexAndCache(currentSnapshot)
    }

    fun getAudioItemsByIds(mediaIds: List<String>): List<MediaItem> {
        val requestedIds = mediaIds
            .asSequence()
            .mapNotNull { mediaId -> mediaId.trim().toLongOrNull()?.takeIf { it > 0L } }
            .distinct()
            .toList()
        if (requestedIds.isEmpty()) {
            return emptyList()
        }

        val requestedIdStrings = requestedIds.map(Long::toString)
        val cachedItemsById = getValidCachedItemsById(requestedIdStrings.toSet())
        val missingIds = requestedIds.filter { id -> id.toString() !in cachedItemsById }
        if (missingIds.isEmpty()) {
            return requestedIdStrings.mapNotNull(cachedItemsById::get)
        }
        if (!canReadLibraryIndexOnCurrentThread()) {
            return requestedIdStrings.mapNotNull(cachedItemsById::get)
        }

        val missingIdStrings = missingIds.map(Long::toString)
        val indexFresh = isAudioIndexFresh(currentMediaStoreSnapshot())
        val indexedItemsById = if (indexFresh) {
            libraryIndexDao.getValidIndexesByMediaIds(missingIdStrings)
                .toMediaItems(playbackStatsForIds(missingIdStrings.toSet()))
                .associateBy(MediaItem::mediaId)
        } else {
            emptyMap()
        }
        val stillMissingIds = missingIds.filter { id -> id.toString() !in indexedItemsById }
        val playbackStats = playbackStatsForIds(stillMissingIds.map(Long::toString).toSet())
        val queriedItemsById = queryAudioIndexesByIds(stillMissingIds)
            .toMediaItems(playbackStats)
            .associateBy(MediaItem::mediaId)
        val itemsById = cachedItemsById + indexedItemsById + queriedItemsById

        return requestedIdStrings.mapNotNull(itemsById::get)
    }

    fun getAudioItemsByQueueKeys(queueKeys: List<PlaybackQueueSnapshotItem>): List<MediaItem> {
        if (queueKeys.isEmpty()) {
            return emptyList()
        }
        val byIds = getAudioItemsByIds(queueKeys.map(PlaybackQueueSnapshotItem::mediaId))
            .associateBy(MediaItem::mediaId)
            .toMutableMap()
        val missingStableKeys = queueKeys
            .asSequence()
            .filter { key -> key.mediaId !in byIds }
            .map(PlaybackQueueSnapshotItem::stableKey)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (missingStableKeys.isNotEmpty() && canReadLibraryIndexOnCurrentThread()) {
            val currentSnapshot = currentMediaStoreSnapshot()
            if (!isAudioIndexFresh(currentSnapshot)) {
                reconcileAudioIndexAndCache(currentSnapshot)
            }
            val stableItems = libraryIndexDao.getValidIndexesByStableKeys(missingStableKeys)
                .toMediaItems()
            stableItems.forEach { item ->
                byIds[item.mediaId] = item
            }
            val stableItemsByKey = stableItems.associateBy { item -> item.stableKey.orEmpty() }
            return queueKeys.mapNotNull { key ->
                byIds[key.mediaId] ?: stableItemsByKey[key.stableKey]
            }
        }
        return queueKeys.mapNotNull { key -> byIds[key.mediaId] }
    }

    fun invalidateAudioItems() {
        synchronized(audioCacheLock) {
            audioCache = AudioCache()
        }
    }

    fun refreshAudioItems(): RefreshResult {
        val indexedSnapshot = queryIndexedAudioSnapshot()
        val pendingAudioPaths = discoverUnindexedAudioPaths(indexedSnapshot)
        val scanResult = scanAudioFiles(pendingAudioPaths)
        val mediaStoreRefreshSucceeded = requestMediaStoreRefresh()
        val items = getAudioItems(forceRefresh = true)
        return RefreshResult(
            items = items,
            scannedFileCount = scanResult.scannedFileCount,
            failedScanCount = scanResult.failedScanCount,
            scanTimedOut = scanResult.timedOut,
            mediaStoreRefreshSucceeded = mediaStoreRefreshSucceeded,
        )
    }

    fun getItem(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) {
            return getRootItem()
        }
        return getAudioItemsByIds(listOf(mediaId)).firstOrNull()
    }

    private fun getAudioItemsFromIndexIfFresh(currentSnapshot: MediaStoreSnapshot): List<MediaItem> {
        if (!isAudioIndexFresh(currentSnapshot)) {
            return emptyList()
        }
        return libraryIndexDao.getValidIndexes().toMediaItems()
    }

    private fun isAudioIndexFresh(currentSnapshot: MediaStoreSnapshot): Boolean {
        val snapshotKey = libraryIndexDao.getSnapshotKey() ?: return false
        return snapshotKey == currentSnapshot.storageKey && libraryIndexDao.getValidIndexCount() > 0
    }

    private fun canReadLibraryIndexOnCurrentThread(): Boolean {
        return Looper.myLooper() != Looper.getMainLooper()
    }

    private fun reconcileAudioIndexAndCache(currentSnapshot: MediaStoreSnapshot): List<MediaItem> {
        val items = reconcileAudioIndex(currentSnapshot)
        synchronized(audioCacheLock) {
            audioCache = AudioCache(
                snapshot = currentSnapshot,
                items = items,
            )
        }
        return items
    }

    private fun reconcileAudioIndex(currentSnapshot: MediaStoreSnapshot): List<MediaItem> {
        val indexedAt = System.currentTimeMillis()
        val existingIndexes = libraryIndexDao.getAllIndexes()
        val existingByStableKey = existingIndexes.associateBy(LibraryIndexEntity::stableKey)
        val existingByMediaId = existingIndexes.associateBy(LibraryIndexEntity::mediaId)
        val currentRows = queryAudioCursorRows(
            selection = audioSelection(),
            selectionArgs = null,
            sortOrder = audioSortOrder(),
        )
        val currentStableKeys = currentRows.mapTo(linkedSetOf(), AudioCursorRow::stableKey)
        val nextIndexes = currentRows.map { row ->
            val previous = existingByStableKey[row.stableKey] ?: existingByMediaId[row.mediaId]
            if (previous != null && previous.matches(row)) {
                previous.copy(
                    mediaId = row.mediaId,
                    uri = row.uri,
                    valid = true,
                )
            } else {
                row.toLibraryIndexEntity(indexedAt)
            }
        }
        val invalidStableKeys = existingIndexes
            .asSequence()
            .filter(LibraryIndexEntity::valid)
            .map(LibraryIndexEntity::stableKey)
            .filter { stableKey -> stableKey !in currentStableKeys }
            .toList()

        libraryIndexDatabase.runInTransaction {
            if (existingIndexes.isEmpty()) {
                libraryIndexDao.deleteAllIndexes()
            }
            if (nextIndexes.isNotEmpty()) {
                libraryIndexDao.upsertIndexes(nextIndexes)
            }
            invalidStableKeys.chunked(SqlBindParameterChunkSize).forEach { chunk ->
                libraryIndexDao.markInvalid(chunk, indexedAt)
            }
            libraryIndexDao.upsertSnapshot(
                LibraryIndexSnapshotEntity(
                    snapshotKey = currentSnapshot.storageKey,
                    updatedAt = indexedAt,
                ),
            )
        }

        return libraryIndexDao.getValidIndexes().toMediaItems()
    }

    private fun queryAudioIndexesByIds(mediaIds: List<Long>): List<LibraryIndexEntity> {
        if (mediaIds.isEmpty()) {
            return emptyList()
        }
        val indexedAt = System.currentTimeMillis()
        return mediaIds
            .chunked(MediaStoreIdSelectionChunkSize)
            .flatMap { ids ->
                queryAudioCursorRows(
                    selection = buildString {
                        append(audioSelection())
                        append(" AND ${MediaStore.Audio.Media._ID} IN (")
                        append(ids.joinToString(separator = ",") { "?" })
                        append(")")
                    },
                    selectionArgs = ids.map(Long::toString).toTypedArray(),
                    sortOrder = null,
                )
            }
            .map { row -> row.toLibraryIndexEntity(indexedAt) }
    }

    private fun queryAudioCursorRows(
        selection: String,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): List<AudioCursorRow> {
        val rows = mutableListOf<AudioCursorRow>()
        val legacyVolumeRoots = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            externalVolumeRoots()
        } else {
            emptySet()
        }
        try {
            context.contentResolver.query(
                audioCollection(),
                audioItemProjection(),
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.toAudioCursorRow(legacyVolumeRoots)?.let(rows::add)
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        return rows
    }

    private fun Cursor.toAudioCursorRow(
        legacyVolumeRoots: Set<ExternalVolumeRoot>,
    ): AudioCursorRow? {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val storageLocation = audioStorageLocation(legacyVolumeRoots)
        val displayName = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val stableKey = stableAudioLibraryKey(
            storageLocation.volumeName,
            storageLocation.relativePath,
            displayName,
        ) ?: return null
        val durationMs = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
        val mimeType = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
            ?.takeIf { it.isNotBlank() }

        return AudioCursorRow(
            mediaId = id.toString(),
            stableKey = stableKey,
            uri = ContentUris.withAppendedId(audioCollection(), id).toString(),
            title = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
            artist = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
            album = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
            albumArtist = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST))
            } else {
                null
            },
            albumId = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)).takeIf { it > 0L },
            durationMs = durationMs,
            track = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)).takeIf { it > 0 },
            year = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)).takeIf { it > 0 },
            volumeName = storageLocation.volumeName,
            relativePath = storageLocation.relativePath,
            displayName = displayName,
            mimeType = mimeType,
            dateAdded = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)).takeIf { it > 0L },
            dateModified = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)).takeIf { it > 0L },
            generationAdded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.GENERATION_ADDED)).takeIf { it > 0L }
            } else {
                null
            },
            generationModified = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.GENERATION_MODIFIED)).takeIf { it > 0L }
            } else {
                null
            },
        )
    }

    private fun Cursor.audioStorageLocation(
        legacyVolumeRoots: Set<ExternalVolumeRoot>,
    ): AudioStorageLocation {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return AudioStorageLocation(
                volumeName = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME))
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: StableKeyVolumeFallback,
                relativePath = normalizeLibraryRelativePath(
                    getString(getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)),
                ),
            )
        }

        val dataPath = getString(getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
        val audioFile = dataPath?.let(::File)
        val volumeRoot = audioFile?.let { file ->
            legacyVolumeRoots.firstOrNull { candidate ->
                file.absolutePath == candidate.root.absolutePath ||
                    file.absolutePath.startsWith("${candidate.root.absolutePath}${File.separator}")
            }
        }
        return AudioStorageLocation(
            volumeName = volumeRoot?.mediaStoreVolumeName ?: StableKeyVolumeFallback,
            relativePath = normalizeLibraryRelativePath(
                if (audioFile != null && volumeRoot != null) {
                    audioFile.relativePathFromVolumeRoot(volumeRoot.root)
                } else {
                    audioFile?.parentFile?.absolutePath
                },
            ),
        )
    }

    private fun AudioCursorRow.toLibraryIndexEntity(indexedAt: Long): LibraryIndexEntity {
        val normalizedTitle = title
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_song_title)
        val normalizedArtist = artist
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
            ?: context.getString(R.string.unknown_artist)
        val normalizedAlbum = album
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() }
        val normalizedAlbumArtist = albumArtist
            ?.fixLegacyMetadataEncoding()
            ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
        val titleSortKey = LegacyLibraryTitleNormalizer.normalize(normalizedTitle)
        return LibraryIndexEntity(
            mediaId = mediaId,
            stableKey = stableKey,
            uri = uri,
            title = normalizedTitle,
            artist = normalizedArtist,
            album = normalizedAlbum,
            albumArtist = normalizedAlbumArtist,
            albumId = albumId,
            durationMs = durationMs,
            track = track,
            year = year,
            volumeName = volumeName,
            relativePath = relativePath,
            displayName = displayName,
            mimeType = mimeType,
            dateAdded = dateAdded,
            dateModified = dateModified,
            generationAdded = generationAdded,
            generationModified = generationModified,
            titleSortKey = titleSortKey,
            titleSection = titleSortKey.legacyLibraryTitleSection(),
            qualityBadge = resolveAudioQualityBadge(displayName, mimeType),
            indexedAt = indexedAt,
            valid = true,
        )
    }

    private fun LibraryIndexEntity.matches(row: AudioCursorRow): Boolean {
        return stableKey == row.stableKey &&
            dateModified == row.dateModified &&
            generationModified == row.generationModified &&
            durationMs == row.durationMs &&
            relativePath == row.relativePath &&
            displayName == row.displayName &&
            mimeType == row.mimeType
    }

    private fun List<LibraryIndexEntity>.toMediaItems(
        playbackStats: Map<String, PlaybackStatsRecord>? = null,
    ): List<MediaItem> {
        val resolvedPlaybackStats = playbackStats
            ?: runCatching(playbackStatsProvider).getOrDefault(emptyMap())
        return map { index -> index.toMediaItem(resolvedPlaybackStats[index.mediaId]) }
    }

    private fun LibraryIndexEntity.toMediaItem(stats: PlaybackStatsRecord?): MediaItem {
        val playCount = stats?.playCount?.takeIf { it > 0L }
        val score = stats?.score?.takeIf { it > 0 }
        val extras = Bundle().apply {
            putString(MediaIdExtraKey, mediaId)
            putString(StableKeyExtraKey, stableKey)
            putString(TitleSortKeyExtraKey, titleSortKey)
            putString(TitleSectionExtraKey, titleSection)
            if (relativePath.isNotBlank()) {
                putString(RelativePathExtraKey, relativePath)
            }
            albumId?.let { putLong(AlbumIdExtraKey, it) }
            dateAdded?.let { putLong(DateAddedExtraKey, it) }
            generationAdded?.let { putLong(GenerationAddedExtraKey, it) }
            if (!qualityBadge.isNullOrBlank()) {
                putString(AudioQualityBadgeExtraKey, qualityBadge)
            }
            if (playCount != null) {
                putLong(PlayCountExtraKey, playCount)
            }
            if (score != null) {
                putLong(RatingExtraKey, score.toLong())
            }
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist)
            .setSubtitle(artist)
            .setDurationMs(durationMs)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)

        if (!album.isNullOrBlank()) {
            metadataBuilder.setAlbumTitle(album)
        }
        if (!albumArtist.isNullOrBlank()) {
            metadataBuilder.setAlbumArtist(albumArtist)
        }
        track?.let(metadataBuilder::setTrackNumber)
        year?.let(metadataBuilder::setReleaseYear)
        mediaId.toLongOrNull()?.let { id ->
            metadataBuilder.setArtworkUri(trackArtworkUri(id))
        }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun getValidCachedItemsById(mediaIds: Set<String>): Map<String, MediaItem> {
        if (mediaIds.isEmpty()) {
            return emptyMap()
        }
        return synchronized(audioCacheLock) {
            val cache = audioCache
            if (cache.items.isEmpty() || cache.snapshot != currentMediaStoreSnapshot()) {
                return@synchronized emptyMap()
            }
            cache.items
                .asSequence()
                .filter { item -> item.mediaId in mediaIds }
                .associateBy(MediaItem::mediaId)
        }
    }

    private fun playbackStatsForIds(mediaIds: Set<String>): Map<String, PlaybackStatsRecord> {
        if (mediaIds.isEmpty()) {
            return emptyMap()
        }
        return runCatching {
            playbackStatsByIdsProvider(mediaIds)
        }.getOrDefault(emptyMap())
    }

    companion object {
        const val ROOT_ID = "root"
        const val StableKeyExtraKey = "com.smartisan.music.extra.STABLE_KEY"
        const val MediaIdExtraKey = "com.smartisan.music.extra.MEDIA_ID"
        const val AlbumIdExtraKey = "com.smartisan.music.extra.ALBUM_ID"
        const val RelativePathExtraKey = "com.smartisan.music.extra.RELATIVE_PATH"
        const val DateAddedExtraKey = "com.smartisan.music.extra.DATE_ADDED"
        const val GenerationAddedExtraKey = "com.smartisan.music.extra.GENERATION_ADDED"
        const val TitleSortKeyExtraKey = "com.smartisan.music.extra.TITLE_SORT_KEY"
        const val TitleSectionExtraKey = "com.smartisan.music.extra.TITLE_SECTION"
        const val AudioQualityBadgeExtraKey = "com.smartisan.music.extra.AUDIO_QUALITY_BADGE"
        const val PlayCountExtraKey = "com.smartisan.music.extra.PLAY_COUNT"
        const val RatingExtraKey = "com.smartisan.music.extra.RATING"
        const val AudioQualityBadgeFlac = "flac"
        const val AudioQualityBadgeApe = "ape"
        const val AudioQualityBadgeWav = "wav"
        const val AudioQualityBadgeAiff = "aiff"
        const val AudioQualityBadgeAlac = "alac"
        const val AudioQualityBadgeCue = "cue"
        private const val MediaScannerWaitTimeoutSeconds = 30L
        private const val MediaStoreIdSelectionChunkSize = 500
        private const val SqlBindParameterChunkSize = 900
        private const val LegacyExternalVolumeName = "external"
        private const val MediaStoreFingerprintSeed = 1_125_899_906_842_597L
        private const val MediaStoreFingerprintMultiplier = 31L
        private val AudioFileExtensions = setOf(
            "aac",
            "aif",
            "aiff",
            "alac",
            "amr",
            "ape",
            "flac",
            "m4a",
            "m4b",
            "mid",
            "midi",
            "mka",
            "mp3",
            "oga",
            "ogg",
            "opus",
            "wav",
            "wma",
        )

        fun albumArtworkUri(albumId: Long): Uri {
            return ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId,
            )
        }

        fun trackArtworkUri(mediaId: Long): Uri {
            return Uri.parse("content://media/external/audio/media/$mediaId/albumart")
        }

        private fun resolveAudioQualityBadge(
            displayName: String?,
            mimeType: String?,
        ): String? {
            val extension = displayName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            val normalizedMimeType = mimeType?.lowercase(Locale.ROOT).orEmpty()

            return when {
                extension == AudioQualityBadgeFlac || normalizedMimeType.contains(AudioQualityBadgeFlac) -> {
                    AudioQualityBadgeFlac
                }
                extension == AudioQualityBadgeApe || normalizedMimeType.contains(AudioQualityBadgeApe) || normalizedMimeType.contains("monkeys-audio") -> {
                    AudioQualityBadgeApe
                }
                extension == AudioQualityBadgeWav || normalizedMimeType.contains(AudioQualityBadgeWav) || normalizedMimeType.contains("wave") -> {
                    AudioQualityBadgeWav
                }
                extension == AudioQualityBadgeAiff || extension == "aif" || normalizedMimeType.contains(AudioQualityBadgeAiff) -> {
                    AudioQualityBadgeAiff
                }
                extension == AudioQualityBadgeAlac || normalizedMimeType.contains(AudioQualityBadgeAlac) -> {
                    AudioQualityBadgeAlac
                }
                extension == AudioQualityBadgeCue || normalizedMimeType.contains(AudioQualityBadgeCue) -> {
                    AudioQualityBadgeCue
                }
                else -> null
            }
        }

    }

    data class RefreshResult(
        val items: List<MediaItem>,
        val scannedFileCount: Int,
        val failedScanCount: Int,
        val scanTimedOut: Boolean,
        val mediaStoreRefreshSucceeded: Boolean,
    ) {
        // ContentResolver.refresh() 是 provider best-effort 提示；MediaStore 不支持时会返回 false。
        // 手动重扫是否成功应以 MediaScanner 回调和重新查询结果为准，避免误报失败。
        val successful: Boolean
            get() = !scanTimedOut && failedScanCount == 0
    }

    private data class AudioCache(
        val snapshot: MediaStoreSnapshot? = null,
        val items: List<MediaItem> = emptyList(),
    )

    private fun currentMediaStoreSnapshot(): MediaStoreSnapshot {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return currentApi30MediaStoreSnapshot()
        }
        val fingerprint = currentMediaStoreFingerprint()
        val versions = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            Api29.mediaStoreVersions(context)
        } else {
            mapOf(LegacyExternalVolumeName to MediaStore.getVersion(context))
        }
        return MediaStoreSnapshot(
            versions.mapValues { (_, version) ->
                VolumeSnapshot(
                    version = version,
                    generation = fingerprint,
                )
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun currentApi30MediaStoreSnapshot(): MediaStoreSnapshot {
        val volumes = runCatching {
            MediaStore.getExternalVolumeNames(context)
        }.getOrDefault(emptySet())
        return MediaStoreSnapshot(
            volumes
                .sorted()
                .associateWith { volume ->
                    VolumeSnapshot(
                        version = MediaStore.getVersion(context, volume),
                        generation = MediaStore.getGeneration(context, volume),
                    )
                },
        )
    }

    private fun currentMediaStoreFingerprint(): Long {
        var fingerprint = MediaStoreFingerprintSeed
        runCatching {
            context.contentResolver.query(
                audioCollection(),
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.SIZE,
                ),
                audioSelection(),
                null,
                "${MediaStore.Audio.Media._ID} ASC",
            )
        }.getOrNull()?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                fingerprint = fingerprint * MediaStoreFingerprintMultiplier + cursor.getLong(idColumn)
                fingerprint = fingerprint * MediaStoreFingerprintMultiplier + cursor.getLong(dateModifiedColumn)
                fingerprint = fingerprint * MediaStoreFingerprintMultiplier + cursor.getLong(sizeColumn)
            }
        }
        return fingerprint
    }

    private data class MediaStoreSnapshot(
        val volumes: Map<String, VolumeSnapshot>,
    ) {
        val storageKey: String = volumes.entries.joinToString("\u001e") { (volume, snapshot) ->
            listOf(volume, snapshot.version, snapshot.generation.toString()).joinToString("\u001f")
        }
    }

    private data class VolumeSnapshot(
        val version: String,
        val generation: Long,
    )

    private data class IndexedAudioSnapshot(
        val fileKeys: Set<String>,
    )

    private data class ExternalVolumeRoot(
        val root: File,
        val mediaStoreVolumeName: String,
    )

    private data class AudioStorageLocation(
        val volumeName: String,
        val relativePath: String,
    )

    private data class ScanResult(
        val scannedFileCount: Int,
        val failedScanCount: Int,
        val timedOut: Boolean,
    )

    private data class AudioCursorRow(
        val mediaId: String,
        val stableKey: String,
        val uri: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val albumId: Long?,
        val durationMs: Long,
        val track: Int?,
        val year: Int?,
        val volumeName: String,
        val relativePath: String,
        val displayName: String,
        val mimeType: String?,
        val dateAdded: Long?,
        val dateModified: Long?,
        val generationAdded: Long?,
        val generationModified: Long?,
    )

    private fun audioCollection(): Uri {
        return audioMediaCollectionUri()
    }

    private fun audioItemProjection(): Array<String> {
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projection += MediaStore.Audio.Media.ALBUM_ARTIST
            projection += MediaStore.Audio.Media.GENERATION_ADDED
            projection += MediaStore.Audio.Media.GENERATION_MODIFIED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.MediaColumns.VOLUME_NAME
            projection += MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            projection += MediaStore.MediaColumns.DATA
        }
        return projection.toTypedArray()
    }

    private fun audioSelection(): String {
        return buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} > 0")
        }
    }

    private fun audioSortOrder(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${MediaStore.Audio.Media.GENERATION_ADDED} DESC"
        } else {
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        }
    }

    private fun queryIndexedAudioSnapshot(): IndexedAudioSnapshot {
        val fileKeys = linkedSetOf<String>()
        val legacyVolumeRoots = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            externalVolumeRoots()
        } else {
            emptySet()
        }
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns.VOLUME_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
        }

        runCatching {
            context.contentResolver.query(
                audioCollection(),
                projection,
                audioSelection(),
                null,
                null,
            )
        }.getOrNull()?.use { cursor ->
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val storageLocation = cursor.audioStorageLocation(legacyVolumeRoots)
                val displayName = cursor.getString(displayNameColumn)
                stableAudioLibraryKey(
                    storageLocation.volumeName,
                    storageLocation.relativePath,
                    displayName,
                )?.let(fileKeys::add)
            }
        }

        return IndexedAudioSnapshot(
            fileKeys = fileKeys,
        )
    }

    private fun discoverUnindexedAudioPaths(indexedSnapshot: IndexedAudioSnapshot): List<String> {
        val pendingPaths = linkedSetOf<String>()
        externalVolumeRoots().forEach { volumeRoot ->
            volumeRoot.root.walkTopDown()
                .onEnter { directory -> shouldEnterDirectory(volumeRoot.root, directory) }
                .onFail { _, _ -> }
                .forEach { candidate ->
                    if (
                        candidate.name.startsWith('.') ||
                        !candidate.hasAudioCandidateExtension() ||
                        !candidate.isFile ||
                        !candidate.canRead()
                    ) {
                        return@forEach
                    }
                    val relativePathFromRoot = candidate.relativeToOrNull(volumeRoot.root)
                        ?.invariantSeparatorsPath
                        ?: return@forEach
                    if (shouldSkipMediaScannerPath(relativePathFromRoot)) {
                        return@forEach
                    }
                    val relativePath = candidate.relativePathFromVolumeRoot(volumeRoot.root)
                    val key = stableAudioLibraryKey(
                        volumeName = volumeRoot.mediaStoreVolumeName,
                        relativePath = relativePath,
                        displayName = candidate.name,
                    ) ?: return@forEach
                    if (key !in indexedSnapshot.fileKeys) {
                        pendingPaths += candidate.absolutePath
                    }
                }
        }
        return pendingPaths.toList()
    }

    private fun externalVolumeRoots(): Set<ExternalVolumeRoot> {
        val storageManager = context.getSystemService(StorageManager::class.java)
        return context.getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .mapNotNull { appDir ->
                val root = volumeRootFromAppDir(appDir)?.takeIf { it.isDirectory }
                    ?: return@mapNotNull null
                val storageVolume = storageManager?.getStorageVolume(root)
                val mediaStoreVolumeName = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        storageVolume?.mediaStoreVolumeName
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        Api29.mediaStoreVolumeName(storageVolume, root)
                    }
                    else -> root.absolutePath
                } ?: root.absolutePath
                ExternalVolumeRoot(
                    root = root,
                    mediaStoreVolumeName = mediaStoreVolumeName,
                )
            }
            .toSet()
    }

    private fun volumeRootFromAppDir(appDir: File): File? {
        val marker = "${File.separator}Android${File.separator}data${File.separator}"
        val path = appDir.absolutePath
        val markerIndex = path.indexOf(marker)
        if (markerIndex <= 0) {
            return null
        }
        return File(path.substring(0, markerIndex))
    }

    private fun shouldEnterDirectory(scanRoot: File, directory: File): Boolean {
        if (directory == scanRoot) {
            return true
        }
        if (!directory.canRead()) {
            return false
        }
        if (directory.name.startsWith('.')) {
            return false
        }
        val relativePath = directory.relativeToOrNull(scanRoot)
            ?.invariantSeparatorsPath
            .orEmpty()
        if (shouldSkipMediaScannerPath(relativePath)) {
            return false
        }
        return when {
            relativePath == "Android/data" || relativePath.startsWith("Android/data/") -> false
            relativePath == "Android/obb" || relativePath.startsWith("Android/obb/") -> false
            else -> true
        }
    }

    private fun File.relativePathFromVolumeRoot(volumeRoot: File): String? {
        val parent = parentFile ?: return null
        if (parent == volumeRoot) {
            return null
        }
        return parent.relativeToOrNull(volumeRoot)
            ?.invariantSeparatorsPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it/" }
    }

    private fun scanAudioFiles(paths: List<String>): ScanResult {
        if (paths.isEmpty()) {
            return ScanResult(
                scannedFileCount = 0,
                failedScanCount = 0,
                timedOut = false,
            )
        }
        val scannedFileCount = AtomicInteger(0)
        val failedScanCount = AtomicInteger(0)
        var timedOut = false

        paths.chunked(128).forEach { batch ->
            val latch = CountDownLatch(batch.size)
            val acceptingCallbacks = AtomicBoolean(true)
            val completedCallbackCount = AtomicInteger(0)
            val mimeTypes = batch.map(::resolveAudioMimeType).toTypedArray()
            MediaScannerConnection.scanFile(
                context,
                batch.toTypedArray(),
                mimeTypes,
            ) { _, uri ->
                if (acceptingCallbacks.get()) {
                    completedCallbackCount.incrementAndGet()
                    if (uri == null) {
                        failedScanCount.incrementAndGet()
                    } else {
                        scannedFileCount.incrementAndGet()
                    }
                }
                latch.countDown()
            }
            if (!latch.await(MediaScannerWaitTimeoutSeconds, TimeUnit.SECONDS)) {
                acceptingCallbacks.set(false)
                timedOut = true
                failedScanCount.addAndGet(batch.size - completedCallbackCount.get())
            }
        }

        return ScanResult(
            scannedFileCount = scannedFileCount.get(),
            failedScanCount = failedScanCount.get(),
            timedOut = timedOut,
        )
    }

    private fun requestMediaStoreRefresh(): Boolean {
        return runCatching {
            context.contentResolver.refresh(
                audioCollection(),
                null,
                null,
            )
        }.getOrDefault(false)
    }

    private fun File.hasAudioCandidateExtension(): Boolean {
        val extension = extension.lowercase(Locale.ROOT)
        return extension in AudioFileExtensions
    }

    private fun resolveAudioMimeType(path: String): String? {
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        if (extension.isEmpty()) {
            return null
        }
        return when (extension) {
            AudioQualityBadgeApe -> "audio/ape"
            AudioQualityBadgeAlac -> "audio/alac"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private object Api29 {
    fun mediaStoreVersions(context: Context): Map<String, String> {
        return runCatching {
            MediaStore.getExternalVolumeNames(context)
                .sorted()
                .associateWith { volume -> MediaStore.getVersion(context, volume) }
                .takeIf(Map<String, String>::isNotEmpty)
        }.getOrNull() ?: mapOf(MediaStore.VOLUME_EXTERNAL to MediaStore.getVersion(context))
    }

    fun mediaStoreVolumeName(storageVolume: StorageVolume?, root: File): String {
        return when {
            storageVolume?.isPrimary == true -> MediaStore.VOLUME_EXTERNAL_PRIMARY
            !storageVolume?.uuid.isNullOrBlank() -> storageVolume.uuid.orEmpty().lowercase(Locale.ROOT)
            else -> root.absolutePath
        }
    }
}

private object LegacyLibraryTitleNormalizer {
    private val combiningMarks = "\\p{Mn}+".toRegex()

    @Synchronized
    fun normalize(title: String): String {
        val trimmed = title.trim()
        val transliterated = HanLatinTransliterator.transliterate(trimmed)
        return Normalizer.normalize(transliterated, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .trim()
    }
}

internal fun String.fixLegacyMetadataEncoding(): String {
    if (isEmpty() || !looksLikeLegacyMojibake()) {
        return this
    }
    return LegacyMetadataRepairCharsets.firstNotNullOfOrNull { charset ->
        repairLegacyMojibake(charset)
    } ?: this
}

private fun String.repairLegacyMojibake(charset: Charset): String? {
    return toByteArrayOrNull(charset)
        ?.let { bytes -> String(bytes, StandardCharsets.UTF_8) }
        ?.takeIf { repaired ->
            repaired.isNotEmpty() &&
                repaired != this &&
                ReplacementCharacter !in repaired &&
                (repaired.containsCjkOrFullWidth() || !repaired.looksLikeLegacyMojibake())
        }
}

private fun String.toByteArrayOrNull(charset: Charset): ByteArray? {
    return runCatching {
        val encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buffer = encoder.encode(CharBuffer.wrap(this))
        ByteArray(buffer.remaining()).also(buffer::get)
    }.getOrNull()
}

private fun String.looksLikeLegacyMojibake(): Boolean {
    return LegacyMojibakeMarkers.any(::contains)
}

private fun String.containsCjkOrFullWidth(): Boolean {
    return any { char ->
        val code = char.code
        code in 0x3040..0x30FF ||
            code in 0x3400..0x9FFF ||
            code in 0x1100..0x11FF ||
            code in 0xAC00..0xD7AF ||
            code in 0xFF01..0xFFEF
    }
}

private val LegacyMetadataRepairCharsets = listOf(
    StandardCharsets.ISO_8859_1,
    Charset.forName("windows-1252"),
    Charset.forName("windows-1250"),
)

private val LegacyMojibakeMarkers = listOf("Ã", "Â", "ã", "ď", "ï", "æ", "å", "¤", "½", "ž")
private const val ReplacementCharacter = '\uFFFD'

private fun String.legacyLibraryTitleSection(): String {
    val firstLetter = firstOrNull { char ->
        char.isLetterOrDigit()
    } ?: return "#"
    val upper = firstLetter.uppercaseChar()
    return if (upper in 'A'..'Z') {
        upper.toString()
    } else {
        "#"
    }
}

internal fun stableAudioLibraryKey(
    volumeName: String?,
    relativePath: String?,
    displayName: String?,
): String? {
    val normalizedDisplayName = displayName?.trim().orEmpty()
    if (normalizedDisplayName.isEmpty()) {
        return null
    }
    val normalizedVolumeName = volumeName
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: StableKeyVolumeFallback
    return "$normalizedVolumeName:${normalizeLibraryRelativePath(relativePath)}$normalizedDisplayName"
        .lowercase(Locale.ROOT)
}

private fun normalizeLibraryRelativePath(relativePath: String?): String {
    return relativePath
        ?.replace('\\', '/')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { if (it.endsWith('/')) it else "$it/" }
        .orEmpty()
}

internal fun shouldSkipMediaScannerPath(relativePath: String): Boolean {
    return relativePath
        .replace('\\', '/')
        .split('/')
        .filter(String::isNotEmpty)
        .any { segment -> segment.startsWith('.') }
}

private const val StableKeyVolumeFallback = "unknown-volume"
