package com.smartisan.music.data.settings

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun missingPreferenceDefaultsToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromPreference(null))
        assertEquals(ThemeMode.System, ThemeMode.fromPreference("unknown"))
    }

    @Test
    fun preferenceValuesRoundTripToModes() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromPreference(mode.preferenceValue))
        }
    }

    @Test
    fun modesMapToAppCompatNightModes() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.System.appCompatNightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.Light.appCompatNightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.Dark.appCompatNightMode)
    }
}
