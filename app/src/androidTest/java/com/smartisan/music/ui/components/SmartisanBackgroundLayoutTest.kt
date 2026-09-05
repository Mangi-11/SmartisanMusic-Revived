package com.smartisan.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartisan.music.R
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.shell.playback.PlaybackBar
import com.smartisan.music.ui.shell.playback.PlaybackBarSnapshot
import com.smartisan.music.ui.shell.tabs.MusicBottomBar
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartisanBackgroundLayoutTest {
    @get:Rule val compose = createComposeRule()

    private var density = 1f
    private var navigationHeightPx = 0f
    private var playbackHeightPx = 0f

    @Test
    fun painterBackgroundPreservesContentHeightUnderBoundedConstraints() {
        var parentHeight by mutableStateOf(220.dp)
        compose.setContent {
            val currentDensity = LocalDensity.current
            SideEffect { density = currentDensity.density }
            Box(Modifier.size(280.dp, parentHeight).testTag(ParentTag)) {
                Column(
                    Modifier.fillMaxWidth()
                        .smartisanPainterBackground(ColorPainter(Color.Blue))
                        .testTag(BackgroundTag)
                ) {
                    Spacer(Modifier.height(31.dp))
                }
            }
        }

        listOf(220.dp, 460.dp).forEach { height ->
            compose.runOnIdle { parentHeight = height }
            val parent = bounds(ParentTag)
            val background = bounds(BackgroundTag)
            assertEquals(parent.width, background.width, PixelTolerance)
            assertEquals(31f * density, background.height, PixelTolerance)
            assertEquals(parent.top, background.top, PixelTolerance)
            assertTrue(background.bottom < parent.bottom)
        }
    }

    @Test
    fun navigationAloneStaysAtTheBottomWithoutCoveringPageContent() {
        assertBottomChromeLayout(withPlayback = false)
    }

    @Test
    fun playbackAndNavigationStayAtTheBottomWithoutCoveringPageContent() {
        assertBottomChromeLayout(withPlayback = true)
    }

    private fun assertBottomChromeLayout(withPlayback: Boolean) {
        var parentHeight by mutableStateOf(220.dp)
        compose.setContent { BottomChromeSample(parentHeight, withPlayback) }

        listOf(220.dp, 460.dp).forEach { height ->
            compose.runOnIdle { parentHeight = height }
            val parent = bounds(ParentTag)
            val chrome = bounds(ChromeTag)
            val navigation = bounds(NavigationTag)
            val expectedChromeHeight =
                navigationHeightPx + if (withPlayback) playbackHeightPx else 0f

            assertEquals(parent.width, chrome.width, PixelTolerance)
            assertEquals(parent.bottom, chrome.bottom, PixelTolerance)
            assertEquals(expectedChromeHeight, chrome.height, PixelTolerance)
            assertEquals(navigationHeightPx, navigation.height, PixelTolerance)
            assertEquals(parent.bottom, navigation.bottom, PixelTolerance)
            assertTrue("The page must retain space above bottom chrome", chrome.top > parent.top)

            if (withPlayback) {
                val playback = bounds(PlaybackTag)
                assertEquals(playbackHeightPx, playback.height, PixelTolerance)
                assertEquals(chrome.top, playback.top, PixelTolerance)
                assertEquals(navigation.top, playback.bottom, PixelTolerance)
            }

            // A tagged page may still be reported as displayed while a sibling's oversized
            // background covers it. Check the composed result, not just semantics visibility.
            val image = compose.onNodeWithTag(ParentTag).captureToImage()
            val pixels = IntArray(image.width * image.height)
            image.readPixels(pixels)
            val sampleY = (20f * density).roundToInt().coerceAtMost(image.height - 1)
            assertEquals(PageColor.toArgb(), pixels[sampleY * image.width + image.width / 2])
        }
    }

    @Composable
    private fun BottomChromeSample(parentHeight: Dp, withPlayback: Boolean) {
        val currentDensity = LocalDensity.current
        val navigationInset = WindowInsets.navigationBars.getBottom(currentDensity)
        val tabsHeight = dimensionResource(R.dimen.smartisan_tabswitch_tabbar_height)
        SideEffect {
            density = currentDensity.density
            navigationHeightPx = with(currentDensity) { tabsHeight.toPx() } + navigationInset
            playbackHeightPx = with(currentDensity) { PlaybackHeight.toPx() }
        }
        Box(Modifier.size(320.dp, parentHeight).testTag(ParentTag)) {
            Box(Modifier.fillMaxSize().background(PageColor))
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).testTag(ChromeTag)) {
                if (withPlayback) {
                    PlaybackBar(
                        snapshot = PlaybackBarSnapshot(mediaItem = SampleMediaItem),
                        shown = true,
                        favoriteIds = emptySet(),
                        artworkBitmap = null,
                        onHidden = {},
                        onOpenPlayback = {},
                        onToggleFavorite = {},
                        onPrevious = {},
                        onPlayPause = {},
                        onNext = {},
                        modifier =
                            Modifier.fillMaxWidth().height(PlaybackHeight).testTag(PlaybackTag),
                    )
                }
                MusicBottomBar(
                    currentDestination = MusicDestination.Songs,
                    destinations =
                        listOf(
                            MusicDestination.Playlist,
                            MusicDestination.Artist,
                            MusicDestination.Album,
                            MusicDestination.Songs,
                            MusicDestination.More,
                        ),
                    onDestinationSelected = {},
                    onEditRequested = {},
                    modifier = Modifier.testTag(NavigationTag),
                    topChromeVisible = !withPlayback,
                )
            }
        }
    }

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val PixelTolerance = 1f
        const val ParentTag = "bounded-parent"
        const val BackgroundTag = "content-background"
        const val ChromeTag = "bottom-chrome"
        const val NavigationTag = "bottom-navigation"
        const val PlaybackTag = "bottom-playback"
        val PlaybackHeight = 67.dp
        val PageColor = Color(0xFF14A7B8)
        val SampleMediaItem: MediaItem =
            MediaItem.Builder()
                .setMediaId("background-layout-test-track")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("A").setArtist("B").build())
                .build()
    }
}
