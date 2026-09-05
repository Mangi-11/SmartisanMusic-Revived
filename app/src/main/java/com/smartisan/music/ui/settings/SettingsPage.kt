package com.smartisan.music.ui.settings

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.smartisan.music.R
import com.smartisan.music.data.settings.*
import com.smartisan.music.launcher.AppIconManager
import com.smartisan.music.ui.shell.PageStackTransition
import com.smartisan.music.ui.shell.PredictiveBackHandler
import com.smartisan.music.ui.shell.rememberPredictiveBackState

@Composable
internal fun SettingsPage(
    active: Boolean,
    playbackSettings: PlaybackSettings,
    artistSettings: ArtistSettings,
    navigationSettings: NavigationSettings,
    themeMode: ThemeMode,
    onClose: () -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    onArtistSeparatorsChange: (Set<String>) -> Unit,
    onTabPinnedChange: (String, Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appIconManager = remember(context) { AppIconManager(context) }
    var appIcon by remember(appIconManager) { mutableStateOf(appIconManager.currentIcon()) }
    var editingArtistSeparators by remember { mutableStateOf(false) }
    var artistSeparatorsInitialValues by remember { mutableStateOf(emptySet<String>()) }
    var secondaryPage by rememberSaveable { mutableStateOf<SettingsSecondaryPage?>(null) }
    val latestOnArtistSeparatorsChange by rememberUpdatedState(onArtistSeparatorsChange)

    val settingsPredictiveBackState = rememberPredictiveBackState()

    PredictiveBackHandler(
        enabled = active && secondaryPage != null,
        state = settingsPredictiveBackState,
    ) {
        secondaryPage = null
    }
    BackHandler(enabled = active && secondaryPage == null) {
        onClose()
    }

    PageStackTransition(
        secondaryKey = secondaryPage,
        modifier = modifier.fillMaxSize().background(colorResource(R.color.page_background)),
        label = "settings page stack",
        predictiveBackProgress = settingsPredictiveBackState.progress,
        predictiveBackExitConsumed = settingsPredictiveBackState.exitConsumed,
        onPredictiveBackExitConsumedReset = settingsPredictiveBackState::reset,
        primaryContent = {
            SettingsRootPage(
                active = active,
                playbackSettings = playbackSettings,
                artistSettings = artistSettings,
                navigationSettings = navigationSettings,
                themeMode = themeMode,
                appIcon = appIcon,
                onClose = onClose,
                onScratchEnabledChange = onScratchEnabledChange,
                onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                onAudioFxClick = {
                    secondaryPage = SettingsSecondaryPage.AudioFx
                },
                onArtistSeparatorsClick = {
                    artistSeparatorsInitialValues = artistSettings.separators
                    editingArtistSeparators = true
                },
                onNavigationClick = {
                    secondaryPage = SettingsSecondaryPage.Navigation
                },
                onAppIconClick = {
                    secondaryPage = SettingsSecondaryPage.AppIcon
                },
                onThemeClick = {
                    secondaryPage = SettingsSecondaryPage.Theme
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
        secondaryContent = { page ->
            when (page) {
                SettingsSecondaryPage.AudioFx ->
                    AudioFxSettingsPage(
                        active = active,
                        playbackSettings = playbackSettings,
                        onClose = {
                            secondaryPage = null
                        },
                        onAudioFxEnabledChange = onAudioFxEnabledChange,
                        onAudioFxPresetChange = onAudioFxPresetChange,
                        onAudioFxCustomGainDbPointsChange = onAudioFxCustomGainDbPointsChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                SettingsSecondaryPage.Navigation ->
                    NavigationSettingsPage(
                        active = active,
                        navigationSettings = navigationSettings,
                        onClose = {
                            secondaryPage = null
                        },
                        onTabPinnedChange = onTabPinnedChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                SettingsSecondaryPage.Theme ->
                    ThemeSettingsPage(
                        active = active,
                        themeMode = themeMode,
                        onClose = {
                            secondaryPage = null
                        },
                        onThemeModeChange = onThemeModeChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                SettingsSecondaryPage.AppIcon ->
                    AppIconSettingsPage(
                        active = active,
                        selectedIcon = appIcon,
                        onClose = {
                            secondaryPage = null
                        },
                        onIconSelected = { selectedIcon ->
                            appIconManager
                                .setIcon(selectedIcon)
                                .onSuccess { appliedIcon ->
                                    appIcon = appliedIcon
                                }
                                .onFailure { error ->
                                    Log.e(
                                        "AppIconSettings",
                                        "Failed to change launcher icon",
                                        error,
                                    )
                                    Toast.makeText(
                                            context,
                                            R.string.app_icon_change_failed,
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        },
    )

    if (editingArtistSeparators) {
        ArtistSeparatorsDialog(
            initialSeparators = artistSeparatorsInitialValues,
            onDismiss = { editingArtistSeparators = false },
            onConfirm = { separators ->
                editingArtistSeparators = false
                latestOnArtistSeparatorsChange(separators)
            },
        )
    }
}

private enum class SettingsSecondaryPage {
    AudioFx,
    Navigation,
    Theme,
    AppIcon,
}
