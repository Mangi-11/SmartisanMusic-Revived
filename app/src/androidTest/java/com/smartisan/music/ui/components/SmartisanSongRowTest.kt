package com.smartisan.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smartisan.music.ui.songs.SmartisanSongRow
import com.smartisan.music.ui.songs.SongPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SmartisanSongRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun moreButtonDoesNotStartPlayback() {
        var plays = 0
        var actions = 0
        lateinit var moreLabel: String
        compose.setContent {
            moreLabel =
                androidx.compose.ui.res.stringResource(com.smartisan.music.R.string.tab_more)
            Column(Modifier.width(360.dp)) {
                SmartisanSongRow(
                    track,
                    { plays++ },
                    { actions++ },
                    Modifier.testTag("song"),
                    playback = SongPlaybackState(null, false),
                )
            }
        }
        compose.onNodeWithContentDescription(moreLabel).performClick()
        compose.runOnIdle {
            assertEquals(0, plays)
            assertEquals(1, actions)
        }
        compose.onNodeWithTag("song").performClick()
        compose.runOnIdle {
            assertEquals(1, plays)
            assertEquals(1, actions)
        }
    }

    @Test
    fun editSelectionUsesLatestExternalStateWithoutPlaying() {
        val selected = mutableStateOf(false)
        var plays = 0
        val selections = mutableListOf<Boolean>()
        compose.setContent {
            Column(Modifier.width(360.dp)) {
                SmartisanSongRow(
                    track,
                    { plays++ },
                    {},
                    Modifier.testTag("song"),
                    editMode = true,
                    selected = selected.value,
                    onSelectionChange = {
                        selections += it
                        selected.value = it
                    },
                    playback = SongPlaybackState(null, false),
                )
            }
        }
        compose.onNodeWithTag("song").performClick()
        compose.onNodeWithTag("song").performClick()
        compose.runOnIdle {
            assertEquals(listOf(true, false), selections)
            assertEquals(0, plays)
        }
    }

    private val track =
        MediaItem.Builder()
            .setMediaId("fixture-song")
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle("Sample song").setArtist("Sample artist").build()
            )
            .build()
}
