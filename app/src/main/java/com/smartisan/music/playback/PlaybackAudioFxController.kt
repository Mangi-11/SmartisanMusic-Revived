@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.smartisan.music.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.media3.common.C
import com.smartisan.music.data.settings.AudioFxPreset
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.data.settings.activeAudioFxGainDbPoints
import com.smartisan.music.data.settings.bassBoostStrength
import kotlin.math.ln

private val AudioFxControlFrequenciesHz = floatArrayOf(60f, 230f, 910f, 4_000f, 14_000f)

internal class PlaybackAudioFxController {
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var settings: PlaybackSettings = PlaybackSettings()
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    fun setAudioSessionId(sessionId: Int) {
        if (audioSessionId == sessionId) {
            return
        }
        releaseEffects()
        audioSessionId = sessionId
        applySettings()
    }

    fun setSettings(settings: PlaybackSettings) {
        if (this.settings == settings) {
            return
        }
        this.settings = settings
        applySettings()
    }

    fun release() {
        releaseEffects()
        audioSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun applySettings() {
        val preset = settings.activeAudioFxPreset()
        if (audioSessionId <= 0 || preset == AudioFxPreset.Original) {
            releaseEffects()
            return
        }

        applyEqualizer(settings.activeAudioFxGainDbPoints())
        applyBassBoost(preset)
    }

    private fun applyEqualizer(gains: FloatArray) {
        val effect = equalizer ?: createEqualizer() ?: return
        val range = runCatching { effect.bandLevelRange }.getOrNull()
        val minLevel = range?.getOrNull(0)?.toInt() ?: return
        val maxLevel = range.getOrNull(1)?.toInt() ?: return
        val bandCount = runCatching { effect.numberOfBands.toInt() }.getOrDefault(0)
        if (bandCount <= 0) {
            runCatching { effect.enabled = false }
            return
        }

        for (band in 0 until bandCount) {
            val bandIndex = band.toShort()
            val centerHz = runCatching {
                effect.getCenterFreq(bandIndex).toFloat() / 1000f
            }.getOrDefault(AudioFxControlFrequenciesHz[band.coerceAtMost(AudioFxControlFrequenciesHz.lastIndex)])
            val gainMb = (interpolateGainDb(centerHz, gains) * 100f)
                .toInt()
                .coerceIn(minLevel, maxLevel)
                .toShort()
            runCatching {
                effect.setBandLevel(bandIndex, gainMb)
            }
        }
        runCatching {
            effect.enabled = true
        }.onFailure {
            releaseEqualizer()
        }
    }

    private fun applyBassBoost(preset: AudioFxPreset) {
        val strength = preset.bassBoostStrength()
        val effect = bassBoost ?: createBassBoost() ?: return
        runCatching {
            if (effect.strengthSupported) {
                effect.setStrength(strength)
            }
            effect.enabled = strength > 0
        }.onFailure {
            releaseBassBoost()
        }
    }

    private fun createEqualizer(): Equalizer? {
        return runCatching {
            Equalizer(0, audioSessionId).also { effect ->
                equalizer = effect
            }
        }.getOrNull()
    }

    private fun createBassBoost(): BassBoost? {
        return runCatching {
            BassBoost(0, audioSessionId).also { effect ->
                bassBoost = effect
            }
        }.getOrNull()
    }

    private fun releaseEffects() {
        releaseEqualizer()
        releaseBassBoost()
    }

    private fun releaseEqualizer() {
        equalizer?.let { effect ->
            runCatching { effect.enabled = false }
            runCatching { effect.release() }
        }
        equalizer = null
    }

    private fun releaseBassBoost() {
        bassBoost?.let { effect ->
            runCatching { effect.enabled = false }
            runCatching { effect.release() }
        }
        bassBoost = null
    }

    private fun PlaybackSettings.activeAudioFxPreset(): AudioFxPreset {
        return if (audioFxEnabled) audioFxPreset else AudioFxPreset.Original
    }

    private fun interpolateGainDb(
        centerHz: Float,
        gains: FloatArray,
    ): Float {
        val safeHz = centerHz.coerceAtLeast(AudioFxControlFrequenciesHz.first())
        if (safeHz <= AudioFxControlFrequenciesHz.first()) {
            return gains.first()
        }
        if (safeHz >= AudioFxControlFrequenciesHz.last()) {
            return gains.last()
        }

        for (index in 0 until AudioFxControlFrequenciesHz.lastIndex) {
            val startHz = AudioFxControlFrequenciesHz[index]
            val endHz = AudioFxControlFrequenciesHz[index + 1]
            if (safeHz in startHz..endHz) {
                val fraction = (
                    (ln(safeHz) - ln(startHz)) /
                        (ln(endHz) - ln(startHz))
                    ).coerceIn(0f, 1f)
                return gains[index] + (gains[index + 1] - gains[index]) * fraction
            }
        }
        return gains.last()
    }
}
