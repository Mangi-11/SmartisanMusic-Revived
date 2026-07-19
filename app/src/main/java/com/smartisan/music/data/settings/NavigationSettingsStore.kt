package com.smartisan.music.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.navigation.NavigationLayout
import com.smartisan.music.ui.navigation.navigationLayoutFromLegacyHiddenTabs
import com.smartisan.music.ui.navigation.normalizedNavigationLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val NavigationSettingsStoreName = "navigation_settings"
private const val RouteSeparator = "|"

private val Context.navigationSettingsDataStore by preferencesDataStore(
    name = NavigationSettingsStoreName,
)

data class NavigationSettings(
    val layout: NavigationLayout = NavigationLayout(),
)

class NavigationSettingsStore(
    private val context: Context,
) {

    val settings: Flow<NavigationSettings> = context.navigationSettingsDataStore.data
        .map(Preferences::toNavigationSettings)
        .distinctUntilChanged()

    suspend fun commitLayout(layout: NavigationLayout) {
        context.navigationSettingsDataStore.edit { preferences ->
            preferences.writeLayout(layout)
        }
    }

    suspend fun setTabPinned(route: String, pinned: Boolean) {
        val destination = MusicDestination.fromRoute(route)?.takeIf(MusicDestination::movable) ?: return
        context.navigationSettingsDataStore.edit { preferences ->
            val current = preferences.toNavigationSettings().layout
            val updated = if (pinned) current.promote(destination) else current.demote(destination)
            preferences.writeLayout(updated)
        }
    }
}

internal fun Preferences.toNavigationSettings(): NavigationSettings {
    val encodedOrder = this[OrderedRoutesKey]
    val layout = if (encodedOrder == null) {
        navigationLayoutFromLegacyHiddenTabs(this[LegacyHiddenTabsKey].orEmpty())
    } else {
        normalizedNavigationLayout(
            routes = encodedOrder.split(RouteSeparator).filter(String::isNotBlank),
            bottomCount = this[BottomCountKey] ?: NavigationLayout().bottomCount,
        )
    }
    return NavigationSettings(layout = layout)
}

private fun NavigationLayout.normalized(): NavigationLayout {
    return normalizedNavigationLayout(
        routes = orderedDestinations.map(MusicDestination::route),
        bottomCount = bottomCount,
    )
}

private fun MutablePreferences.writeLayout(layout: NavigationLayout) {
    val normalized = layout.normalized()
    this[OrderedRoutesKey] = normalized.orderedDestinations.joinToString(RouteSeparator, transform = MusicDestination::route)
    this[BottomCountKey] = normalized.bottomCount
}

private val OrderedRoutesKey = stringPreferencesKey("ordered_routes_v2")
private val BottomCountKey = intPreferencesKey("bottom_count_v2")
private val LegacyHiddenTabsKey = stringSetPreferencesKey("hidden_tabs")
