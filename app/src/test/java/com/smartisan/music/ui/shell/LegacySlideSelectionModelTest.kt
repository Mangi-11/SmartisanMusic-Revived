package com.smartisan.music.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySlideSelectionModelTest {

    @Test
    fun changesThroughSelectsAnchorAndRestoresShrunkRange() {
        val model = LegacySlideSelectionModel()
        model.begin(
            position = 1,
            key = "song-1",
            selected = false,
        )

        val changes = model.changesThrough(
            position = 4,
            keyAtPosition = ::keyAtPosition,
            isSelected = { false },
        )

        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-1", true),
                LegacySlideSelectionChange("song-2", true),
                LegacySlideSelectionChange("song-3", true),
                LegacySlideSelectionChange("song-4", true),
            ),
            changes,
        )
        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-4", false),
                LegacySlideSelectionChange("song-3", false),
            ),
            model.changesThrough(
                position = 2,
                keyAtPosition = ::keyAtPosition,
                isSelected = { false },
            ),
        )
    }

    @Test
    fun changesThroughDeselectsWhenAnchorWasSelected() {
        val model = LegacySlideSelectionModel()
        model.begin(
            position = 3,
            key = "song-3",
            selected = true,
        )

        val changes = model.changesThrough(
            position = 1,
            keyAtPosition = ::keyAtPosition,
            isSelected = { key -> key in setOf("song-1", "song-2", "song-3") },
        )

        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-3", false),
                LegacySlideSelectionChange("song-2", false),
                LegacySlideSelectionChange("song-1", false),
            ),
            changes,
        )
    }

    @Test
    fun changesThroughReselectsWhenDeselectRangeShrinks() {
        val model = LegacySlideSelectionModel()
        model.begin(
            position = 3,
            key = "song-3",
            selected = true,
        )

        model.changesThrough(
            position = 1,
            keyAtPosition = ::keyAtPosition,
            isSelected = { key -> key in setOf("song-1", "song-2", "song-3") },
        )

        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-1", true),
            ),
            model.changesThrough(
                position = 2,
                keyAtPosition = ::keyAtPosition,
                isSelected = { key -> key in setOf("song-2", "song-3") },
            ),
        )
    }

    @Test
    fun changesThroughCanReapplyAfterRangeExpandsAgain() {
        val model = LegacySlideSelectionModel()
        model.begin(
            position = 1,
            key = "song-1",
            selected = false,
        )

        model.changesThrough(
            position = 4,
            keyAtPosition = ::keyAtPosition,
            isSelected = { false },
        )
        model.changesThrough(
            position = 2,
            keyAtPosition = ::keyAtPosition,
            isSelected = { false },
        )

        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-3", true),
                LegacySlideSelectionChange("song-4", true),
            ),
            model.changesThrough(
                position = 4,
                keyAtPosition = ::keyAtPosition,
                isSelected = { false },
            ),
        )
    }

    @Test
    fun changesThroughRestoresAnchorWhenRangeCollapsesToStart() {
        val model = LegacySlideSelectionModel()
        model.begin(
            position = 1,
            key = "song-1",
            selected = false,
        )

        model.changesThrough(
            position = 4,
            keyAtPosition = ::keyAtPosition,
            isSelected = { false },
        )

        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-4", false),
                LegacySlideSelectionChange("song-3", false),
                LegacySlideSelectionChange("song-2", false),
                LegacySlideSelectionChange("song-1", false),
            ),
            model.changesThrough(
                position = 1,
                keyAtPosition = ::keyAtPosition,
                isSelected = { false },
            ),
        )
    }

    @Test
    fun changesThroughStillTogglesAnchorBeforeRangeExpands() {
        val model = LegacySlideSelectionModel()
        model.begin(
            position = 1,
            key = "song-1",
            selected = false,
        )

        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-1", true),
            ),
            model.changesThrough(
                position = 1,
                keyAtPosition = ::keyAtPosition,
                isSelected = { false },
            ),
        )
    }

    @Test
    fun changesThroughSkipsHeadersAndAlreadyMatchingItems() {
        val model = LegacySlideSelectionModel()
        model.begin(
            position = 0,
            key = "song-0",
            selected = false,
        )

        val changes = model.changesThrough(
            position = 3,
            keyAtPosition = { position ->
                if (position == 2) null else keyAtPosition(position)
            },
            isSelected = { key -> key == "song-1" },
        )

        assertEquals(
            listOf(
                LegacySlideSelectionChange("song-0", true),
                LegacySlideSelectionChange("song-3", true),
            ),
            changes,
        )
    }

    private fun keyAtPosition(position: Int): String? {
        return if (position >= 0) "song-$position" else null
    }
}
