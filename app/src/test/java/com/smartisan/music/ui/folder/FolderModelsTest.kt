package com.smartisan.music.ui.folder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderModelsTest {

    @Test
    fun `isMediaInDirectory matches normalized directory key`() {
        assertTrue(
            isMediaInDirectory(
                relativePath = "Music/Albums",
                directoryKey = "Music/Albums/",
            ),
        )
    }

    @Test
    fun `isMediaInDirectory does not match child directory`() {
        assertFalse(
            isMediaInDirectory(
                relativePath = "Music/Albums/Live/",
                directoryKey = "Music/Albums/",
            ),
        )
    }

    @Test
    fun `isMediaInDirectory rejects blank directory key`() {
        assertFalse(
            isMediaInDirectory(
                relativePath = "Music/",
                directoryKey = " ",
            ),
        )
    }
}
