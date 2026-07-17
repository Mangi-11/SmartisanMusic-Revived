package com.smartisan.music.ui.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.smartisan.music.R
import kotlin.random.Random

private const val VinylPopcornMaxStreams = 3
private const val VinylPopcornPriority = 1
private const val VinylPopcornActiveStreamLimit = 8
private const val VinylPopcornMinVolume = 0.08f
private const val VinylPopcornVolumeRange = 0.07f
private const val VinylPopcornMinRate = 0.92f
private const val VinylPopcornRateRange = 0.16f

private val VinylPopcornSamples = intArrayOf(
    R.raw.y_1_01,
    R.raw.y_2_01,
    R.raw.y_3_01,
    R.raw.y_4_01,
    R.raw.y_5_01,
    R.raw.y_6_01,
    R.raw.y_7_01,
    R.raw.y_8_01,
    R.raw.y_9_01,
    R.raw.y_10_01,
    R.raw.y_11_01,
    R.raw.y_12_01,
    R.raw.y_13_01,
    R.raw.y_14_01,
    R.raw.y_15_01,
    R.raw.y_16_01,
    R.raw.y_17_01,
    R.raw.y_18_01,
    R.raw.y_19_01,
    R.raw.y_20_01,
)

internal class VinylPopcornSoundController(
    context: Context,
) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(VinylPopcornMaxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .build()
    private val random = Random.Default
    private val loadedSampleIds = linkedSetOf<Int>()
    private val activeStreamIds = ArrayDeque<Int>()
    private val lock = Any()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(lock) {
                    loadedSampleIds += sampleId
                }
            }
        }
        VinylPopcornSamples.forEach { resId ->
            soundPool.load(context, resId, VinylPopcornPriority)
        }
    }

    fun playRandomPop() {
        val sampleId = synchronized(lock) {
            loadedSampleIds.randomOrNull(random)
        } ?: return
        val volume = VinylPopcornMinVolume + (random.nextFloat() * VinylPopcornVolumeRange)
        val rate = VinylPopcornMinRate + (random.nextFloat() * VinylPopcornRateRange)
        val streamId = soundPool.play(
            sampleId,
            volume,
            volume,
            VinylPopcornPriority,
            0,
            rate,
        )
        if (streamId == 0) {
            return
        }
        synchronized(lock) {
            activeStreamIds.addLast(streamId)
            while (activeStreamIds.size > VinylPopcornActiveStreamLimit) {
                soundPool.stop(activeStreamIds.removeFirst())
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            while (activeStreamIds.isNotEmpty()) {
                soundPool.stop(activeStreamIds.removeFirst())
            }
        }
    }

    fun release() {
        stop()
        soundPool.release()
    }
}
