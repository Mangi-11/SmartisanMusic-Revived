package com.smartisan.music.ui.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartisan.music.R
import com.smartisan.music.ui.shell.playback.PlaybackBar
import com.smartisan.music.ui.shell.playback.PlaybackBarSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun coverAndMetadataOpenPlaybackWithoutChangingPlaybackOrClickingThePage() {
        val events = mutableListOf<String>()
        var callbackVersion by mutableStateOf(1)
        var coverCenter = Offset.Zero
        var shadowCenter = Offset.Zero
        var playLabel = ""
        compose.setContent {
            val density = LocalDensity.current
            val coverPadding = dimensionResource(R.dimen.float_cover_padding_left)
            val coverSize = dimensionResource(R.dimen.daily_recommend_cover_width)
            val contentHeight = dimensionResource(R.dimen.play_back_content_height)
            val currentPlayLabel = stringResource(R.string.play)
            val currentCallbackVersion = callbackVersion
            SideEffect {
                coverCenter =
                    with(density) {
                        Offset(
                            (coverPadding + coverSize / 2).toPx(),
                            (6.dp + contentHeight / 2).toPx(),
                        )
                    }
                shadowCenter = with(density) { Offset(180.dp.toPx(), 2.dp.toPx()) }
                playLabel = currentPlayLabel
            }
            Box(Modifier.size(360.dp, 180.dp)) {
                Box(Modifier.fillMaxSize().clickable { events += "page" })
                PlaybackBar(
                    snapshot = PlaybackBarSnapshot(mediaItem = SampleMediaItem),
                    shown = true,
                    favoriteIds = emptySet(),
                    artworkBitmap = null,
                    onHidden = { events += "hidden" },
                    onOpenPlayback = { events += "open:$currentCallbackVersion" },
                    onToggleFavorite = { events += "favorite" },
                    onPrevious = { events += "previous" },
                    onPlayPause = { events += "play" },
                    onNext = { events += "next" },
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(67.dp)
                            .align(Alignment.BottomCenter)
                            .testTag(BarTag),
                )
            }
        }

        compose.onNodeWithText(SampleTitle).assertIsDisplayed()
        compose.onNodeWithTag(BarTag).performTouchInput { click(coverCenter) }
        compose.onNodeWithText(SampleTitle).performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf("open:1", "open:1"), events) }

        compose.runOnIdle { callbackVersion = 2 }
        compose.onNodeWithTag(BarTag).performTouchInput { click(coverCenter) }
        compose.onNodeWithText(SampleTitle).performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(listOf("open:1", "open:1", "open:2", "open:2"), events)
        }

        compose.onNodeWithContentDescription(playLabel).performTouchInput { click(center) }
        compose.onNodeWithTag(BarTag).performTouchInput { click(shadowCenter) }
        compose.runOnIdle {
            assertEquals(listOf("open:1", "open:1", "open:2", "open:2", "play"), events)
        }
    }

    private companion object {
        const val BarTag = "playback-bar"
        const val SampleTitle = "Sample"
        val SampleMediaItem: MediaItem =
            MediaItem.Builder()
                .setMediaId("playback-bar-test-track")
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(SampleTitle).setArtist("Artist").build()
                )
                .build()
    }
}
