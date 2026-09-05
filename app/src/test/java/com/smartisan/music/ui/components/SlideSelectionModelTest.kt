package com.smartisan.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SlideSelectionModelTest {

    @Test
    fun changesThroughSelectsAnchorAndRestoresShrunkRange() {
        val model = SlideSelectionModel()
        model.begin(
            position = 1,
            key = "song-1",
            selected = false,
        )

        val changes =
            model.changesThrough(
                position = 4,
                keyAtPosition = ::keyAtPosition,
                isSelected = { false },
            )

        assertEquals(
            listOf(
                SlideSelectionChange("song-1", true),
                SlideSelectionChange("song-2", true),
                SlideSelectionChange("song-3", true),
                SlideSelectionChange("song-4", true),
            ),
            changes,
        )
        assertEquals(
            listOf(
                SlideSelectionChange("song-4", false),
                SlideSelectionChange("song-3", false),
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
        val model = SlideSelectionModel()
        model.begin(
            position = 3,
            key = "song-3",
            selected = true,
        )

        val changes =
            model.changesThrough(
                position = 1,
                keyAtPosition = ::keyAtPosition,
                isSelected = { key -> key in setOf("song-1", "song-2", "song-3") },
            )

        assertEquals(
            listOf(
                SlideSelectionChange("song-3", false),
                SlideSelectionChange("song-2", false),
                SlideSelectionChange("song-1", false),
            ),
            changes,
        )
    }

    @Test
    fun changesThroughReselectsWhenDeselectRangeShrinks() {
        val model = SlideSelectionModel()
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
            listOf(SlideSelectionChange("song-1", true)),
            model.changesThrough(
                position = 2,
                keyAtPosition = ::keyAtPosition,
                isSelected = { key -> key in setOf("song-2", "song-3") },
            ),
        )
    }

    @Test
    fun changesThroughCanReapplyAfterRangeExpandsAgain() {
        val model = SlideSelectionModel()
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
                SlideSelectionChange("song-3", true),
                SlideSelectionChange("song-4", true),
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
        val model = SlideSelectionModel()
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
                SlideSelectionChange("song-4", false),
                SlideSelectionChange("song-3", false),
                SlideSelectionChange("song-2", false),
                SlideSelectionChange("song-1", false),
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
        val model = SlideSelectionModel()
        model.begin(
            position = 1,
            key = "song-1",
            selected = false,
        )

        assertEquals(
            listOf(SlideSelectionChange("song-1", true)),
            model.changesThrough(
                position = 1,
                keyAtPosition = ::keyAtPosition,
                isSelected = { false },
            ),
        )
    }

    @Test
    fun changesThroughSkipsHeadersAndAlreadyMatchingItems() {
        val model = SlideSelectionModel()
        model.begin(
            position = 0,
            key = "song-0",
            selected = false,
        )

        val changes =
            model.changesThrough(
                position = 3,
                keyAtPosition = { position ->
                    if (position == 2) null else keyAtPosition(position)
                },
                isSelected = { key -> key == "song-1" },
            )

        assertEquals(
            listOf(
                SlideSelectionChange("song-0", true),
                SlideSelectionChange("song-3", true),
            ),
            changes,
        )
    }

    private fun keyAtPosition(position: Int): String? {
        return if (position >= 0) "song-$position" else null
    }
}
