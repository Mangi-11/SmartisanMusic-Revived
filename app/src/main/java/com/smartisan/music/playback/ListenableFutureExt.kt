package com.smartisan.music.playback

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> ListenableFuture<T>.await(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                runCatching { get() }
                    .onSuccess(continuation::resume)
                    .onFailure(continuation::resumeWithException)
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation {
            cancel(false)
        }
    }
