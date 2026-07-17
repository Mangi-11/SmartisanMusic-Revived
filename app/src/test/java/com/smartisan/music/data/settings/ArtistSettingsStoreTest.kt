package com.smartisan.music.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSettingsStoreTest {

    @Test
    fun parseArtistSeparatorInputKeepsCommonSeparators() {
        val separators = parseArtistSeparatorInput(" /;、, ")

        assertEquals(setOf("/", ";", "、", ","), separators)
    }

    @Test
    fun normalizeArtistSeparatorsDropsBlankAndDuplicateValues() {
        val separators = setOf(" / ", "", " /", " ; ").normalizedArtistSeparators()

        assertEquals(setOf("/", ";"), separators)
    }
}
