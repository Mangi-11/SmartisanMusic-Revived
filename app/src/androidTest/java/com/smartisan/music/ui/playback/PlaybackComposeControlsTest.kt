package com.smartisan.music.ui.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartisan.music.R
import com.smartisan.music.ui.components.SmartisanRatingBar
import com.smartisan.music.ui.shell.playback.PlaybackBar
import com.smartisan.music.ui.shell.playback.PlaybackBarSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackComposeControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun volumeRejectsTrackGesturesAndAcceptsThumbGestures() {
        val changes = mutableListOf<Float>()
        compose.setContent {
            var value by remember { mutableFloatStateOf(.5f) }
            PlaybackVolumeBar(value, 300.dp, Modifier.testTag("volume")) {
                changes += it
                value = it
            }
        }
        val volume = compose.onNodeWithTag("volume")
        volume.performTouchInput {
            down(Offset(width * .1f, center.y))
            moveTo(Offset(width * .9f, center.y))
            up()
        }
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        volume.performTouchInput {
            down(center)
            moveTo(Offset(width * .9f, center.y))
            up()
        }
        compose.runOnIdle {
            assertTrue(changes.isNotEmpty())
            assertTrue(changes.last() > .9f)
        }
    }

    @Test
    fun ratingPreviewsDuringDragAndCommitsOnceOnRelease() {
        val changes = mutableListOf<Int>()
        compose.setContent {
            var score by remember { mutableIntStateOf(0) }
            SmartisanRatingBar(
                score,
                {
                    changes += it
                    score = it
                },
                Modifier.height(40.dp).testTag("rating"),
            )
        }
        val rating = compose.onNodeWithTag("rating")
        rating.performTouchInput {
            down(Offset(width * .2f, center.y))
            moveTo(Offset(width * .7f, center.y))
        }
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        rating.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(4), changes) }
    }

    @Test
    fun ratingStillPreviewsAfterTwoScoreUpdates() {
        val changes = mutableListOf<Int>()
        compose.setContent {
            var score by remember { mutableIntStateOf(0) }
            SmartisanRatingBar(
                score,
                {
                    changes += it
                    score = it
                },
                Modifier.height(40.dp).testTag("rating"),
            )
        }
        val rating = compose.onNodeWithTag("rating")
        fun assertPreview(score: Int) {
            rating.assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(score.toFloat(), 0f..5f, 4),
                )
            )
        }
        rating.performTouchInput {
            down(Offset(width * .2f, center.y))
            moveTo(Offset(width * .7f, center.y))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(4), changes) }
        rating.performTouchInput {
            down(Offset(width * .7f, center.y))
            moveTo(Offset(width * .3f, center.y))
        }
        assertPreview(2)
        compose.runOnIdle { assertEquals(listOf(4), changes) }
        rating.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(4, 2), changes) }
        rating.performTouchInput {
            down(Offset(width * .3f, center.y))
            moveTo(Offset(width * .95f, center.y))
        }
        assertPreview(5)
        compose.runOnIdle { assertEquals(listOf(4, 2), changes) }
        rating.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(4, 2, 5), changes) }
    }

    @Test
    fun playbackBarKeepsMicroMoveClicksAndBlocksBackground() {
        var nextClicks = 0
        var backgroundClicks = 0
        val nextDescription =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.next_song)
        compose.setContent {
            Box(Modifier.width(360.dp).height(67.dp)) {
                Box(Modifier.fillMaxSize().clickable { backgroundClicks++ })
                PlaybackBar(
                    snapshot =
                        PlaybackBarSnapshot(
                            MediaItem.Builder().setMediaId("synthetic-track").build()
                        ),
                    shown = true,
                    favoriteIds = emptySet(),
                    artworkBitmap = null,
                    onHidden = {},
                    onOpenPlayback = {},
                    onToggleFavorite = {},
                    onPrevious = {},
                    onPlayPause = {},
                    onNext = { nextClicks++ },
                    modifier = Modifier.fillMaxSize().testTag("bar"),
                )
            }
        }
        compose.onNodeWithContentDescription(nextDescription).performTouchInput {
            down(center)
            moveTo(center + Offset(1f, 0f))
            up()
        }
        compose.onNodeWithTag("bar").performTouchInput {
            down(Offset(1f, center.y))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, nextClicks)
            assertEquals(0, backgroundClicks)
        }
    }

    @Test
    fun playbackPanelKeepsMicroMoveClicksAndBlocksPanelBlankSpace() {
        var lyricsClicks = 0
        var dismissClicks = 0
        var backgroundClicks = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            Box(Modifier.width(360.dp).height(420.dp)) {
                Box(Modifier.fillMaxSize().clickable { backgroundClicks++ })
                PlaybackMoreActionsOverlay(
                    visible = true,
                    favoriteEnabled = false,
                    visualPage = PlaybackVisualPage.Cover,
                    scratchEnabled = false,
                    sleepTimerActive = false,
                    addToPlaylistEnabled = true,
                    shareEnabled = true,
                    callbacks =
                        PlaybackMoreActionCallbacks(
                            onAddToPlaylistClick = {},
                            onAddToQueueClick = {},
                            onFavoriteToggle = {},
                            onShareClick = {},
                            onLyricsToggle = { lyricsClicks++ },
                            onSleepTimerClick = {},
                            onScratchToggle = {},
                            onDeleteClick = {},
                            onDismissRequest = { dismissClicks++ },
                        ),
                    modifier = Modifier.fillMaxSize().testTag("panel"),
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.lyrics)).performTouchInput {
            down(center)
            moveTo(center + Offset(1f, 0f))
            up()
        }
        val panel = compose.onNodeWithTag("panel")
        val titleY =
            compose
                .onNodeWithText(context.getString(R.string.select_action))
                .fetchSemanticsNode()
                .boundsInRoot
                .center
                .y - panel.fetchSemanticsNode().boundsInRoot.top
        panel.performTouchInput {
            down(Offset(1f, titleY))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, lyricsClicks)
            assertEquals(0, dismissClicks)
            assertEquals(0, backgroundClicks)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.cancel)).performTouchInput {
            down(center)
            moveTo(center + Offset(1f, 0f))
            up()
        }
        compose.runOnIdle { assertEquals(1, dismissClicks) }
    }

    @Test
    fun queueHandleCommitsOneMediaIndexMoveAndCancellationCommitsNothing() {
        val moves = mutableListOf<Pair<Int, Int>>()
        compose.setContent {
            val tracks = remember {
                (3..6).map { index ->
                    PlaybackQueueTrack(
                        index,
                        "synthetic-$index",
                        "Track $index",
                        "Artist",
                        0,
                        MediaItem.Builder().setMediaId("synthetic-$index").build(),
                    )
                }
            }
            PlaybackQueueLayer(
                PlaybackQueueSnapshot(upcoming = tracks),
                onItemClick = {},
                onFavoriteCurrentClick = {},
                onCurrentRatingChanged = { _, _ -> },
                onClearUpcomingClick = {},
                onMoveRequest = { from, to -> moves += from to to },
                modifier = Modifier.width(360.dp).height(500.dp).testTag("queue"),
            )
        }
        val queue = compose.onNodeWithTag("queue")
        fun rowY(title: String): Float =
            compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.center.y -
                queue.fetchSemanticsNode().boundsInRoot.top
        val first = rowY("Track 3")
        val second = rowY("Track 4")
        queue.performTouchInput {
            down(Offset(width - 2f, first))
            moveTo(Offset(width - 2f, second))
            cancel()
        }
        compose.runOnIdle { assertTrue(moves.isEmpty()) }
        queue.performTouchInput {
            down(Offset(width - 2f, first))
            moveTo(Offset(width - 2f, second))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(3 to 4), moves) }
    }
}
