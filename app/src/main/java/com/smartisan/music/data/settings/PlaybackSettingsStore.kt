package com.smartisan.music.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val PlaybackSettingsStoreName = "playback_settings"

private val Context.playbackSettingsDataStore by preferencesDataStore(
    name = PlaybackSettingsStoreName,
)

data class PlaybackSettings(
    val scratchEnabled: Boolean = true,
    val hidePlayerAxisEnabled: Boolean = false,
    val popcornSoundEnabled: Boolean = false,
    val audioFxEnabled: Boolean = false,
    val audioFxPreset: AudioFxPreset = AudioFxPreset.Original,
    val audioFxCustomGainDbPoints: List<Float> = AudioFxDefaultCustomGainDbPoints,
)

enum class AudioFxPreset(
    val preferenceValue: String,
) {
    Original("original"),
    Bass("bass"),
    Clear("clear"),
    Vocal("vocal"),
    Rock("rock"),
    Custom("custom");

    companion object {
        fun fromPreference(value: String?): AudioFxPreset {
            return entries.firstOrNull { preset -> preset.preferenceValue == value } ?: Original
        }
    }
}

internal const val AudioFxBandCount = 5
internal const val AudioFxMinGainDb = -12f
internal const val AudioFxMaxGainDb = 12f
internal val AudioFxDefaultCustomGainDbPoints = listOf(2.0f, 0.8f, 2.8f, 1.7f, 0.9f)

internal fun normalizeAudioFxGainDbPoints(gains: List<Float>): List<Float> {
    return List(AudioFxBandCount) { index ->
        (gains.getOrNull(index) ?: AudioFxDefaultCustomGainDbPoints[index])
            .coerceIn(AudioFxMinGainDb, AudioFxMaxGainDb)
    }
}

internal fun PlaybackSettings.activeAudioFxGainDbPoints(): FloatArray {
    return audioFxPreset.equalizerGainDbPoints(audioFxCustomGainDbPoints)
}

internal fun AudioFxPreset.equalizerGainDbPoints(
    customGainDbPoints: List<Float> = AudioFxDefaultCustomGainDbPoints,
): FloatArray {
    return when (this) {
        AudioFxPreset.Original -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        AudioFxPreset.Bass -> floatArrayOf(5.5f, 3.2f, 0.3f, -0.8f, 0f)
        AudioFxPreset.Clear -> floatArrayOf(-1.8f, -0.6f, 1.6f, 3.4f, 2.7f)
        AudioFxPreset.Vocal -> floatArrayOf(-1.2f, 0.5f, 3.4f, 2.1f, -0.7f)
        AudioFxPreset.Rock -> floatArrayOf(3.2f, 1.4f, -1.1f, 2.2f, 3.8f)
        AudioFxPreset.Custom -> normalizeAudioFxGainDbPoints(customGainDbPoints).toFloatArray()
    }
}

internal fun AudioFxPreset.bassBoostStrength(): Short {
    return when (this) {
        AudioFxPreset.Original -> 0
        AudioFxPreset.Bass -> 620
        AudioFxPreset.Clear -> 80
        AudioFxPreset.Vocal -> 120
        AudioFxPreset.Rock -> 320
        AudioFxPreset.Custom -> 0
    }.toShort()
}

class PlaybackSettingsStore(
    private val context: Context,
) {

    val settings: Flow<PlaybackSettings> = context.playbackSettingsDataStore.data
        .map { preferences ->
            PlaybackSettings(
                scratchEnabled = preferences[ScratchEnabledKey] ?: true,
                hidePlayerAxisEnabled = preferences[HidePlayerAxisEnabledKey] ?: false,
                popcornSoundEnabled = preferences[PopcornSoundEnabledKey] ?: false,
                audioFxEnabled = preferences[AudioFxEnabledKey] ?: false,
                audioFxPreset = AudioFxPreset.fromPreference(preferences[AudioFxPresetKey]),
                audioFxCustomGainDbPoints = parseAudioFxCustomGainDbPoints(
                    preferences[AudioFxCustomGainDbPointsKey],
                ),
            )
        }
        .distinctUntilChanged()

    suspend fun setScratchEnabled(enabled: Boolean) {
        setBoolean(ScratchEnabledKey, enabled)
    }

    suspend fun setHidePlayerAxisEnabled(enabled: Boolean) {
        setBoolean(HidePlayerAxisEnabledKey, enabled)
    }

    suspend fun setPopcornSoundEnabled(enabled: Boolean) {
        setBoolean(PopcornSoundEnabledKey, enabled)
    }

    suspend fun setAudioFxEnabled(enabled: Boolean) {
        setBoolean(AudioFxEnabledKey, enabled)
    }

    suspend fun setAudioFxPreset(preset: AudioFxPreset) {
        context.playbackSettingsDataStore.edit { preferences ->
            preferences[AudioFxPresetKey] = preset.preferenceValue
            preferences[AudioFxEnabledKey] = preset != AudioFxPreset.Original
        }
    }

    suspend fun setAudioFxCustomGainDbPoints(gains: List<Float>) {
        context.playbackSettingsDataStore.edit { preferences ->
            preferences[AudioFxCustomGainDbPointsKey] = serializeAudioFxCustomGainDbPoints(gains)
            preferences[AudioFxPresetKey] = AudioFxPreset.Custom.preferenceValue
            preferences[AudioFxEnabledKey] = true
        }
    }

    private suspend fun setBoolean(
        key: Preferences.Key<Boolean>,
        value: Boolean,
    ) {
        context.playbackSettingsDataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}

private fun parseAudioFxCustomGainDbPoints(value: String?): List<Float> {
    if (value.isNullOrBlank()) {
        return AudioFxDefaultCustomGainDbPoints
    }
    val parsed = value.split(",")
        .mapNotNull { item -> item.trim().toFloatOrNull() }
    return normalizeAudioFxGainDbPoints(parsed)
}

private fun serializeAudioFxCustomGainDbPoints(gains: List<Float>): String {
    return normalizeAudioFxGainDbPoints(gains).joinToString(",") { gain ->
        gain.toString()
    }
}

private val ScratchEnabledKey = booleanPreferencesKey("scratch_enabled")
private val HidePlayerAxisEnabledKey = booleanPreferencesKey("hide_player_axis_enabled")
private val PopcornSoundEnabledKey = booleanPreferencesKey("popcorn_sound_enabled")
private val AudioFxEnabledKey = booleanPreferencesKey("audio_fx_enabled")
private val AudioFxPresetKey = stringPreferencesKey("audio_fx_preset")
private val AudioFxCustomGainDbPointsKey = stringPreferencesKey("audio_fx_custom_gain_db_points")
