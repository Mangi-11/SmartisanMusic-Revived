package com.smartisan.music.playback

import androidx.media3.common.MediaItem
import com.smartisan.music.isExternalAudioLaunchItem

internal fun MediaItem.canResolveDirectSessionPlaybackItem(): Boolean {
    return isExternalAudioLaunchItem()
}

internal fun MediaItem.toDirectSessionPlaybackItemOrNull(): MediaItem? {
    return takeIf(MediaItem::isExternalAudioLaunchItem)
}

internal fun List<MediaItem>.resolveDirectSessionPlaybackItemsOrNull(): MutableList<MediaItem>? {
    val resolvedItems = ArrayList<MediaItem>(size)
    for (item in this) {
        resolvedItems += item.toDirectSessionPlaybackItemOrNull() ?: return null
    }
    return resolvedItems
}
