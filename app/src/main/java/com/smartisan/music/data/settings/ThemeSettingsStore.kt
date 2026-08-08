package com.smartisan.music.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

private const val ThemeSettingsPreferencesName = "theme_settings"
private const val ThemeModeKey = "theme_mode"

enum class ThemeMode(
    val preferenceValue: String,
    val labelRes: Int,
) {
    System("system", com.smartisan.music.R.string.theme_mode_system),
    Light("light", com.smartisan.music.R.string.theme_mode_light),
    Dark("dark", com.smartisan.music.R.string.theme_mode_dark),
    ;

    val appCompatNightMode: Int
        get() = when (this) {
            System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Light -> AppCompatDelegate.MODE_NIGHT_NO
            Dark -> AppCompatDelegate.MODE_NIGHT_YES
        }

    companion object {
        fun fromPreference(value: String?): ThemeMode {
            return entries.firstOrNull { mode -> mode.preferenceValue == value } ?: System
        }
    }
}

/**
 * Theme mode is kept in a small synchronous preference because MainActivity must apply it
 * before AppCompat creates the view hierarchy. This avoids a light/dark flash on cold start.
 */
class ThemeSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        ThemeSettingsPreferencesName,
        Context.MODE_PRIVATE,
    )

    fun currentMode(): ThemeMode {
        return ThemeMode.fromPreference(preferences.getString(ThemeModeKey, null))
    }

    fun setMode(mode: ThemeMode) {
        preferences.edit()
            .putString(ThemeModeKey, mode.preferenceValue)
            .apply()
    }
}
