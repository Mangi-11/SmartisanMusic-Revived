package com.smartisan.music.ui.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ScratchPlaybackCycleMs = 1_800f
private const val ScratchMinMotionDegrees = 0.05f
private const val ScratchDecodeTimeoutUs = 10_000L
private const val ScratchDecodeWindowBeforeMs = 12_000L
private const val ScratchDecodeWindowAfterMs = 18_000L
private const val ScratchDecodeRefreshDistanceMs = 4_000L
private const val ScratchMinDeltaTimeMs = 8L
private const val ScratchMaxDeltaTimeMs = 72L
private const val ScratchOutputFrames = 384
private const val ScratchOutputChannels = 2
private const val ScratchOutputGain = 1.08f
private const val ScratchMaxPlaybackRatePermille = 6_000

internal class ScratchSoundController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val decodeExecutor = Executors.newSingleThreadExecutor()
    private val playbackLock = ReentrantLock()
    private val playbackWakeCondition = playbackLock.newCondition()
    private val playbackThread = Thread(::playbackLoop, "ScratchAudioTrack").apply {
        isDaemon = true
        start()
    }

    @Volatile
    private var released = false

    @Volatile
    private var sourceGeneration = 0

    @Volatile
    private var currentSourceKey: String? = null

    @Volatile
    private var currentSourceUri: Uri? = null

    @Volatile
    private var loadingRequest: ScratchDecodeRequest? = null

    @Volatile
    private var pendingDecodePositionMs: Long? = null

    @Volatile
    private var scratchBuffer: ScratchBuffer? = null

    @Volatile
    private var scratchActive = false

    @Volatile
    private var scratchPositionMs = 0L

    @Volatile
    private var requestedDirection = 0

    @Volatile
    private var requestedPlaybackRatePermille = 0f

    @Volatile
    private var cursorResetGeneration = 0L

    @Volatile
    private var lastMotionRealtimeMs = 0L

    private var audioTrack: AudioTrack? = null
    private var audioTrackSampleRate = 0
    private var playbackCursor = 0.0
    private var appliedCursorResetGeneration = -1L
    private var appliedBufferIdentity: ScratchBufferIdentity? = null
    private val stereoWriteBuffer = ShortArray(ScratchOutputFrames * ScratchOutputChannels)

    fun prepareSource(
        sourceUri: Uri?,
        positionMs: Long,
    ) {
        val sourceKey = sourceUri?.toString()
        if (sourceKey.isNullOrEmpty()) {
            sourceGeneration += 1
            currentSourceKey = null
            currentSourceUri = null
            loadingRequest = null
            pendingDecodePositionMs = null
            scratchBuffer = null
            wakePlaybackThread()
            return
        }
        currentSourceUri = sourceUri
        if (currentSourceKey != sourceKey) {
            currentSourceKey = sourceKey
            scratchBuffer = null
            loadingRequest = null
            pendingDecodePositionMs = null
        }
        requestBufferForPosition(sourceUri, sourceKey, positionMs)
    }

    private fun requestBufferForPosition(
        sourceUri: Uri,
        sourceKey: String,
        positionMs: Long,
    ) {
        if (released) {
            return
        }
        val anchorPositionMs = positionMs.coerceAtLeast(0L)
        scratchBuffer?.takeIf { buffer ->
            buffer.covers(sourceKey, anchorPositionMs) &&
                !buffer.needsRefresh(anchorPositionMs)
        }?.let {
            return
        }
        loadingRequest?.takeIf { request ->
            request.sourceKey == sourceKey
        }?.let {
            pendingDecodePositionMs = anchorPositionMs
            return
        }
        startDecodeRequest(sourceUri, sourceKey, anchorPositionMs)
    }

    private fun startDecodeRequest(
        sourceUri: Uri,
        sourceKey: String,
        anchorPositionMs: Long,
    ) {
        val request = ScratchDecodeRequest(
            sourceUri = sourceUri,
            sourceKey = sourceKey,
            windowStartMs = (anchorPositionMs - ScratchDecodeWindowBeforeMs).coerceAtLeast(0L),
            windowEndMs = anchorPositionMs + ScratchDecodeWindowAfterMs,
        )
        val generation = sourceGeneration + 1
        sourceGeneration = generation
        loadingRequest = request
        decodeExecutor.execute {
            val decodedBuffer = decodeScratchBuffer(appContext, request)
            if (released || generation != sourceGeneration) {
                return@execute
            }
            if (decodedBuffer != null) {
                scratchBuffer = decodedBuffer
            }
            loadingRequest = null
            val pendingPositionMs = pendingDecodePositionMs
            pendingDecodePositionMs = null
            if (
                pendingPositionMs != null &&
                currentSourceKey == sourceKey &&
                currentSourceUri == sourceUri &&
                scratchBuffer?.takeIf { buffer ->
                    buffer.covers(sourceKey, pendingPositionMs) &&
                        !buffer.needsRefresh(pendingPositionMs)
                } == null
            ) {
                startDecodeRequest(sourceUri, sourceKey, pendingPositionMs.coerceAtLeast(0L))
            }
            wakePlaybackThread()
        }
    }

    fun onScratchStart(
        sourceUri: Uri?,
        positionMs: Long,
    ) {
        prepareSource(sourceUri, positionMs)
        scratchPositionMs = positionMs
        scratchActive = true
        requestedDirection = 1
        requestedPlaybackRatePermille = 0f
        lastMotionRealtimeMs = 0L
        cursorResetGeneration += 1
        wakePlaybackThread()
    }

    fun onScratchMotion(
        positionMs: Long,
        deltaAngleDegrees: Float,
    ) {
        val magnitude = abs(deltaAngleDegrees)
        if (released) {
            return
        }
        scratchPositionMs = positionMs
        if (magnitude < ScratchMinMotionDegrees) {
            requestedDirection = 0
            requestedPlaybackRatePermille = 0f
            cursorResetGeneration += 1
            wakePlaybackThread()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val deltaTimeMs = when {
            lastMotionRealtimeMs == 0L -> 16L
            else -> (now - lastMotionRealtimeMs).coerceIn(
                ScratchMinDeltaTimeMs,
                ScratchMaxDeltaTimeMs,
            )
        }
        lastMotionRealtimeMs = now

        val direction = if (deltaAngleDegrees >= 0f) 1 else -1
        requestedPlaybackRatePermille = scratchPlaybackRatePermille(magnitude, deltaTimeMs).toFloat()
        requestedDirection = direction
        currentSourceUri?.let { sourceUri ->
            currentSourceKey?.let { sourceKey ->
                requestBufferForPosition(sourceUri, sourceKey, positionMs)
            }
        }
        scratchActive = true
        wakePlaybackThread()
    }

    fun stop() {
        scratchActive = false
        requestedDirection = 0
        requestedPlaybackRatePermille = 0f
        lastMotionRealtimeMs = 0L
        wakePlaybackThread()
    }

    fun release() {
        stop()
        released = true
        sourceGeneration += 1
        decodeExecutor.shutdownNow()
        wakePlaybackThread()
        playbackThread.join(500)
    }

    private fun wakePlaybackThread() {
        playbackLock.withLock {
            playbackWakeCondition.signalAll()
        }
    }

    private fun playbackLoop() {
        try {
            while (!released) {
                val buffer = scratchBuffer
                val sourceKey = currentSourceKey
                val shouldPlay = scratchActive &&
                    buffer != null &&
                    sourceKey != null &&
                    buffer.covers(sourceKey, scratchPositionMs) &&
                    !released

                if (!shouldPlay || buffer.stereoSamples.isEmpty()) {
                    pauseAndFlushTrack()
                    playbackLock.withLock {
                        if (!released) {
                            playbackWakeCondition.await(24L, TimeUnit.MILLISECONDS)
                        }
                    }
                    continue
                }

                val localCursorResetGeneration = cursorResetGeneration
                val bufferIdentity = buffer.identity
                if (shouldResetScratchCursor(
                        appliedBufferIdentity = appliedBufferIdentity,
                        currentBufferIdentity = bufferIdentity,
                        appliedCursorResetGeneration = appliedCursorResetGeneration,
                        currentCursorResetGeneration = localCursorResetGeneration,
                    ) ||
                    !buffer.containsSample(playbackCursor)
                ) {
                    playbackCursor = buffer.positionMsToSample(scratchPositionMs)
                    appliedBufferIdentity = bufferIdentity
                    appliedCursorResetGeneration = localCursorResetGeneration
                }

                val frameStep = (requestedPlaybackRatePermille / 1_000f) *
                    requestedDirection.toDouble()
                if (!buffer.containsSampleSpan(playbackCursor, frameStep, ScratchOutputFrames)) {
                    playbackCursor = buffer.positionMsToSample(scratchPositionMs)
                }
                if (!buffer.containsSampleSpan(playbackCursor, frameStep, ScratchOutputFrames)) {
                    currentSourceUri?.let { sourceUri ->
                        requestBufferForPosition(sourceUri, sourceKey, scratchPositionMs)
                    }
                    pauseAndFlushTrack()
                    playbackLock.withLock {
                        if (!released) {
                            playbackWakeCondition.await(24L, TimeUnit.MILLISECONDS)
                        }
                    }
                    continue
                }

                val track = ensureAudioTrack(buffer.sampleRate)
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                fillStereoBuffer(buffer, frameStep)
                track.write(
                    stereoWriteBuffer,
                    0,
                    stereoWriteBuffer.size,
                    AudioTrack.WRITE_BLOCKING,
                )
            }
        } finally {
            releaseAudioTrack()
        }
    }

    private fun fillStereoBuffer(
        buffer: ScratchBuffer,
        frameStep: Double,
    ) {
        val lastFrameIndex = buffer.frameCount - 1
        if (lastFrameIndex < 0) {
            stereoWriteBuffer.fill(0)
            return
        }
        if (frameStep == 0.0) {
            stereoWriteBuffer.fill(0)
            return
        }
        var outputIndex = 0
        repeat(ScratchOutputFrames) {
            val left = amplifySample(
                interpolateStereoSample(buffer.stereoSamples, playbackCursor, channel = 0),
            )
            val right = amplifySample(
                interpolateStereoSample(buffer.stereoSamples, playbackCursor, channel = 1),
            )
            stereoWriteBuffer[outputIndex++] = left
            stereoWriteBuffer[outputIndex++] = right
            playbackCursor += frameStep
        }
    }

    private fun ensureAudioTrack(sampleRate: Int): AudioTrack {
        audioTrack?.takeIf { audioTrackSampleRate == sampleRate }?.let { return it }
        releaseAudioTrack()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val safeBufferSize = maxOf(
            minBufferSize.coerceAtLeast(0),
            stereoWriteBuffer.size * Short.SIZE_BYTES * 4,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(safeBufferSize)
            .build()
            .also {
                audioTrack = it
                audioTrackSampleRate = sampleRate
            }
    }

    private fun pauseAndFlushTrack() {
        val track = audioTrack ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
            }
        }
    }

    private fun releaseAudioTrack() {
        audioTrack?.release()
        audioTrack = null
        audioTrackSampleRate = 0
    }
}

internal fun scratchPlaybackRatePermille(
    deltaAngleDegrees: Float,
    deltaTimeMs: Long,
): Int {
    val scaledRate = (
        ScratchPlaybackCycleMs * deltaAngleDegrees /
            (360f * deltaTimeMs.coerceIn(ScratchMinDeltaTimeMs, ScratchMaxDeltaTimeMs).toFloat())
    ) * 1_000f
    return scaledRate
        .roundToInt()
        .coerceIn(0, ScratchMaxPlaybackRatePermille)
}

private fun amplifySample(sample: Int): Short {
    return (sample * ScratchOutputGain)
        .roundToInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()
}

private fun interpolateStereoSample(
    samples: ShortArray,
    sampleIndex: Double,
    channel: Int,
): Int {
    if (samples.isEmpty()) {
        return 0
    }
    val frameCount = samples.size / ScratchOutputChannels
    val lastFrameIndex = frameCount - 1
    val clampedIndex = sampleIndex.coerceIn(0.0, lastFrameIndex.toDouble())
    val leftFrameIndex = clampedIndex.toInt()
    val rightFrameIndex = (leftFrameIndex + 1).coerceAtMost(lastFrameIndex)
    val fraction = clampedIndex - leftFrameIndex
    val sampleChannel = channel.coerceIn(0, ScratchOutputChannels - 1)
    val left = samples[(leftFrameIndex * ScratchOutputChannels) + sampleChannel].toDouble()
    val right = samples[(rightFrameIndex * ScratchOutputChannels) + sampleChannel].toDouble()
    return (left + ((right - left) * fraction)).roundToInt()
}

private data class ScratchBuffer(
    val sourceKey: String,
    val sampleRate: Int,
    val windowStartMs: Long,
    val stereoSamples: ShortArray,
) {
    val frameCount: Int
        get() = stereoSamples.size / ScratchOutputChannels

    val identity: ScratchBufferIdentity
        get() = ScratchBufferIdentity(
            sourceKey = sourceKey,
            windowStartMs = windowStartMs,
            sampleRate = sampleRate,
            frameCount = frameCount,
        )

    private val windowEndMs: Long
        get() = windowStartMs + ((frameCount * 1_000L) / sampleRate.coerceAtLeast(1))

    fun covers(
        sourceKey: String,
        positionMs: Long,
    ): Boolean {
        return this.sourceKey == sourceKey && positionMs in windowStartMs..windowEndMs
    }

    fun needsRefresh(positionMs: Long): Boolean {
        return (windowStartMs > 0L && positionMs - windowStartMs < ScratchDecodeRefreshDistanceMs) ||
            windowEndMs - positionMs < ScratchDecodeRefreshDistanceMs
    }

    fun containsSample(sampleIndex: Double): Boolean {
        return sampleIndex in 0.0..(frameCount - 1).toDouble()
    }

    fun containsSampleSpan(
        startSampleIndex: Double,
        frameStep: Double,
        outputFrames: Int,
    ): Boolean {
        return containsScratchSampleSpan(
            frameCount = frameCount,
            startSampleIndex = startSampleIndex,
            frameStep = frameStep,
            outputFrames = outputFrames,
        )
    }

    fun positionMsToSample(positionMs: Long): Double {
        if (stereoSamples.isEmpty()) {
            return 0.0
        }
        val localPositionMs = positionMs.coerceAtLeast(windowStartMs) - windowStartMs
        val samplePosition = localPositionMs * sampleRate.toDouble() / 1_000.0
        return samplePosition.coerceIn(0.0, (frameCount - 1).toDouble())
    }
}

internal data class ScratchBufferIdentity(
    val sourceKey: String,
    val windowStartMs: Long,
    val sampleRate: Int,
    val frameCount: Int,
)

internal fun shouldResetScratchCursor(
    appliedBufferIdentity: ScratchBufferIdentity?,
    currentBufferIdentity: ScratchBufferIdentity,
    appliedCursorResetGeneration: Long,
    currentCursorResetGeneration: Long,
): Boolean {
    return appliedBufferIdentity != currentBufferIdentity ||
        appliedCursorResetGeneration != currentCursorResetGeneration
}

internal fun containsScratchSampleSpan(
    frameCount: Int,
    startSampleIndex: Double,
    frameStep: Double,
    outputFrames: Int,
): Boolean {
    if (frameCount <= 0 || outputFrames <= 0) {
        return false
    }
    val lastSampleIndex = (frameCount - 1).toDouble()
    val endSampleIndex = startSampleIndex + (frameStep * (outputFrames - 1))
    return startSampleIndex in 0.0..lastSampleIndex &&
        endSampleIndex in 0.0..lastSampleIndex
}

private data class ScratchDecodeRequest(
    val sourceUri: Uri,
    val sourceKey: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
) {
    val windowStartUs: Long
        get() = windowStartMs * 1_000L

    val windowEndUs: Long
        get() = windowEndMs * 1_000L
}

private fun decodeScratchBuffer(
    context: Context,
    request: ScratchDecodeRequest,
): ScratchBuffer? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
        extractor.setDataSource(context, request.sourceUri, null)
        val trackIndex = findAudioTrackIndex(extractor)
        if (trackIndex == -1) {
            null
        } else {
            extractor.selectTrack(trackIndex)
            extractor.seekTo(request.windowStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val sourceFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = sourceFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mimeType).apply {
                configure(sourceFormat, null, null, 0)
                start()
            }
            codec = decoder

            val initialSampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val initialCapacity = buildInitialSampleCapacity(request, initialSampleRate)
            val sampleBuilder = StereoSampleBuilder(initialCapacity)
            val bufferInfo = MediaCodec.BufferInfo()
            var outputSampleRate = initialSampleRate
            var outputChannelCount = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var firstOutputTimeUs: Long? = null
            var inputEnded = false
            var outputEnded = false

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(ScratchDecodeTimeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex) ?: break
                        val sampleTimeUs = extractor.sampleTime
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0 || (sampleTimeUs >= 0L && sampleTimeUs > request.windowEndUs)) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                sampleTimeUs,
                                extractor.sampleFlags,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, ScratchDecodeTimeoutUs)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        outputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> {
                        if (outputIndex >= 0) {
                            if (bufferInfo.size > 0) {
                                if (firstOutputTimeUs == null) {
                                    firstOutputTimeUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                                }
                                val outputBuffer = decoder.getOutputBuffer(outputIndex)
                                    ?.duplicate()
                                    ?.apply {
                                        position(bufferInfo.offset)
                                        limit(bufferInfo.offset + bufferInfo.size)
                                    }
                                if (outputBuffer != null) {
                                    sampleBuilder.append(
                                        outputBuffer,
                                        outputChannelCount,
                                        pcmEncoding,
                                    )
                                }
                            }
                            decoder.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputEnded = true
                            }
                        }
                    }
                }
            }

            val stereoSamples = sampleBuilder.build()
            if (stereoSamples.isEmpty()) {
                null
            } else {
                ScratchBuffer(
                    sourceKey = request.sourceKey,
                    sampleRate = outputSampleRate,
                    windowStartMs = (firstOutputTimeUs ?: request.windowStartUs) / 1_000L,
                    stereoSamples = stereoSamples,
                )
            }
        }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        extractor.release()
    }
}

private fun findAudioTrackIndex(extractor: MediaExtractor): Int {
    repeat(extractor.trackCount) { index ->
        val format = extractor.getTrackFormat(index)
        val mimeType = format.getString(MediaFormat.KEY_MIME) ?: return@repeat
        if (mimeType.startsWith("audio/")) {
            return index
        }
    }
    return -1
}

private fun buildInitialSampleCapacity(
    request: ScratchDecodeRequest,
    sampleRate: Int,
): Int {
    val durationMs = (request.windowEndMs - request.windowStartMs).coerceAtLeast(1L)
    val projectedSamples = (durationMs * sampleRate / 1_000L)
        .coerceAtLeast(sampleRate.toLong())
    return projectedSamples.toInt()
}

private class StereoSampleBuilder(initialCapacity: Int) {
    private var buffer = ShortArray(initialCapacity.coerceAtLeast(2_048) * ScratchOutputChannels)
    private var frameCount = 0

    fun append(
        outputBuffer: ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
    ) {
        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> appendFloat(outputBuffer, channelCount)
            else -> appendIntegerPcm(outputBuffer, channelCount, pcmEncoding)
        }
    }

    fun build(): ShortArray = buffer.copyOf(frameCount * ScratchOutputChannels)

    private fun appendIntegerPcm(
        outputBuffer: ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
    ) {
        val channels = channelCount.coerceAtLeast(1)
        val bytesPerSample = pcmBytesPerSample(pcmEncoding)
        val pcmBuffer = outputBuffer.order(ByteOrder.nativeOrder())
        val frameCount = pcmBuffer.remaining() / (channels * bytesPerSample)
        ensureCapacity(this.frameCount + frameCount)
        repeat(frameCount) {
            val left = pcmBuffer.readPcmSampleAsShort(pcmEncoding)
            val right = if (channels > 1) {
                pcmBuffer.readPcmSampleAsShort(pcmEncoding)
            } else {
                left
            }
            repeat((channels - 2).coerceAtLeast(0)) {
                pcmBuffer.readPcmSampleAsShort(pcmEncoding)
            }
            val outputIndex = this.frameCount * ScratchOutputChannels
            buffer[outputIndex] = left
            buffer[outputIndex + 1] = right
            this.frameCount += 1
        }
    }

    private fun appendFloat(
        outputBuffer: ByteBuffer,
        channelCount: Int,
    ) {
        val channels = channelCount.coerceAtLeast(1)
        val floatBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        val frameCount = floatBuffer.remaining() / channels
        ensureCapacity(this.frameCount + frameCount)
        repeat(frameCount) {
            val left = floatBuffer.get()
            val right = if (channels > 1) {
                floatBuffer.get()
            } else {
                left
            }
            repeat((channels - 2).coerceAtLeast(0)) {
                floatBuffer.get()
            }
            val outputIndex = this.frameCount * ScratchOutputChannels
            buffer[outputIndex] = (left * Short.MAX_VALUE.toFloat())
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer[outputIndex + 1] = (right * Short.MAX_VALUE.toFloat())
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            this.frameCount += 1
        }
    }

    private fun ensureCapacity(requiredFrameCount: Int) {
        val requiredSize = requiredFrameCount * ScratchOutputChannels
        if (requiredSize <= buffer.size) {
            return
        }
        var newSize = buffer.size
        while (newSize < requiredSize) {
            newSize *= 2
        }
        buffer = buffer.copyOf(newSize)
    }
}

internal fun pcmBytesPerSample(pcmEncoding: Int): Int {
    return when (pcmEncoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_32BIT,
        AudioFormat.ENCODING_PCM_FLOAT,
        -> 4
        else -> 2
    }
}

internal fun ByteBuffer.readPcmSampleAsShort(pcmEncoding: Int): Short {
    return when (pcmEncoding) {
        AudioFormat.ENCODING_PCM_8BIT -> {
            (((get().toInt() and 0xFF) - 128) shl 8).toShort()
        }
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val signed24 = readSigned24BitSample()
            (signed24 shr 8).toShort()
        }
        AudioFormat.ENCODING_PCM_32BIT -> {
            (int shr 16).toShort()
        }
        else -> short
    }
}

private fun ByteBuffer.readSigned24BitSample(): Int {
    val first = get().toInt() and 0xFF
    val second = get().toInt() and 0xFF
    val third = get().toInt() and 0xFF
    val value = if (order() == ByteOrder.BIG_ENDIAN) {
        (first shl 16) or (second shl 8) or third
    } else {
        first or (second shl 8) or (third shl 16)
    }
    return if ((value and 0x80_0000) != 0) {
        value or -0x100_0000
    } else {
        value
    }
}
