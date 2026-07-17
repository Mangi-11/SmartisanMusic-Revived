package com.smartisan.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.media3.common.MediaItem

internal const val ExternalAudioMediaIdPrefix = "external-audio-"
internal const val ExternalAudioExtraKey = "com.smartisan.music.extra.EXTERNAL_AUDIO"

data class ExternalAudioLaunchRequest(
    val requestId: Int,
    val uri: Uri,
    val mimeType: String?,
    val displayName: String?,
)

internal data class ExternalAudioMediaStoreIds(
    val mediaStoreId: Long?,
    val albumId: Long?,
)

internal fun MediaItem.isExternalAudioLaunchItem(): Boolean {
    return mediaMetadata.extras?.getBoolean(ExternalAudioExtraKey, false) == true ||
        mediaId.startsWith(ExternalAudioMediaIdPrefix)
}

internal fun ExternalAudioLaunchRequest.resolveExternalAudioArtist(context: Context): String? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            when (uri.scheme) {
                ContentScheme -> retriever.setDataSource(context, uri)
                FileScheme -> retriever.setDataSource(uri.path ?: return@runCatching null)
                else -> return@runCatching null
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
        } finally {
            runCatching {
                retriever.release()
            }
        }
    }.getOrNull()?.takeIf(String::isNotBlank)
}

internal fun ExternalAudioLaunchRequest.resolveExternalAudioAlbumId(context: Context): Long? {
    return resolveExternalAudioMediaStoreIds(context).albumId
}

internal fun ExternalAudioLaunchRequest.resolveExternalAudioMediaStoreIds(context: Context): ExternalAudioMediaStoreIds {
    if (uri.scheme != ContentScheme) {
        return ExternalAudioMediaStoreIds(mediaStoreId = null, albumId = null)
    }
    val queriedIds = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }
            val mediaIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            ExternalAudioMediaStoreIds(
                mediaStoreId = cursor.getPositiveLong(mediaIdColumn),
                albumId = cursor.getPositiveLong(albumIdColumn),
            )
        }
    }.getOrNull()

    return ExternalAudioMediaStoreIds(
        mediaStoreId = queriedIds?.mediaStoreId ?: uri.resolveMediaStoreAudioId(context),
        albumId = queriedIds?.albumId,
    )
}

private fun android.database.Cursor.getPositiveLong(columnIndex: Int): Long? {
    if (columnIndex == -1) {
        return null
    }
    return getLong(columnIndex).takeIf { it > 0L }
}

private fun Uri.resolveMediaStoreAudioId(context: Context): Long? {
    val tailId = lastPathSegment
        ?.takeIf { it.startsWith("audio:") }
        ?.substringAfter(':')
        ?.toLongOrNull()
    if (tailId != null) {
        return tailId
    }
    val directId = lastPathSegment?.toLongOrNull()
    if (directId != null) {
        return directId
    }
    return runCatching {
        if (!DocumentsContract.isDocumentUri(context, this)) {
            return@runCatching null
        }
        DocumentsContract.getDocumentId(this)
            .takeIf { it.startsWith("audio:") }
            ?.substringAfter(':')
            ?.toLongOrNull()
    }.getOrNull()
}

private const val ContentScheme = "content"
private const val FileScheme = "file"
