package com.smartisan.music.ui.shell

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.smartisan.music.R
import com.smartisan.music.data.settings.ArtistSettings
import com.smartisan.music.data.settings.AudioFxMaxGainDb
import com.smartisan.music.data.settings.AudioFxMinGainDb
import com.smartisan.music.data.settings.AudioFxPreset
import com.smartisan.music.data.settings.NavigationSettings
import com.smartisan.music.data.settings.PlaybackSettings
import com.smartisan.music.data.settings.equalizerGainDbPoints
import com.smartisan.music.data.settings.normalizeAudioFxGainDbPoints
import com.smartisan.music.data.settings.parseArtistSeparatorInput
import com.smartisan.music.launcher.AppIcon
import com.smartisan.music.launcher.AppIconManager
import com.smartisan.music.ui.navigation.MusicDestination
import com.smartisan.music.ui.navigation.MaxBottomDestinationCount
import com.smartisan.music.ui.navigation.MinBottomDestinationCount
import com.smartisan.music.ui.shell.settings.LegacyAppIconSettingsPage
import com.smartisan.music.ui.shell.settings.labelRes
import com.smartisan.music.ui.shell.titlebar.LegacyPortSmartisanTitleBar
import com.smartisan.music.ui.widgets.legacy.ShadowDrawable
import com.smartisan.music.ui.widgets.legacy.SwitchEx
import com.smartisan.music.ui.widgets.legacy.TitleBar
import kotlin.math.roundToInt

@Composable
internal fun LegacyPortSettingsPage(
    active: Boolean,
    playbackSettings: PlaybackSettings,
    artistSettings: ArtistSettings,
    navigationSettings: NavigationSettings,
    onClose: () -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    onArtistSeparatorsChange: (Set<String>) -> Unit,
    onTabPinnedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appIconManager = remember(context) { AppIconManager(context) }
    var appIcon by remember(appIconManager) { mutableStateOf(appIconManager.currentIcon()) }
    var editingArtistSeparators by remember { mutableStateOf(false) }
    var artistSeparatorsInitialValues by remember { mutableStateOf(emptySet<String>()) }
    var secondaryPage by rememberSaveable { mutableStateOf<LegacySettingsSecondaryPage?>(null) }
    val latestOnArtistSeparatorsChange by rememberUpdatedState(onArtistSeparatorsChange)

    val settingsPredictiveBackState = rememberLegacyPortPredictiveBackState()

    LegacyPortPredictiveBackHandler(
        enabled = active && secondaryPage != null,
        state = settingsPredictiveBackState,
    ) {
        secondaryPage = null
    }
    BackHandler(enabled = active && secondaryPage == null) {
        onClose()
    }

    LegacyPortPageStackTransition(
        secondaryKey = secondaryPage,
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
        label = "legacy settings page stack",
        predictiveBackProgress = settingsPredictiveBackState.progress,
        predictiveBackExitConsumed = settingsPredictiveBackState.exitConsumed,
        onPredictiveBackExitConsumedReset = settingsPredictiveBackState::reset,
        primaryContent = {
            LegacySettingsRootPage(
                active = active,
                playbackSettings = playbackSettings,
                artistSettings = artistSettings,
                navigationSettings = navigationSettings,
                appIcon = appIcon,
                onClose = onClose,
                onScratchEnabledChange = onScratchEnabledChange,
                onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                onAudioFxClick = {
                    secondaryPage = LegacySettingsSecondaryPage.AudioFx
                },
                onArtistSeparatorsClick = {
                    artistSeparatorsInitialValues = artistSettings.separators
                    editingArtistSeparators = true
                },
                onNavigationClick = {
                    secondaryPage = LegacySettingsSecondaryPage.Navigation
                },
                onAppIconClick = {
                    secondaryPage = LegacySettingsSecondaryPage.AppIcon
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
        secondaryContent = { page ->
            when (page) {
                LegacySettingsSecondaryPage.AudioFx -> LegacyAudioFxSettingsPage(
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
                LegacySettingsSecondaryPage.Navigation -> LegacyNavigationSettingsPage(
                    active = active,
                    navigationSettings = navigationSettings,
                    onClose = {
                        secondaryPage = null
                    },
                    onTabPinnedChange = onTabPinnedChange,
                    modifier = Modifier.fillMaxSize(),
                )
                LegacySettingsSecondaryPage.AppIcon -> LegacyAppIconSettingsPage(
                    active = active,
                    selectedIcon = appIcon,
                    onClose = {
                        secondaryPage = null
                    },
                    onIconSelected = { selectedIcon ->
                        appIconManager.setIcon(selectedIcon)
                            .onSuccess { appliedIcon ->
                                appIcon = appliedIcon
                            }
                            .onFailure { error ->
                                Log.e("AppIconSettings", "Failed to change launcher icon", error)
                                Toast.makeText(
                                    context,
                                    R.string.app_icon_change_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )

    if (editingArtistSeparators) {
        DisposableEffect(artistSeparatorsInitialValues) {
            val dialog = LegacyArtistSeparatorsDialog(
                context = context,
                initialSeparators = artistSeparatorsInitialValues,
                onDismiss = {
                    editingArtistSeparators = false
                },
                onConfirm = { separators ->
                    editingArtistSeparators = false
                    latestOnArtistSeparatorsChange(separators)
                },
            )
            dialog.show()
            onDispose {
                dialog.dismissIfShowing()
            }
        }
    }
}

private fun TitleBar.setupLegacySettingsTitleBar(
    titleRes: Int,
    onClose: () -> Unit,
    closeAffordance: LegacySettingsCloseAffordance,
) {
    removeAllLeftViews()
    removeAllRightViews()
    setShadowVisible(false)
    setCenterText(titleRes)
    when (closeAffordance) {
        LegacySettingsCloseAffordance.Back -> {
            addLeftImageView(R.drawable.standard_icon_back_selector).apply {
                setOnClickListener {
                    onClose()
                }
            }
        }
        LegacySettingsCloseAffordance.Done -> {
            addRightImageView(R.drawable.standard_icon_complete_selector).apply {
                setOnClickListener {
                    onClose()
                }
            }
        }
    }
}

private enum class LegacySettingsCloseAffordance {
    Back,
    Done,
}

@Composable
private fun LegacySettingsRootPage(
    active: Boolean,
    playbackSettings: PlaybackSettings,
    artistSettings: ArtistSettings,
    navigationSettings: NavigationSettings,
    appIcon: AppIcon,
    onClose: () -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxClick: () -> Unit,
    onArtistSeparatorsClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onAppIconClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        LegacyPortSmartisanTitleBar(
            modifier = Modifier.fillMaxWidth(),
            showShadow = true,
        ) { titleBar ->
            titleBar.setupLegacySettingsTitleBar(
                titleRes = R.string.setting,
                onClose = onClose,
                closeAffordance = LegacySettingsCloseAffordance.Done,
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                LegacySettingsContentView(context)
            },
            update = { view ->
                view.visibility = if (active) View.VISIBLE else View.INVISIBLE
                view.bind(
                    settings = playbackSettings,
                    artistSettings = artistSettings,
                    navigationSettings = navigationSettings,
                    appIcon = appIcon,
                    onScratchEnabledChange = onScratchEnabledChange,
                    onHidePlayerAxisEnabledChange = onHidePlayerAxisEnabledChange,
                    onPopcornSoundEnabledChange = onPopcornSoundEnabledChange,
                    onAudioFxClick = onAudioFxClick,
                    onArtistSeparatorsClick = onArtistSeparatorsClick,
                    onNavigationClick = onNavigationClick,
                    onAppIconClick = onAppIconClick,
                )
            },
        )
    }
}

@Composable
private fun LegacyAudioFxSettingsPage(
    active: Boolean,
    playbackSettings: PlaybackSettings,
    onClose: () -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        LegacyPortSmartisanTitleBar(
            modifier = Modifier.fillMaxWidth(),
            showShadow = true,
        ) { titleBar ->
            titleBar.setupLegacySettingsTitleBar(
                titleRes = R.string.audio_fx,
                onClose = onClose,
                closeAffordance = LegacySettingsCloseAffordance.Back,
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                LegacyAudioFxContentView(context)
            },
            update = { view ->
                view.visibility = if (active) View.VISIBLE else View.INVISIBLE
                view.bind(
                    settings = playbackSettings,
                    onAudioFxEnabledChange = onAudioFxEnabledChange,
                    onAudioFxPresetChange = onAudioFxPresetChange,
                    onAudioFxCustomGainDbPointsChange = onAudioFxCustomGainDbPointsChange,
                )
            },
        )
    }
}

@Composable
private fun LegacyNavigationSettingsPage(
    active: Boolean,
    navigationSettings: NavigationSettings,
    onClose: () -> Unit,
    onTabPinnedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.White),
    ) {
        LegacyPortSmartisanTitleBar(
            modifier = Modifier.fillMaxWidth(),
            showShadow = true,
        ) { titleBar ->
            titleBar.setupLegacySettingsTitleBar(
                titleRes = R.string.bottom_tab_visibility,
                onClose = onClose,
                closeAffordance = LegacySettingsCloseAffordance.Back,
            )
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                LegacyNavigationContentView(context)
            },
            update = { view ->
                view.visibility = if (active) View.VISIBLE else View.INVISIBLE
                view.bind(
                    navigationSettings = navigationSettings,
                    onTabPinnedChange = onTabPinnedChange,
                )
            },
        )
    }
}

private enum class LegacySettingsRowShape(
    val backgroundRes: Int,
    val shadowRes: Int,
) {
    Single(R.drawable.group_list_item_bg_single, R.drawable.list_content_item_single_shadow),
    Top(R.drawable.group_list_item_bg_top, R.drawable.list_content_item_top_shadow),
    Middle(R.drawable.group_list_item_bg_mid, R.drawable.list_content_item_middle_shadow),
    Bottom(R.drawable.group_list_item_bg_bottom, R.drawable.list_content_item_bottom_shadow),
}

private enum class LegacySettingsSecondaryPage {
    AudioFx,
    Navigation,
    AppIcon,
}

private val AudioFxFrequencyLabels = listOf("60", "230", "910", "4k", "14k")

private fun PlaybackSettings.activeAudioFxPreset(): AudioFxPreset {
    return if (audioFxEnabled) audioFxPreset else AudioFxPreset.Original
}

private fun PlaybackSettings.audioFxSummary(context: Context): String {
    return if (audioFxEnabled) {
        context.getString(audioFxPreset.labelRes())
    } else {
        context.getString(R.string.audio_fx_off)
    }
}

private fun AudioFxPreset.labelRes(): Int {
    return when (this) {
        AudioFxPreset.Original -> R.string.audio_fx_original
        AudioFxPreset.Bass -> R.string.audio_fx_bass
        AudioFxPreset.Clear -> R.string.audio_fx_clear
        AudioFxPreset.Vocal -> R.string.audio_fx_vocal
        AudioFxPreset.Rock -> R.string.audio_fx_rock
        AudioFxPreset.Custom -> R.string.audio_fx_custom
    }
}

private fun AudioFxPreset.summaryRes(): Int {
    return when (this) {
        AudioFxPreset.Original -> R.string.audio_fx_original_summary
        AudioFxPreset.Bass -> R.string.audio_fx_bass_summary
        AudioFxPreset.Clear -> R.string.audio_fx_clear_summary
        AudioFxPreset.Vocal -> R.string.audio_fx_vocal_summary
        AudioFxPreset.Rock -> R.string.audio_fx_rock_summary
        AudioFxPreset.Custom -> R.string.audio_fx_custom_summary
    }
}

private fun NavigationSettings.toNavigationSummary(context: Context): String {
    val pinnedSummary = context.resources.getQuantityString(
        R.plurals.bottom_tab_pinned_count,
        layout.bottomCount,
        layout.bottomCount,
    )
    val overflowSummary = context.resources.getQuantityString(
        R.plurals.bottom_tab_more_count,
        layout.overflowDestinations.size,
        layout.overflowDestinations.size,
    )
    return context.getString(
        R.string.bottom_tab_layout_summary,
        pinnedSummary,
        overflowSummary,
    )
}

private fun View.applyLegacySettingsBackground(shape: LegacySettingsRowShape) {
    val target = requireNotNull(context.getDrawable(shape.backgroundRes)).mutate()
    val shadow = requireNotNull(context.getDrawable(shape.shadowRes)).mutate()
    val shadowPadding = Rect()
    shadow.getPadding(shadowPadding)
    background = ShadowDrawable(
        shadow = shadow,
        target = target,
        insetLeftRight = shadowPadding.left,
        insetTopBottom = shadowPadding.top,
    )
}

private fun Context.dpFloat(value: Float): Float {
    return value * resources.displayMetrics.density
}

private fun Context.spFloat(value: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}

private class LegacySettingsContentView(context: Context) : ScrollView(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private val scratchRow = LegacySettingsSwitchRow(context, R.string.djing)
    private val axisRow = LegacySettingsSwitchRow(context, R.string.player_axis_enabled)
    private val popcornRow = LegacySettingsSwitchRow(context, R.string.popcorn_sound)
    private val audioFxRow = LegacySettingsValueRow(
        context = context,
        titleRes = R.string.audio_fx,
        showArrow = true,
    )
    private val artistSeparatorsRow = LegacySettingsValueRow(context, R.string.artist_separators)
    private val bottomTabRow = LegacySettingsValueRow(
        context = context,
        titleRes = R.string.bottom_tab_visibility,
        showArrow = true,
    )
    private val appIconRow = LegacySettingsValueRow(
        context = context,
        titleRes = R.string.app_icon,
        showArrow = true,
    )

    init {
        setBackgroundResource(R.drawable.account_background)
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_ALWAYS
        clipChildren = false
        clipToPadding = false
        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(gapView(context))
        content.addView(sectionTitleView(context, R.string.settings_section_playback))
        content.addView(
            settingsGroup(
                context,
                audioFxRow to LegacySettingsRowShape.Top,
                scratchRow to LegacySettingsRowShape.Middle,
                axisRow to LegacySettingsRowShape.Middle,
                popcornRow to LegacySettingsRowShape.Bottom,
            ),
        )
        content.addView(gapView(context))
        content.addView(sectionTitleView(context, R.string.settings_section_library))
        content.addView(
            settingsGroup(
                context,
                artistSeparatorsRow to LegacySettingsRowShape.Single,
            ),
        )
        content.addView(gapView(context))
        content.addView(sectionTitleView(context, R.string.settings_section_navigation))
        content.addView(
            settingsGroup(
                context,
                bottomTabRow to LegacySettingsRowShape.Single,
            ),
        )
        content.addView(gapView(context))
        content.addView(sectionTitleView(context, R.string.settings_section_appearance))
        content.addView(
            settingsGroup(
                context,
                appIconRow to LegacySettingsRowShape.Single,
            ),
        )
        content.addView(gapView(context))
    }

    fun bind(
        settings: PlaybackSettings,
        artistSettings: ArtistSettings,
        navigationSettings: NavigationSettings,
        appIcon: AppIcon,
        onScratchEnabledChange: (Boolean) -> Unit,
        onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
        onPopcornSoundEnabledChange: (Boolean) -> Unit,
        onAudioFxClick: () -> Unit,
        onArtistSeparatorsClick: () -> Unit,
        onNavigationClick: () -> Unit,
        onAppIconClick: () -> Unit,
    ) {
        scratchRow.bind(settings.scratchEnabled, onScratchEnabledChange)
        axisRow.bind(settings.hidePlayerAxisEnabled, onHidePlayerAxisEnabledChange)
        popcornRow.bind(settings.popcornSoundEnabled, onPopcornSoundEnabledChange)
        audioFxRow.bind(
            value = settings.audioFxSummary(context),
            onClick = onAudioFxClick,
        )
        artistSeparatorsRow.bind(
            value = artistSettings.separators.toSeparatorSummary(context),
            onClick = onArtistSeparatorsClick,
        )
        bottomTabRow.bind(
            value = navigationSettings.toNavigationSummary(context),
            onClick = onNavigationClick,
        )
        appIconRow.bind(
            value = context.getString(appIcon.labelRes()),
            onClick = onAppIconClick,
        )
    }

    private fun gapView(context: Context): View {
        return View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.list_item_vertical_gap),
            )
        }
    }

    private fun sectionTitleView(context: Context, textRes: Int): TextView {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return TextView(context).apply {
            setText(textRes)
            setTextColor(context.getColor(R.color.setting_item_summary_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(context.dpPx(18), context.dpPx(0), context.dpPx(18), context.dpPx(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = margin
                rightMargin = margin
            }
        }
    }

    private fun settingsGroup(
        context: Context,
        vararg rows: Pair<View, LegacySettingsRowShape>,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            rows.forEach { (row, shape) ->
                row.setLegacySettingsBackground(shape)
                addView(row, rowLayoutParams(context))
            }
        }
    }

    private fun rowLayoutParams(context: Context): LinearLayout.LayoutParams {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.resources.getDimensionPixelSize(R.dimen.list_item_min_height),
        ).apply {
            leftMargin = margin
            rightMargin = margin
        }
    }

    private fun View.setLegacySettingsBackground(shape: LegacySettingsRowShape) {
        val target = requireNotNull(context.getDrawable(shape.backgroundRes)).mutate()
        val shadow = requireNotNull(context.getDrawable(shape.shadowRes)).mutate()
        val shadowPadding = Rect()
        shadow.getPadding(shadowPadding)
        background = ShadowDrawable(
            shadow = shadow,
            target = target,
            insetLeftRight = shadowPadding.left,
            insetTopBottom = shadowPadding.top,
        )
    }
}

/**
 * 开关表示“固定到底栏”；关闭后页面仍可从「更多」进入，不会从应用中消失。
 * 底栏保留 2–4 个内容目的地，再加一个不可移动的「更多」。
 */
private class LegacyNavigationContentView(context: Context) : ScrollView(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private data class TabRow(val destination: MusicDestination, val row: LegacySettingsSwitchRow)

    private val movableRows = MusicDestination.movableEntries.map { destination ->
        TabRow(destination, LegacySettingsSwitchRow(context, destination.labelRes))
    }
    private val moreRow = LegacySettingsSwitchRow(
        context = context,
        titleRes = MusicDestination.More.labelRes,
        lockedSummaryRes = R.string.bottom_tab_more_locked,
    )

    init {
        setBackgroundResource(R.drawable.account_background)
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_ALWAYS
        clipChildren = false
        clipToPadding = false
        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(gapView(context))
        content.addView(sectionTitleView(context, R.string.bottom_tab_visibility))

        val allRows = (movableRows.map { it.row } + moreRow)
        content.addView(
            settingsGroup(
                context,
                *allRows.mapIndexed { index, row ->
                    row to when (index) {
                        0 -> LegacySettingsRowShape.Top
                        allRows.lastIndex -> LegacySettingsRowShape.Bottom
                        else -> LegacySettingsRowShape.Middle
                    }
                }.toTypedArray(),
            ),
        )
        content.addView(gapView(context))
    }

    fun bind(
        navigationSettings: NavigationSettings,
        onTabPinnedChange: (route: String, pinned: Boolean) -> Unit,
    ) {
        val layout = navigationSettings.layout
        movableRows.forEach { (destination, row) ->
            val pinned = layout.isPinned(destination)
            val enabled = if (pinned) {
                layout.bottomCount > MinBottomDestinationCount
            } else {
                layout.bottomCount < MaxBottomDestinationCount
            }
            row.bind(
                checked = pinned,
                enabled = enabled,
            ) { checked ->
                onTabPinnedChange(destination.route, checked)
            }
        }
        moreRow.bindLocked(checked = true)
    }

    private fun gapView(context: Context): View {
        return View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.list_item_vertical_gap),
            )
        }
    }

    private fun sectionTitleView(context: Context, textRes: Int): TextView {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return TextView(context).apply {
            setText(textRes)
            setTextColor(context.getColor(R.color.setting_item_summary_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(context.dpPx(18), 0, context.dpPx(18), context.dpPx(7))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = margin
                rightMargin = margin
            }
        }
    }

    private fun settingsGroup(
        context: Context,
        vararg rows: Pair<View, LegacySettingsRowShape>,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            rows.forEach { (row, shape) ->
                row.applyLegacySettingsBackground(shape)
                addView(row, rowLayoutParams(context))
            }
        }
    }

    private fun rowLayoutParams(context: Context): LinearLayout.LayoutParams {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.resources.getDimensionPixelSize(R.dimen.list_item_min_height),
        ).apply {
            leftMargin = margin
            rightMargin = margin
        }
    }

    private fun View.applyLegacySettingsBackground(shape: LegacySettingsRowShape) {
        val target = requireNotNull(context.getDrawable(shape.backgroundRes)).mutate()
        val shadow = requireNotNull(context.getDrawable(shape.shadowRes)).mutate()
        val shadowPadding = Rect()
        shadow.getPadding(shadowPadding)
        background = ShadowDrawable(
            shadow = shadow,
            target = target,
            insetLeftRight = shadowPadding.left,
            insetTopBottom = shadowPadding.top,
        )
    }
}

private class LegacyAudioFxContentView(context: Context) : ScrollView(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private val enabledRow = LegacySettingsSwitchRow(context, R.string.audio_fx_enabled)
    private val previewPanel = LegacyAudioFxPreviewPanel(context)
    private val presetRows = AudioFxPreset.entries.map { preset ->
        preset to LegacyAudioFxPresetRow(context, preset)
    }

    init {
        setBackgroundResource(R.drawable.account_background)
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_ALWAYS
        clipChildren = false
        clipToPadding = false
        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(gapView(context))
        content.addView(
            settingsGroup(
                context,
                enabledRow to LegacySettingsRowShape.Single,
            ),
        )
        content.addView(gapView(context))
        content.addView(
            previewPanel,
            panelLayoutParams(context, context.dpPx(188)),
        )
        content.addView(gapView(context))
        content.addView(
            settingsGroup(
                context,
                *presetRows.mapIndexed { index, pair ->
                    pair.second to when (index) {
                        0 -> LegacySettingsRowShape.Top
                        presetRows.lastIndex -> LegacySettingsRowShape.Bottom
                        else -> LegacySettingsRowShape.Middle
                    }
                }.toTypedArray(),
            ),
        )
        content.addView(gapView(context))
    }

    fun bind(
        settings: PlaybackSettings,
        onAudioFxEnabledChange: (Boolean) -> Unit,
        onAudioFxPresetChange: (AudioFxPreset) -> Unit,
        onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    ) {
        val visiblePreset = settings.activeAudioFxPreset()
        enabledRow.bind(settings.audioFxEnabled, onAudioFxEnabledChange)
        previewPanel.bind(
            preset = visiblePreset,
            enabled = settings.audioFxEnabled,
            customGainDbPoints = settings.audioFxCustomGainDbPoints,
            onCustomGainDbPointsChange = onAudioFxCustomGainDbPointsChange,
        )
        presetRows.forEach { (preset, row) ->
            row.bind(
                selected = preset == visiblePreset,
                enabled = settings.audioFxEnabled,
                onClick = {
                    onAudioFxPresetChange(preset)
                },
            )
        }
    }

    private fun gapView(context: Context): View {
        return View(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.list_item_vertical_gap),
            )
        }
    }

    private fun settingsGroup(
        context: Context,
        vararg rows: Pair<View, LegacySettingsRowShape>,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            rows.forEach { (row, shape) ->
                row.applyLegacySettingsBackground(shape)
                addView(row, rowLayoutParams(context))
            }
        }
    }

    private fun rowLayoutParams(context: Context): LinearLayout.LayoutParams {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.resources.getDimensionPixelSize(R.dimen.list_item_min_height),
        ).apply {
            leftMargin = margin
            rightMargin = margin
        }
    }

    private fun panelLayoutParams(context: Context, height: Int): LinearLayout.LayoutParams {
        val margin = context.resources.getDimensionPixelSize(R.dimen.list_item_left_right_margin)
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height,
        ).apply {
            leftMargin = margin
            rightMargin = margin
        }
    }
}

private class LegacyAudioFxPreviewPanel(context: Context) : LinearLayout(context) {
    private val titleView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setText(R.string.audio_fx_curve)
        setTextColor(context.getColorStateList(R.color.setting_item_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.primary_text_size))
    }
    private val valueView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        setTextColor(context.getColorStateList(R.color.setting_item_summary_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
    }
    private val curveView = LegacyAudioFxCurveView(context)

    init {
        orientation = VERTICAL
        setPadding(context.dpPx(18), context.dpPx(10), context.dpPx(18), context.dpPx(12))
        applyLegacySettingsBackground(LegacySettingsRowShape.Single)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            titleView,
            LinearLayout.LayoutParams(0, context.dpPx(34), 1f),
        )
        header.addView(
            valueView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dpPx(34)),
        )
        addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dpPx(34)))
        addView(curveView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun bind(
        preset: AudioFxPreset,
        enabled: Boolean,
        customGainDbPoints: List<Float>,
        onCustomGainDbPointsChange: (List<Float>) -> Unit,
    ) {
        titleView.alpha = if (enabled) 1f else 0.72f
        valueView.text = context.getString(preset.labelRes())
        valueView.alpha = if (enabled) 1f else 0.72f
        curveView.bind(
            preset = preset,
            enabled = enabled,
            customGainDbPoints = customGainDbPoints,
            onCustomGainDbPointsChange = onCustomGainDbPointsChange,
        )
    }
}

private class LegacyAudioFxPresetRow(
    context: Context,
    private val preset: AudioFxPreset,
) : RelativeLayout(context) {
    private val titleView = TextView(context).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
        setText(preset.labelRes())
        setTextColor(context.getColorStateList(R.color.setting_item_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.primary_text_size))
        setDuplicateParentStateEnabled(true)
    }
    private val summaryView = TextView(context).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
        setText(preset.summaryRes())
        setTextColor(context.getColorStateList(R.color.setting_item_summary_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
        setDuplicateParentStateEnabled(true)
    }
    private val selectedView = ImageView(context).apply {
        id = View.generateViewId()
        setImageResource(R.drawable.selector_radio_choice)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    init {
        isClickable = true
        isFocusable = true
        addView(
            selectedView,
            LayoutParams(context.dpPx(28), context.dpPx(28)).apply {
                addRule(ALIGN_PARENT_RIGHT)
                addRule(CENTER_VERTICAL)
                rightMargin = context.dpPx(14)
            },
        )
        val textColumn = LinearLayout(context).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                titleView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                summaryView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        addView(
            textColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                addRule(CENTER_VERTICAL)
                addRule(LEFT_OF, selectedView.id)
                leftMargin = context.dpPx(18)
                rightMargin = context.dpPx(10)
            },
        )
    }

    fun bind(
        selected: Boolean,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        val contentAlpha = if (enabled) 1f else 0.62f
        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
        titleView.alpha = contentAlpha
        summaryView.alpha = contentAlpha
        selectedView.alpha = contentAlpha
        isEnabled = enabled
        setOnClickListener {
            if (isEnabled) {
                onClick()
            }
        }
    }
}

private class LegacyAudioFxCurveView(context: Context) : View(context) {
    private var preset: AudioFxPreset = AudioFxPreset.Original
    private var audioFxEnabled = false
    private var customGainDbPoints = normalizeAudioFxGainDbPoints(emptyList())
    private var onCustomGainDbPointsChange: ((List<Float>) -> Unit)? = null
    private var activeBandIndex = -1
    private val graphBounds = RectF()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xe2, 0xe2, 0xe2)
        strokeWidth = context.dpFloat(1f)
        style = Paint.Style.STROKE
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xd8, 0xd8, 0xd8)
        strokeWidth = context.dpFloat(1.2f)
        style = Paint.Style.STROKE
    }
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xdb, 0x3b, 0x3b)
        strokeWidth = context.dpFloat(2.1f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val disabledCurvePaint = Paint(curvePaint).apply {
        color = Color.rgb(0xa6, 0xa6, 0xa6)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xdb, 0x3b, 0x3b)
        style = Paint.Style.FILL
    }
    private val disabledHandlePaint = Paint(handlePaint).apply {
        color = Color.rgb(0xa6, 0xa6, 0xa6)
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = context.dpFloat(1.4f)
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.setting_item_summary_text_color)
        textSize = context.spFloat(10.5f)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()

    fun bind(
        preset: AudioFxPreset,
        enabled: Boolean,
        customGainDbPoints: List<Float>,
        onCustomGainDbPointsChange: (List<Float>) -> Unit,
    ) {
        val normalizedCustomGains = normalizeAudioFxGainDbPoints(customGainDbPoints)
        this.onCustomGainDbPointsChange = onCustomGainDbPointsChange
        val changed = this.preset != preset ||
            audioFxEnabled != enabled ||
            this.customGainDbPoints != normalizedCustomGains
        isClickable = enabled && preset == AudioFxPreset.Custom
        if (changed) {
            this.preset = preset
            audioFxEnabled = enabled
            this.customGainDbPoints = normalizedCustomGains
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!audioFxEnabled || preset != AudioFxPreset.Custom || !updateGraphBounds()) {
            return super.onTouchEvent(event)
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isTouchInsideGraph(event)) {
                    false
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    activeBandIndex = nearestBandIndex(event.x)
                    updateCustomGainFromTouch(event.y)
                    true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                updateCustomGainFromTouch(event.y)
                true
            }
            MotionEvent.ACTION_UP -> {
                updateCustomGainFromTouch(event.y)
                parent?.requestDisallowInterceptTouchEvent(false)
                activeBandIndex = -1
                performClick()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                activeBandIndex = -1
                true
            }
            else -> true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!updateGraphBounds()) {
            return
        }
        val left = graphBounds.left
        val top = graphBounds.top
        val right = graphBounds.right
        val bottom = graphBounds.bottom

        val verticalCount = AudioFxFrequencyLabels.size
        for (index in 0 until verticalCount) {
            val x = left + (right - left) * index / (verticalCount - 1)
            canvas.drawLine(x, top, x, bottom, gridPaint)
            canvas.drawText(AudioFxFrequencyLabels[index], x, height - context.dpFloat(7f), labelPaint)
        }
        for (index in 0..4) {
            val y = top + (bottom - top) * index / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        val centerY = top + (bottom - top) / 2f
        canvas.drawLine(left, centerY, right, centerY, centerPaint)

        val values = currentGainDbPoints()
        path.reset()
        values.forEachIndexed { index, value ->
            val x = left + (right - left) * index / (values.size - 1)
            val y = gainToY(value)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, if (audioFxEnabled) curvePaint else disabledCurvePaint)

        if (preset == AudioFxPreset.Custom) {
            val radius = handleRadius()
            values.forEachIndexed { index, value ->
                val x = left + (right - left) * index / (values.size - 1)
                val y = gainToY(value)
                canvas.drawCircle(x, y, radius, if (audioFxEnabled) handlePaint else disabledHandlePaint)
                canvas.drawCircle(x, y, radius, handleStrokePaint)
            }
        }
    }

    private fun currentGainDbPoints(): FloatArray {
        return if (preset == AudioFxPreset.Custom) {
            normalizeAudioFxGainDbPoints(customGainDbPoints).toFloatArray()
        } else {
            preset.equalizerGainDbPoints()
        }
    }

    private fun updateGraphBounds(): Boolean {
        val horizontalInset = horizontalGraphInset()
        graphBounds.set(
            paddingLeft + horizontalInset,
            paddingTop + context.dpFloat(4f),
            width - paddingRight - horizontalInset,
            height - paddingBottom - context.dpFloat(24f),
        )
        return graphBounds.right > graphBounds.left && graphBounds.bottom > graphBounds.top
    }

    private fun horizontalGraphInset(): Float {
        val labelInset = AudioFxFrequencyLabels.maxOf { label ->
            labelPaint.measureText(label)
        } / 2f
        return maxOf(labelInset, handleRadius()) + context.dpFloat(2f)
    }

    private fun handleRadius(): Float {
        return context.dpFloat(5.6f)
    }

    private fun isTouchInsideGraph(event: MotionEvent): Boolean {
        val slop = context.dpFloat(22f)
        return event.x >= graphBounds.left - slop &&
            event.x <= graphBounds.right + slop &&
            event.y >= graphBounds.top - slop &&
            event.y <= graphBounds.bottom + slop
    }

    private fun nearestBandIndex(x: Float): Int {
        val gains = currentGainDbPoints()
        if (gains.isEmpty()) {
            return 0
        }
        var nearestIndex = 0
        var nearestDistance = Float.MAX_VALUE
        gains.indices.forEach { index ->
            val bandX = graphBounds.left + graphBounds.width() * index / (gains.size - 1)
            val distance = kotlin.math.abs(x - bandX)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        return nearestIndex
    }

    private fun updateCustomGainFromTouch(y: Float) {
        val index = activeBandIndex
        if (index !in 0 until customGainDbPoints.size) {
            return
        }
        val nextGain = yToGain(y)
        if (customGainDbPoints[index] == nextGain) {
            return
        }
        customGainDbPoints = customGainDbPoints.toMutableList().also { gains ->
            gains[index] = nextGain
        }
        invalidate()
        onCustomGainDbPointsChange?.invoke(customGainDbPoints)
    }

    private fun gainToY(gain: Float): Float {
        val centerY = graphBounds.centerY()
        val gainRange = AudioFxMaxGainDb.coerceAtLeast(1f)
        return centerY - gain.coerceIn(AudioFxMinGainDb, AudioFxMaxGainDb) / gainRange * (graphBounds.height() / 2f)
    }

    private fun yToGain(y: Float): Float {
        val centerY = graphBounds.centerY()
        val gainRange = AudioFxMaxGainDb.coerceAtLeast(1f)
        val rawGain = (centerY - y) / (graphBounds.height() / 2f) * gainRange
        return ((rawGain.coerceIn(AudioFxMinGainDb, AudioFxMaxGainDb) * 2f).roundToInt() / 2f)
    }
}

private class LegacySettingsValueRow(
    context: Context,
    titleRes: Int,
    private val showArrow: Boolean = false,
) : RelativeLayout(context) {
    private val titleView = TextView(context).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
        setText(titleRes)
        setTextColor(context.getColorStateList(R.color.setting_item_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.primary_text_size))
        setDuplicateParentStateEnabled(true)
    }
    private val valueView = TextView(context).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        setSingleLine(true)
        setTextColor(context.getColorStateList(R.color.blue_btn_text_color_selector))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
        setDuplicateParentStateEnabled(true)
    }
    private val arrowView = ImageView(context).apply {
        id = View.generateViewId()
        setBackgroundResource(R.drawable.selector_list_content_item_arrow)
        setDuplicateParentStateEnabled(true)
        visibility = if (showArrow) VISIBLE else GONE
    }

    init {
        val contentMarginStart = resources.getDimensionPixelSize(R.dimen.settings_row_content_margin_start)
        val titleAccessoryGap = resources.getDimensionPixelSize(R.dimen.settings_row_title_accessory_gap)
        val accessoryMarginEnd = resources.getDimensionPixelSize(R.dimen.settings_row_accessory_margin_end)
        val valueArrowGap = resources.getDimensionPixelSize(R.dimen.settings_row_value_arrow_gap)
        setBackgroundResource(R.drawable.group_list_item_bg_single)
        isClickable = true
        isFocusable = true
        addView(
            arrowView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(ALIGN_PARENT_RIGHT)
                addRule(CENTER_VERTICAL)
                rightMargin = accessoryMarginEnd
            },
        )
        addView(
            valueView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                if (showArrow) {
                    addRule(LEFT_OF, arrowView.id)
                } else {
                    addRule(ALIGN_PARENT_RIGHT)
                }
                addRule(CENTER_VERTICAL)
                rightMargin = if (showArrow) valueArrowGap else contentMarginStart
            },
        )
        addView(
            titleView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(CENTER_VERTICAL)
                addRule(LEFT_OF, valueView.id)
                leftMargin = contentMarginStart
                rightMargin = titleAccessoryGap
            },
        )
    }

    fun bind(
        value: String,
        onClick: () -> Unit,
    ) {
        valueView.text = value
        setOnClickListener { onClick() }
    }

}

private class LegacySettingsSwitchRow(
    context: Context,
    titleRes: Int,
    private val lockedSummaryRes: Int = 0,
) : RelativeLayout(context) {
    private var binding = false
    private var onCheckedChange: ((Boolean) -> Unit)? = null
    private val titleView = TextView(context).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine(true)
        setText(titleRes)
        setTextColor(context.getColorStateList(R.color.setting_item_text_colorlist))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.primary_text_size))
        setDuplicateParentStateEnabled(true)
    }
    private val summaryView = TextView(context).apply {
        id = View.generateViewId()
        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        setSingleLine(true)
        setTextColor(context.getColor(R.color.setting_item_summary_text_color))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.settings_item_tips_text_size))
        visibility = if (lockedSummaryRes != 0) VISIBLE else GONE
        if (lockedSummaryRes != 0) {
            setText(lockedSummaryRes)
        }
        setDuplicateParentStateEnabled(true)
    }
    private val switchView = SwitchEx(context).apply {
        id = View.generateViewId()
        setDuplicateParentStateEnabled(true)
        setOnCheckedChangeListener { _, checked ->
            if (!binding) {
                onCheckedChange?.invoke(checked)
            }
        }
    }

    init {
        val contentMarginStart = resources.getDimensionPixelSize(R.dimen.settings_row_content_margin_start)
        val titleAccessoryGap = resources.getDimensionPixelSize(R.dimen.settings_row_title_accessory_gap)
        val accessoryMarginEnd = resources.getDimensionPixelSize(R.dimen.settings_row_accessory_margin_end)
        val valueArrowGap = resources.getDimensionPixelSize(R.dimen.settings_row_value_arrow_gap)
        setBackgroundResource(R.drawable.group_list_item_bg_single)
        isClickable = true
        isFocusable = true
        addView(
            switchView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(ALIGN_PARENT_RIGHT)
                addRule(CENTER_VERTICAL)
                rightMargin = accessoryMarginEnd
            },
        )
        addView(
            summaryView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(LEFT_OF, switchView.id)
                addRule(CENTER_VERTICAL)
                rightMargin = valueArrowGap
            },
        )
        addView(
            titleView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ).apply {
                addRule(CENTER_VERTICAL)
                // 有摘要（锁定行）时标题在摘要左侧结束；普通开关行标题直接在开关左侧结束，
                // 避免文字延伸到开关下方被遮挡。
                if (lockedSummaryRes != 0) {
                    addRule(LEFT_OF, summaryView.id)
                } else {
                    addRule(LEFT_OF, switchView.id)
                }
                leftMargin = contentMarginStart
                rightMargin = titleAccessoryGap
            },
        )
        setOnClickListener {
            switchView.performClick()
        }
    }

    fun bind(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        bind(
            checked = checked,
            enabled = true,
            onCheckedChange = onCheckedChange,
        )
    }

    fun bind(
        checked: Boolean,
        enabled: Boolean = true,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        this.onCheckedChange = onCheckedChange
        isEnabled = enabled
        isClickable = enabled
        isFocusable = enabled
        switchView.isEnabled = enabled
        titleView.alpha = if (enabled) 1f else 0.55f
        if (switchView.isChecked != checked) {
            binding = true
            switchView.isChecked = checked
            binding = false
        }
    }

    /**
     * 锁定该行：开关常亮不可切换，常用于不可隐藏的固定项（如「更多」tab）。
     */
    fun bindLocked(checked: Boolean) {
        onCheckedChange = null
        isClickable = false
        isFocusable = false
        switchView.isEnabled = false
        if (switchView.isChecked != checked) {
            binding = true
            switchView.isChecked = checked
            binding = false
        }
    }
}

private class LegacyArtistSeparatorsDialog(
    private val context: Context,
    initialSeparators: Set<String>,
    private val onDismiss: () -> Unit,
    private val onConfirm: (Set<String>) -> Unit,
) {
    private val dialog = Dialog(context, R.style.MmsDialogTheme)
    private val chipRow: LinearLayout
    private val input: EditText
    private var separators = initialSeparators.toCollection(linkedSetOf())

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.revone_global_dialog_shape_background)
        }
        dialog.requestWindowFeature(1)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            onDismiss()
        }

        root.addView(
            TextView(context).apply {
                text = context.getString(R.string.artist_separators)
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.status_bar_color_dialog))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.revone_dialog_button_height),
            ),
        )

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.revone_global_dialog_message_background)
            setPadding(context.dpPx(18), 0, context.dpPx(18), 0)
        }
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        content.addView(
            TextView(context).apply {
                text = context.getString(R.string.artist_separators_hint)
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.setting_item_summary_text_color))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dpPx(48)),
        )

        chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(
                    chipRow,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dpPx(42)),
        )

        val addRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(context).apply {
            setSingleLine(true)
            hint = context.getString(R.string.artist_custom_separator_hint)
            setTextColor(context.getColor(R.color.editor_text_color))
            setHintTextColor(context.getColor(R.color.editor_hint_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = null
            setPadding(0, 0, 0, 0)
        }
        val inputFrame = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.edit_text_bg)
            addView(
                input,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    leftMargin = context.dpPx(12)
                    topMargin = context.dpPx(6)
                    rightMargin = context.dpPx(12)
                    bottomMargin = context.dpPx(6)
                },
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.quick_icon_delete)
                    setOnClickListener {
                        input.text = null
                    }
                },
                LinearLayout.LayoutParams(context.dpPx(32), context.dpPx(32)),
            )
        }
        addRow.addView(
            FrameLayout(context).apply {
                addView(inputFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dpPx(40), Gravity.CENTER))
            },
            LinearLayout.LayoutParams(0, context.dpPx(44), 1f),
        )
        addRow.addView(
            inlineAddButton(
                text = context.getString(R.string.add),
            ).apply {
                setOnClickListener {
                    addSeparatorsFromInput()
                }
            },
            LinearLayout.LayoutParams(context.dpPx(64), context.dpPx(40)).apply {
                leftMargin = context.dpPx(8)
            },
        )
        content.addView(
            addRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dpPx(44)).apply {
                topMargin = context.dpPx(8)
                bottomMargin = context.dpPx(18)
            },
        )

        root.addView(
            dialogActionButton(context.getString(R.string.done)).apply {
                setOnClickListener {
                    addSeparatorsFromInput()
                    onConfirm(separators)
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.revone_dialog_button_height),
            ),
        )

        renderChips()
    }

    fun show() {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.54f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.revone_global_dialog_content_width), WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        input.postDelayed(
            {
                input.requestFocus()
                (dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(input, 0)
            },
            300L,
        )
    }

    fun dismissIfShowing() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    private fun addSeparatorsFromInput() {
        val addedSeparators = parseArtistSeparatorInput(input.text?.toString().orEmpty())
        if (addedSeparators.isNotEmpty()) {
            separators += addedSeparators
            input.text = null
            renderChips()
        }
    }

    private fun renderChips() {
        chipRow.removeAllViews()
        separators.sorted().forEach { separator ->
            chipRow.addView(
                separatorChip(separator),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    context.dpPx(36),
                ).apply {
                    rightMargin = context.dpPx(8)
                },
            )
        }
        if (separators.isEmpty()) {
            chipRow.addView(
                TextView(context).apply {
                    text = context.getString(R.string.not_set)
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(context.getColor(R.color.setting_item_summary_text_color))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun separatorChip(separator: String): TextView {
        return TextView(context).apply {
            text = "$separator  \u00D7"
            gravity = Gravity.CENTER
            setPadding(context.dpPx(16), 0, context.dpPx(14), 0)
            setTextColor(context.getColor(R.color.setting_item_summary_text_color))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.rgb(0xf7, 0xf8, 0xf9))
                setStroke(context.dpPx(1), Color.rgb(0xe2, 0xe2, 0xe2))
                cornerRadius = 5f * context.resources.displayMetrics.density
            }
            setOnClickListener {
                separators -= separator
                renderChips()
            }
        }
    }

    private fun inlineAddButton(text: String): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER
            this.text = text
            setTextColor(context.getColor(R.color.btn_text_color_blue))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(Color.rgb(0xfa, 0xfb, 0xfd))
                setStroke(context.dpPx(1), Color.rgb(0xd7, 0xdc, 0xe8))
                cornerRadius = 7f * context.resources.displayMetrics.density
            }
        }
    }

    private fun dialogActionButton(text: String): TextView {
        return TextView(context).apply {
            gravity = Gravity.CENTER
            this.text = text
            setTextColor(context.getColorStateList(R.color.blue_btn_text_color_selector))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.revone_dialog_button_bg_selector)
        }
    }
}

private fun Set<String>.toSeparatorSummary(context: Context): String {
    return if (isEmpty()) {
        context.getString(R.string.not_set)
    } else {
        sorted().joinToString(" ")
    }
}
