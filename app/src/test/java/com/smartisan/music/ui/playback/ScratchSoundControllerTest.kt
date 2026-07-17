package com.smartisan.music.ui.playback

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScratchSoundControllerTest {

    @Test
    fun `scratch rate follows original turntable mapping`() {
        assertEquals(3_600, scratchPlaybackRatePermille(deltaAngleDegrees = 36f, deltaTimeMs = 50L))
        assertEquals(2_500, scratchPlaybackRatePermille(deltaAngleDegrees = 18f, deltaTimeMs = 36L))
    }

    @Test
    fun `scratch rate stays within turntable playback bounds`() {
        assertEquals(6_000, scratchPlaybackRatePermille(deltaAngleDegrees = 54f, deltaTimeMs = 8L))
        assertEquals(347, scratchPlaybackRatePermille(deltaAngleDegrees = 5f, deltaTimeMs = 90L))
    }

    @Test
    fun `cursor resets when scratch buffer window changes`() {
        val applied = ScratchBufferIdentity(
            sourceKey = "content://song",
            windowStartMs = 10_000L,
            sampleRate = 44_100,
            frameCount = 44_100,
        )
        val refreshed = applied.copy(windowStartMs = 18_000L)

        assertTrue(
            shouldResetScratchCursor(
                appliedBufferIdentity = applied,
                currentBufferIdentity = refreshed,
                appliedCursorResetGeneration = 2L,
                currentCursorResetGeneration = 2L,
            ),
        )
    }

    @Test
    fun `cursor stays on same scratch buffer and generation`() {
        val identity = ScratchBufferIdentity(
            sourceKey = "content://song",
            windowStartMs = 10_000L,
            sampleRate = 44_100,
            frameCount = 44_100,
        )

        assertFalse(
            shouldResetScratchCursor(
                appliedBufferIdentity = identity,
                currentBufferIdentity = identity,
                appliedCursorResetGeneration = 2L,
                currentCursorResetGeneration = 2L,
            ),
        )
    }

    @Test
    fun `cursor resets on explicit scratch restart`() {
        val identity = ScratchBufferIdentity(
            sourceKey = "content://song",
            windowStartMs = 10_000L,
            sampleRate = 44_100,
            frameCount = 44_100,
        )

        assertTrue(
            shouldResetScratchCursor(
                appliedBufferIdentity = identity,
                currentBufferIdentity = identity,
                appliedCursorResetGeneration = 2L,
                currentCursorResetGeneration = 3L,
            ),
        )
    }

    @Test
    fun `cursor resets before any scratch buffer has been applied`() {
        val identity = ScratchBufferIdentity(
            sourceKey = "content://song",
            windowStartMs = 10_000L,
            sampleRate = 44_100,
            frameCount = 44_100,
        )

        assertTrue(
            shouldResetScratchCursor(
                appliedBufferIdentity = null,
                currentBufferIdentity = identity,
                appliedCursorResetGeneration = 0L,
                currentCursorResetGeneration = 0L,
            ),
        )
    }

    @Test
    fun `cursor resets when source or pcm shape changes`() {
        val identity = ScratchBufferIdentity(
            sourceKey = "content://song-a",
            windowStartMs = 10_000L,
            sampleRate = 44_100,
            frameCount = 44_100,
        )

        assertTrue(
            shouldResetScratchCursor(
                appliedBufferIdentity = identity,
                currentBufferIdentity = identity.copy(sourceKey = "content://song-b"),
                appliedCursorResetGeneration = 2L,
                currentCursorResetGeneration = 2L,
            ),
        )
        assertTrue(
            shouldResetScratchCursor(
                appliedBufferIdentity = identity,
                currentBufferIdentity = identity.copy(sampleRate = 48_000),
                appliedCursorResetGeneration = 2L,
                currentCursorResetGeneration = 2L,
            ),
        )
        assertTrue(
            shouldResetScratchCursor(
                appliedBufferIdentity = identity,
                currentBufferIdentity = identity.copy(frameCount = 48_000),
                appliedCursorResetGeneration = 2L,
                currentCursorResetGeneration = 2L,
            ),
        )
    }

    @Test
    fun `pcm sample sizes match Android audio format encodings`() {
        assertEquals(1, pcmBytesPerSample(AudioFormat.ENCODING_PCM_8BIT))
        assertEquals(2, pcmBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
        assertEquals(3, pcmBytesPerSample(AudioFormat.ENCODING_PCM_24BIT_PACKED))
        assertEquals(4, pcmBytesPerSample(AudioFormat.ENCODING_PCM_32BIT))
        assertEquals(4, pcmBytesPerSample(AudioFormat.ENCODING_PCM_FLOAT))
    }

    @Test
    fun `pcm integer samples convert to 16 bit amplitude consistently`() {
        assertEquals(
            0x4000.toShort(),
            ByteBuffer.allocate(2)
                .order(ByteOrder.nativeOrder())
                .putShort(0x4000.toShort())
                .flipForReading()
                .readPcmSampleAsShort(AudioFormat.ENCODING_PCM_16BIT),
        )
        assertEquals(
            0x4000.toShort(),
            ByteBuffer.allocate(3)
                .order(ByteOrder.nativeOrder())
                .putSigned24Sample(0x40_0000)
                .flipForReading()
                .readPcmSampleAsShort(AudioFormat.ENCODING_PCM_24BIT_PACKED),
        )
        assertEquals(
            0x4000.toShort(),
            ByteBuffer.allocate(4)
                .order(ByteOrder.nativeOrder())
                .putInt(0x4000_0000)
                .flipForReading()
                .readPcmSampleAsShort(AudioFormat.ENCODING_PCM_32BIT),
        )
        assertEquals(
            0.toShort(),
            ByteBuffer.allocate(1)
                .put(0x80.toByte())
                .flipForReading()
                .readPcmSampleAsShort(AudioFormat.ENCODING_PCM_8BIT),
        )
    }

    @Test
    fun `pcm integer negative samples keep their sign`() {
        assertEquals(
            (-0x4000).toShort(),
            ByteBuffer.allocate(3)
                .order(ByteOrder.nativeOrder())
                .putSigned24Sample(-0x40_0000)
                .flipForReading()
                .readPcmSampleAsShort(AudioFormat.ENCODING_PCM_24BIT_PACKED),
        )
        assertEquals(
            (-0x4000).toShort(),
            ByteBuffer.allocate(4)
                .order(ByteOrder.nativeOrder())
                .putInt(-0x4000_0000)
                .flipForReading()
                .readPcmSampleAsShort(AudioFormat.ENCODING_PCM_32BIT),
        )
        assertEquals(
            Short.MIN_VALUE,
            ByteBuffer.allocate(1)
                .put(0x00.toByte())
                .flipForReading()
                .readPcmSampleAsShort(AudioFormat.ENCODING_PCM_8BIT),
        )
    }

    @Test
    fun `scratch sample span must fit fully in decoded buffer`() {
        assertTrue(
            containsScratchSampleSpan(
                frameCount = 1_000,
                startSampleIndex = 100.0,
                frameStep = 2.0,
                outputFrames = 100,
            ),
        )
        assertTrue(
            containsScratchSampleSpan(
                frameCount = 1_000,
                startSampleIndex = 900.0,
                frameStep = -2.0,
                outputFrames = 100,
            ),
        )
        assertFalse(
            containsScratchSampleSpan(
                frameCount = 1_000,
                startSampleIndex = 900.0,
                frameStep = 2.0,
                outputFrames = 100,
            ),
        )
        assertFalse(
            containsScratchSampleSpan(
                frameCount = 1_000,
                startSampleIndex = 100.0,
                frameStep = -2.0,
                outputFrames = 100,
            ),
        )
    }
}

private fun ByteBuffer.flipForReading(): ByteBuffer {
    flip()
    return this
}

private fun ByteBuffer.putSigned24Sample(value: Int): ByteBuffer {
    if (order() == ByteOrder.BIG_ENDIAN) {
        put(((value shr 16) and 0xFF).toByte())
        put(((value shr 8) and 0xFF).toByte())
        put((value and 0xFF).toByte())
    } else {
        put((value and 0xFF).toByte())
        put(((value shr 8) and 0xFF).toByte())
        put(((value shr 16) and 0xFF).toByte())
    }
    return this
}
