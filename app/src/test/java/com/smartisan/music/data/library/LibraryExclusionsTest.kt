package com.smartisan.music.data.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryExclusionsTest {

    @Test
    fun isMediaHiddenMatchesDirectoryExactly() {
        val exclusions = LibraryExclusions(
            hiddenDirectoryKeys = setOf("Music/"),
        )

        assertTrue(exclusions.isMediaHidden(mediaId = "1", relativePath = "Music/"))
        assertFalse(exclusions.isMediaHidden(mediaId = "2", relativePath = "Music/Sub/"))
        assertFalse(exclusions.isMediaHidden(mediaId = "3", relativePath = "Music/Sub"))
    }

    @Test
    fun isMediaHiddenHonorsMediaIdOverrides() {
        val exclusions = LibraryExclusions(
            hiddenMediaIds = setOf("song-1"),
            hiddenDirectoryKeys = setOf("Music/"),
        )

        assertTrue(exclusions.isMediaHidden(mediaId = "song-1", relativePath = "Music/Sub/"))
        assertFalse(exclusions.isMediaHidden(mediaId = "song-2", relativePath = "Music/Sub/"))
    }
}

