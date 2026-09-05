package com.smartisan.music.ui.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.data.settings.AudioFxPreset
import com.smartisan.music.data.settings.NavigationSettings
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.data.settings.ThemeMode
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.settings.SettingsPage
import com.smartisan.music.ui.shell.PageStackAxis
import com.smartisan.music.ui.shell.PageStackSlideMillis
import com.smartisan.music.ui.shell.PageStackTransition
import com.smartisan.music.ui.shell.rememberPredictiveBackState
import kotlinx.coroutines.delay

/** `More` 只负责两个职责：列出当前没有固定到底栏的同级目的地，以及承载设置页。 内容目的地由主壳统一渲染，避免在这里复制一套页面栈和状态所有权。 */
@Composable
internal fun MorePage(
    active: Boolean,
    overflowDestinations: List<MusicDestination>,
    playbackSettings: PlaybackSettings,
    artistSettings: ArtistSettings,
    navigationSettings: NavigationSettings,
    themeMode: ThemeMode,
    onDestinationSelected: (MusicDestination) -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    onArtistSeparatorsChange: (Set<String>) -> Unit,
    onTabPinnedChange: (String, Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSettingsPageActiveChanged: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var settingsVisible by remember { mutableStateOf(false) }
    val settingsPredictiveBackState = rememberPredictiveBackState()

    LaunchedEffect(active, settingsVisible) {
        if (active && settingsVisible) {
            onSettingsPageActiveChanged(true)
        } else {
            if (!settingsVisible) {
                delay(PageStackSlideMillis.toLong())
            }
            onSettingsPageActiveChanged(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose { onSettingsPageActiveChanged(false) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PageStackTransition(
            secondaryKey = settingsVisible.takeIf { it },
            modifier = Modifier.fillMaxSize(),
            label = "more settings stack",
            axisForKey = { PageStackAxis.VerticalPush },
            predictiveBackProgress = settingsPredictiveBackState.progress,
            predictiveBackExitConsumed = settingsPredictiveBackState.exitConsumed,
            onPredictiveBackExitConsumedReset = settingsPredictiveBackState::reset,
            primaryContent = {
                SmartisanMoreRootPage(
                    active = active,
                    destinations = overflowDestinations,
                    onDestinationSelected = onDestinationSelected,
                    onSettingsClick = { settingsVisible = true },
                    onSearchClick = onSearchClick,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            secondaryContent = {
                SettingsPage(
                    active = active,
                    playbackSettings = playbackSettings,
                    artistSettings = artistSettings,
                    navigationSettings = navigationSettings,
                    themeMode = themeMode,
                    onClose = { settingsVisible = false },
                    onScratchEnabledChange = onScratchEnabledChange,
                    onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                    onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                    onAudioFxEnabledChange = onAudioFxEnabledChange,
                    onAudioFxPresetChange = onAudioFxPresetChange,
                    onAudioFxCustomGainDbPointsChange = onAudioFxCustomGainDbPointsChange,
                    onArtistSeparatorsChange = onArtistSeparatorsChange,
                    onTabPinnedChange = onTabPinnedChange,
                    onThemeModeChange = onThemeModeChange,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }
}
