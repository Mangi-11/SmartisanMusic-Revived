package com.smartisan.music.ui.shell.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisan.music.ExternalAudioExtraKey
import com.smartisan.music.ExternalAudioLaunchRequest
import com.smartisan.music.ExternalAudioMediaIdPrefix
import com.smartisan.music.playback.LocalAudioLibrary

internal fun ExternalAudioLaunchRequest.toExternalAudioMediaItem(
    fallbackTitle: String,
    artist: String?,
    mediaStoreId: Long?,
    albumId: Long?,
): MediaItem {
    val uriTitle = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringBeforeLast('.', missingDelimiterValue = uri.lastPathSegment.orEmpty())
        ?.takeIf(String::isNotBlank)
    val title = displayName
        ?.substringBeforeLast('.', missingDelimiterValue = displayName)
        ?.takeIf(String::isNotBlank)
        ?: uriTitle
        ?: fallbackTitle
    val normalizedMimeType = mimeType
        ?.lowercase()
        ?.takeUnless { it.endsWith("/*") }
    val extras = Bundle().apply {
        putBoolean(ExternalAudioExtraKey, true)
        if (albumId != null) {
            putLong(LocalAudioLibrary.AlbumIdExtraKey, albumId)
        }
    }
    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(title)
        .setDisplayTitle(title)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
    if (mediaStoreId != null) {
        metadataBuilder.setArtworkUri(LocalAudioLibrary.trackArtworkUri(mediaStoreId))
    }
    artist?.takeIf(String::isNotBlank)?.let { externalArtist ->
        metadataBuilder
            .setArtist(externalArtist)
            .setSubtitle(externalArtist)
    }

    return MediaItem.Builder()
        .setMediaId("$ExternalAudioMediaIdPrefix$requestId")
        .setUri(uri)
        .setMimeType(normalizedMimeType)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}
