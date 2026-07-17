@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisan.music.playback

import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture

internal const val ScratchSeekModeAction = "com.smartisan.music.action.SET_SCRATCH_SEEK_MODE"
internal const val ScratchSeekModeEnabledKey = "scratch_seek_mode_enabled"
internal const val StartSleepTimerAction = "com.smartisan.music.action.START_SLEEP_TIMER"
internal const val CancelSleepTimerAction = "com.smartisan.music.action.CANCEL_SLEEP_TIMER"
internal const val SleepTimerDurationMsKey = "sleep_timer_duration_ms"
internal const val RefreshLibraryAction = "com.smartisan.music.action.REFRESH_LIBRARY"
internal const val InvalidateLibraryAction = "com.smartisan.music.action.INVALIDATE_LIBRARY"
internal const val SetTrackRatingAction = "com.smartisan.music.action.SET_TRACK_RATING"
internal const val ReplaceQueueAndPlayAction = "com.smartisan.music.action.REPLACE_QUEUE_AND_PLAY"
internal const val TrackRatingMediaIdKey = "track_rating_media_id"
internal const val TrackRatingScoreKey = "track_rating_score"
internal const val TrackRatingMinScore = 0
internal const val TrackRatingMaxScore = 5
internal const val ReplaceQueueMediaItemsKey = "replace_queue_media_items"
internal const val ReplaceQueueStartIndexKey = "replace_queue_start_index"
internal const val ReplaceQueueShuffleModeKey = "replace_queue_shuffle_mode"

internal val ScratchSeekModeCommand = SessionCommand(ScratchSeekModeAction, Bundle.EMPTY)
internal val StartSleepTimerCommand = SessionCommand(StartSleepTimerAction, Bundle.EMPTY)
internal val CancelSleepTimerCommand = SessionCommand(CancelSleepTimerAction, Bundle.EMPTY)
internal val RefreshLibraryCommand = SessionCommand(RefreshLibraryAction, Bundle.EMPTY)
internal val InvalidateLibraryCommand = SessionCommand(InvalidateLibraryAction, Bundle.EMPTY)
internal val SetTrackRatingCommand = SessionCommand(SetTrackRatingAction, Bundle.EMPTY)
internal val ReplaceQueueAndPlayCommand = SessionCommand(ReplaceQueueAndPlayAction, Bundle.EMPTY)

internal fun MediaController.setScratchSeekModeEnabled(enabled: Boolean) {
    val args = Bundle().apply {
        putBoolean(ScratchSeekModeEnabledKey, enabled)
    }
    sendCustomCommand(ScratchSeekModeCommand, args)
}

internal fun MediaController.startSleepTimer(durationMs: Long) {
    val args = Bundle().apply {
        putLong(SleepTimerDurationMsKey, durationMs)
    }
    sendCustomCommand(StartSleepTimerCommand, args)
}

internal fun MediaController.cancelSleepTimer() {
    sendCustomCommand(CancelSleepTimerCommand, Bundle.EMPTY)
}

internal fun MediaController.refreshLibrary() =
    sendCustomCommand(RefreshLibraryCommand, Bundle.EMPTY)

internal fun MediaController.invalidateLibrary() =
    sendCustomCommand(InvalidateLibraryCommand, Bundle.EMPTY)

internal fun MediaController.setTrackRating(mediaId: String, score: Int): ListenableFuture<SessionResult> {
    val args = Bundle().apply {
        putString(TrackRatingMediaIdKey, mediaId)
        putInt(TrackRatingScoreKey, score.coerceIn(TrackRatingMinScore, TrackRatingMaxScore))
    }
    return sendCustomCommand(SetTrackRatingCommand, args)
}

internal fun MediaController.sendReplaceQueueAndPlayCommand(
    mediaItems: List<MediaItem>,
    startIndex: Int,
    shuffleModeEnabled: Boolean,
): ListenableFuture<SessionResult> {
    return sendCustomCommand(
        ReplaceQueueAndPlayCommand,
        Bundle().apply {
            putParcelableArrayList(
                ReplaceQueueMediaItemsKey,
                ArrayList(
                    mediaItems.map { item ->
                        item.toBundleIncludeLocalConfiguration(MediaLibraryInfo.INTERFACE_VERSION)
                    },
                ),
            )
            putInt(ReplaceQueueStartIndexKey, startIndex)
            putBoolean(ReplaceQueueShuffleModeKey, shuffleModeEnabled)
        },
    )
}

internal fun Bundle.decodeReplaceQueueAndPlayMediaItems(): List<MediaItem> {
    val itemBundles = BundleCompat.getParcelableArrayList(
        this,
        ReplaceQueueMediaItemsKey,
        Bundle::class.java,
    )
        ?: return emptyList()
    return itemBundles.map { itemBundle ->
        MediaItem.fromBundle(itemBundle, MediaLibraryInfo.INTERFACE_VERSION)
    }
}
