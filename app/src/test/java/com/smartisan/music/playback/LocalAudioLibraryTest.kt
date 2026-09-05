package com.smartisan.music.playback

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioLibraryTest {

    @Test
    fun `shouldSkipMediaScannerPath skips hidden directories`() {
        assertTrue(shouldSkipMediaScannerPath(".MediaTrash/song.mp3"))
        assertTrue(shouldSkipMediaScannerPath("DCIM/.mediaTrash/song.mp3"))
    }

    @Test
    fun `shouldSkipMediaScannerPath skips hidden files`() {
        assertTrue(shouldSkipMediaScannerPath("Download/.trashed-1778146976-song.mp3"))
    }

    @Test
    fun `shouldSkipMediaScannerPath keeps normal audio paths`() {
        assertFalse(shouldSkipMediaScannerPath("Music/Album/song.mp3"))
    }

    @Test
    fun `repairMetadataEncoding repairs utf8 text decoded as latin1`() {
        val mojibake =
            String("周杰伦".toByteArray(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)

        assertEquals("周杰伦", mojibake.repairMetadataEncoding())
    }

    @Test
    fun `repairMetadataEncoding keeps normal metadata`() {
        assertEquals("Björk", "Björk".repairMetadataEncoding())
        assertEquals("São Paulo", "São Paulo".repairMetadataEncoding())
        assertEquals("naïve", "naïve".repairMetadataEncoding())
        assertEquals("周杰伦", "周杰伦".repairMetadataEncoding())
    }

    @Test
    fun `stableAudioLibraryKey normalizes path and display name`() {
        assertEquals(
            "external_primary:music/album/song.mp3",
            stableAudioLibraryKey(
                volumeName = "external_primary",
                relativePath = "Music\\Album",
                displayName = " Song.MP3 ",
            ),
        )
    }

    @Test
    fun `stableAudioLibraryKey uses readable volume fallback`() {
        assertEquals(
            "unknown-volume:song.mp3",
            stableAudioLibraryKey(
                volumeName = null,
                relativePath = null,
                displayName = "song.mp3",
            ),
        )
    }

    @Test
    fun `stableAudioLibraryKey rejects blank display name`() {
        assertEquals(null, stableAudioLibraryKey("external", "Music", " "))
    }
}
