package com.smartisan.music.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.smartisan.music.ui.navigation.DefaultDestinationOrder
import com.smartisan.music.ui.navigation.MaxBottomDestinationCount
import com.smartisan.music.ui.navigation.MinBottomDestinationCount
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.navigation.NavigationLayout
import com.smartisan.music.ui.navigation.navigationLayoutFromLegacyHiddenTabs
import com.smartisan.music.ui.navigation.normalizedNavigationLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSettingsTest {

    @Test
    fun defaultLayoutMatchesOriginalFivePlusThreeSplit() {
        val layout = NavigationLayout()

        assertEquals(
            listOf(
                MusicDestination.Playlist,
                MusicDestination.Artist,
                MusicDestination.Album,
                MusicDestination.Songs,
                MusicDestination.More,
            ),
            layout.bottomDestinations,
        )
        assertEquals(
            listOf(
                MusicDestination.Genre,
                MusicDestination.LovedSongs,
                MusicDestination.Folder,
            ),
            layout.overflowDestinations,
        )
    }

    @Test
    fun swapAcrossBoundaryKeepsZoneSizes() {
        val swapped = NavigationLayout().swap(
            MusicDestination.Playlist,
            MusicDestination.Genre,
        )

        assertEquals(MusicDestination.Genre, swapped.bottomDestinations.first())
        assertEquals(MusicDestination.Playlist, swapped.overflowDestinations.first())
        assertEquals(4, swapped.bottomCount)
    }

    @Test
    fun swappingWithinZoneChangesOnlyOrder() {
        val swapped = NavigationLayout().swap(
            MusicDestination.Artist,
            MusicDestination.Songs,
        )

        assertEquals(
            listOf(
                MusicDestination.Playlist,
                MusicDestination.Songs,
                MusicDestination.Album,
                MusicDestination.Artist,
                MusicDestination.More,
            ),
            swapped.bottomDestinations,
        )
        assertEquals(NavigationLayout().overflowDestinations, swapped.overflowDestinations)
    }

    @Test
    fun moreIsNeverMovable() {
        val layout = NavigationLayout()

        assertSame(layout, layout.swap(MusicDestination.More, MusicDestination.Genre))
        assertSame(layout, layout.demote(MusicDestination.More))
    }

    @Test
    fun promoteAndDemoteRespectThreeToFiveItemBottomBar() {
        val threeItemBottom = NavigationLayout(bottomCount = MinBottomDestinationCount)
        assertSame(threeItemBottom, threeItemBottom.demote(MusicDestination.Playlist))

        val promoted = threeItemBottom.promote(MusicDestination.Genre)
        assertEquals(MinBottomDestinationCount + 1, promoted.bottomCount)
        assertTrue(promoted.isPinned(MusicDestination.Genre))

        val fullBottom = NavigationLayout(bottomCount = MaxBottomDestinationCount)
        assertSame(fullBottom, fullBottom.promote(MusicDestination.Genre))
    }

    @Test
    fun demotedDestinationBecomesFirstOverflowItem() {
        val demoted = NavigationLayout().demote(MusicDestination.Artist)

        assertEquals(3, demoted.bottomCount)
        assertEquals(MusicDestination.Artist, demoted.overflowDestinations.first())
        assertFalse(demoted.isPinned(MusicDestination.Artist))
    }

    @Test
    fun moveNeverCrossesBottomOverflowBoundary() {
        val layout = NavigationLayout()

        val bottomMove = layout.move(MusicDestination.Songs, 1)
        val overflowMove = layout.move(MusicDestination.Genre, -1)

        assertEquals(layout, bottomMove)
        assertEquals(layout, overflowMove)
    }

    @Test
    fun accessibilityMoveReordersWithinEachZone() {
        val layout = NavigationLayout()

        val bottomMove = layout.move(MusicDestination.Artist, 1)
        val overflowMove = layout.move(MusicDestination.Folder, -1)

        assertEquals(
            listOf(
                MusicDestination.Playlist,
                MusicDestination.Album,
                MusicDestination.Artist,
                MusicDestination.Songs,
                MusicDestination.More,
            ),
            bottomMove.bottomDestinations,
        )
        assertEquals(
            listOf(
                MusicDestination.Genre,
                MusicDestination.Folder,
                MusicDestination.LovedSongs,
            ),
            overflowMove.overflowDestinations,
        )
    }

    @Test
    fun normalizationDropsUnknownsAndDuplicatesAndAppendsMissingRoutes() {
        val normalized = normalizedNavigationLayout(
            routes = listOf("folder", "unknown", "folder", "songs"),
            bottomCount = 99,
        )

        assertEquals(MusicDestination.Folder, normalized.orderedDestinations[0])
        assertEquals(MusicDestination.Songs, normalized.orderedDestinations[1])
        assertEquals(DefaultDestinationOrder.toSet(), normalized.orderedDestinations.toSet())
        assertEquals(MaxBottomDestinationCount, normalized.bottomCount)
    }

    @Test
    fun legacyHiddenTabsBecomeOverflowInsteadOfUnreachableDestinations() {
        val migrated = navigationLayoutFromLegacyHiddenTabs(
            hiddenRoutes = setOf(MusicDestination.Album.route),
        )

        assertFalse(migrated.isPinned(MusicDestination.Album))
        assertTrue(MusicDestination.Album in migrated.overflowDestinations)
        assertEquals(3, migrated.bottomCount)
    }

    @Test
    fun legacyMigrationRestoresMinimumTwoContentDestinations() {
        val migrated = navigationLayoutFromLegacyHiddenTabs(
            hiddenRoutes = DefaultDestinationOrder.map(MusicDestination::route).toSet(),
        )

        assertEquals(MinBottomDestinationCount, migrated.bottomCount)
        assertEquals(3, migrated.bottomDestinations.size)
    }

    @Test
    fun storedLegacyHiddenTabsUseTheCompatibilityMigration() {
        val preferences = mutablePreferencesOf(
            stringSetPreferencesKey("hidden_tabs") to setOf(MusicDestination.Album.route),
        )

        val layout = preferences.toNavigationSettings().layout

        assertFalse(layout.isPinned(MusicDestination.Album))
        assertTrue(MusicDestination.Album in layout.overflowDestinations)
    }

    @Test
    fun versionTwoLayoutTakesPrecedenceOverLegacyHiddenTabs() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("ordered_routes_v2") to listOf(
                MusicDestination.Folder,
                MusicDestination.Songs,
                MusicDestination.Playlist,
                MusicDestination.Artist,
                MusicDestination.Album,
                MusicDestination.Genre,
                MusicDestination.LovedSongs,
            ).joinToString("|") { it.route },
            intPreferencesKey("bottom_count_v2") to MinBottomDestinationCount,
            stringSetPreferencesKey("hidden_tabs") to setOf(MusicDestination.Folder.route),
        )

        val layout = preferences.toNavigationSettings().layout

        assertTrue(layout.isPinned(MusicDestination.Folder))
        assertEquals(MinBottomDestinationCount, layout.bottomCount)
    }

    @Test
    fun temporaryPinDoesNotMutatePersistedLayout() {
        val layout = NavigationLayout(bottomCount = MinBottomDestinationCount)
        val effective = layout.bottomDestinationsEnsuring(MusicDestination.Songs)

        assertTrue(MusicDestination.Songs in effective)
        assertEquals(MinBottomDestinationCount, layout.bottomCount)
        assertFalse(layout.isPinned(MusicDestination.Songs))
    }
}
