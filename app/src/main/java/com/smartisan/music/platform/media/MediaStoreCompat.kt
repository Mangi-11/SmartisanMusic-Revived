package com.smartisan.music.platform.media

import android.content.ContentUris
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

internal fun audioMediaCollectionUri(): Uri {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
}

internal fun audioMediaItemUri(mediaId: Long): Uri {
    return ContentUris.withAppendedId(audioMediaCollectionUri(), mediaId)
}

internal fun ContentResolver.loadMediaThumbnailCompat(uri: Uri, size: Size): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            loadThumbnail(uri, size, null)
        }.getOrNull()
    } else {
        null
    }
}
