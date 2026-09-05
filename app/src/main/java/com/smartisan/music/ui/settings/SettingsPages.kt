package com.smartisan.music.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisan.music.R
import com.smartisan.music.data.settings.*
import com.smartisan.music.launcher.AppIcon
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.navigation.*
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun SettingsRootPage(
    active: Boolean,
    playbackSettings: PlaybackSettings,
    artistSettings: ArtistSettings,
    navigationSettings: NavigationSettings,
    themeMode: ThemeMode,
    appIcon: AppIcon,
    onClose: () -> Unit,
    onScratchEnabledChange: (Boolean) -> Unit,
    onHidePlayerAxisEnabledChange: (Boolean) -> Unit,
    onPopcornSoundEnabledChange: (Boolean) -> Unit,
    onAudioFxClick: () -> Unit,
    onArtistSeparatorsClick: () -> Unit,
    onNavigationClick: () -> Unit,
    onAppIconClick: () -> Unit,
    onThemeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(active, stringResource(R.string.setting), onClose, modifier, complete = true) {
        SettingsSection(R.string.settings_section_playback) {
            SettingsValueRow(
                R.string.audio_fx,
                if (playbackSettings.audioFxEnabled)
                    stringResource(playbackSettings.audioFxPreset.labelRes())
                else stringResource(R.string.audio_fx_off),
                true,
                RowShape.Top,
                onAudioFxClick,
            )
            SettingsSwitchRow(
                R.string.djing,
                playbackSettings.scratchEnabled,
                RowShape.Middle,
                onScratchEnabledChange,
            )
            SettingsSwitchRow(
                R.string.player_axis_enabled,
                playbackSettings.hidePlayerAxisEnabled,
                RowShape.Middle,
                onHidePlayerAxisEnabledChange,
            )
            SettingsSwitchRow(
                R.string.popcorn_sound,
                playbackSettings.popcornSoundEnabled,
                RowShape.Bottom,
                onPopcornSoundEnabledChange,
            )
        }
        SettingsSection(R.string.settings_section_library) {
            SettingsValueRow(
                R.string.artist_separators,
                artistSettings.separators.sorted().joinToString(" ").ifEmpty {
                    stringResource(R.string.not_set)
                },
                false,
                RowShape.Single,
                onArtistSeparatorsClick,
            )
        }
        SettingsSection(R.string.settings_section_navigation) {
            val pinned =
                pluralStringResource(
                    R.plurals.bottom_tab_pinned_count,
                    navigationSettings.layout.bottomCount,
                    navigationSettings.layout.bottomCount,
                )
            val more =
                pluralStringResource(
                    R.plurals.bottom_tab_more_count,
                    navigationSettings.layout.overflowDestinations.size,
                    navigationSettings.layout.overflowDestinations.size,
                )
            SettingsValueRow(
                R.string.bottom_tab_visibility,
                stringResource(R.string.bottom_tab_layout_summary, pinned, more),
                true,
                RowShape.Single,
                onNavigationClick,
            )
        }
        SettingsSection(R.string.settings_section_appearance) {
            SettingsValueRow(
                R.string.theme_settings,
                stringResource(themeMode.labelRes),
                true,
                RowShape.Top,
                onThemeClick,
            )
            SettingsValueRow(
                R.string.app_icon,
                stringResource(appIcon.labelRes()),
                true,
                RowShape.Bottom,
                onAppIconClick,
            )
        }
        SettingsGap()
    }
}

@Composable
internal fun NavigationSettingsPage(
    active: Boolean,
    navigationSettings: NavigationSettings,
    onClose: () -> Unit,
    onTabPinnedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(active, stringResource(R.string.bottom_tab_visibility), onClose, modifier) {
        SettingsSection(R.string.bottom_tab_visibility) {
            MusicDestination.movableEntries.forEachIndexed { index, destination ->
                val layout = navigationSettings.layout
                val pinned = layout.isPinned(destination)
                val enabled =
                    if (pinned) layout.bottomCount > MinBottomDestinationCount
                    else layout.bottomCount < MaxBottomDestinationCount
                SettingsSwitchRow(
                    destination.labelRes,
                    pinned,
                    if (index == 0) RowShape.Top else RowShape.Middle,
                    { onTabPinnedChange(destination.route, it) },
                    enabled = enabled,
                )
            }
            SettingsSwitchRow(
                MusicDestination.More.labelRes,
                true,
                RowShape.Bottom,
                {},
                enabled = false,
                lockedSummary = stringResource(R.string.bottom_tab_more_locked),
            )
        }
        SettingsGap()
    }
}

@Composable
internal fun AudioFxSettingsPage(
    active: Boolean,
    playbackSettings: PlaybackSettings,
    onClose: () -> Unit,
    onAudioFxEnabledChange: (Boolean) -> Unit,
    onAudioFxPresetChange: (AudioFxPreset) -> Unit,
    onAudioFxCustomGainDbPointsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preset =
        if (playbackSettings.audioFxEnabled) playbackSettings.audioFxPreset
        else AudioFxPreset.Original
    SettingsScaffold(active, stringResource(R.string.audio_fx), onClose, modifier) {
        SettingsGap()
        SettingsSwitchRow(
            R.string.audio_fx_enabled,
            playbackSettings.audioFxEnabled,
            RowShape.Single,
            onAudioFxEnabledChange,
        )
        SettingsGap()
        Column(
            Modifier.fillMaxWidth()
                .height(188.dp)
                .smartisanShadowBackground(RowShape.Single.background, RowShape.Single.shadow)
                .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .height(34.dp)
                    .alpha(if (playbackSettings.audioFxEnabled) 1f else 0.72f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    stringResource(R.string.audio_fx_curve),
                    Modifier.weight(1f),
                    style = settingsTextStyle(),
                )
                BasicText(
                    stringResource(preset.labelRes()),
                    style = settingsTextStyle(summary = true),
                )
            }
            AudioFxCurve(
                preset,
                playbackSettings.audioFxEnabled,
                playbackSettings.audioFxCustomGainDbPoints,
                onAudioFxCustomGainDbPointsChange,
                Modifier.fillMaxWidth().weight(1f),
            )
        }
        SettingsGap()
        Column(Modifier.selectableGroup()) {
            AudioFxPreset.entries.forEachIndexed { index, item ->
                val source = remember { MutableInteractionSource() }
                SettingsRow(
                    item.labelRes(),
                    stringResource(item.summaryRes()),
                    RowShape.at(index, AudioFxPreset.entries.size),
                    playbackSettings.audioFxEnabled,
                    source,
                    Modifier.selectable(
                        item == preset,
                        source,
                        null,
                        enabled = playbackSettings.audioFxEnabled,
                        role = Role.RadioButton,
                        onClick = smartisanClick { onAudioFxPresetChange(item) },
                    ),
                    titleAccessoryGap = 10.dp,
                ) {
                    Box(
                        Modifier.padding(end = 6.dp)
                            .size(28.dp)
                            .alpha(if (playbackSettings.audioFxEnabled) 1f else 0.62f)
                    ) {
                        if (item == preset)
                            Image(
                                rememberSmartisanDrawablePainter(R.drawable.selector_radio_choice),
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Inside,
                            )
                    }
                }
            }
        }
        SettingsGap()
    }
}

@Composable
private fun SettingsScaffold(
    active: Boolean,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier,
    complete: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize().background(colorResource(R.color.page_background))) {
        if (active) {
            val close =
                SmartisanTitleBarAction(
                    if (complete) R.drawable.standard_icon_complete_selector
                    else R.drawable.standard_icon_back_selector,
                    stringResource(if (complete) R.string.done else R.string.back),
                    onClose,
                )
            SmartisanTitleBar(
                title,
                navigationIcon = if (complete) null else close,
                action = if (complete) close else null,
            )
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .smartisanPainterBackground(
                        rememberSmartisanDrawablePainter(R.drawable.account_background)
                    )
                    .verticalScroll(scroll)
                    .padding(horizontal = dimensionResource(R.dimen.list_item_left_right_margin)),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsGap() {
    Spacer(Modifier.height(dimensionResource(R.dimen.list_item_vertical_gap)))
}

@Composable
private fun SettingsSection(title: Int, content: @Composable ColumnScope.() -> Unit) {
    SettingsGap()
    BasicText(
        stringResource(title),
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 7.dp),
        style = settingsTextStyle(summary = true).copy(fontWeight = FontWeight.Bold),
    )
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun SettingsValueRow(
    title: Int,
    value: String,
    arrow: Boolean,
    shape: RowShape,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    SettingsRow(
        title,
        null,
        shape,
        true,
        source,
        Modifier.clickable(source, null, onClick = smartisanClick(onClick)),
    ) {
        BasicText(
            value,
            Modifier.padding(end = if (arrow) 2.dp else 10.dp),
            style =
                settingsTextStyle(summary = true)
                    .copy(
                        color =
                            smartisanStateColor(
                                R.color.blue_btn_text_color_selector,
                                pressed = pressed,
                                focused = focused,
                            )
                    ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (arrow)
            Image(
                rememberSmartisanDrawablePainter(
                    R.drawable.selector_list_content_item_arrow,
                    pressed = pressed,
                    focused = focused,
                ),
                null,
                contentScale = ContentScale.Inside,
            )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: Int,
    checked: Boolean,
    shape: RowShape,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    lockedSummary: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val switchState = rememberSmartisanSwitchState(checked, enabled, onChange)
    SettingsRow(
        title,
        null,
        shape,
        enabled || lockedSummary != null,
        source,
        Modifier.toggleable(
            checked,
            source,
            null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { switchState.toggle() },
        ),
        titleAlpha = if (enabled || lockedSummary != null) 1f else 0.55f,
    ) {
        if (lockedSummary != null)
            BasicText(
                lockedSummary,
                Modifier.padding(end = 2.dp),
                style = settingsTextStyle(summary = true),
                maxLines = 1,
            )
        SmartisanSwitch(checked, onChange, Modifier.clearAndSetSemantics {}, enabled, switchState)
    }
}

@Composable
private fun SettingsRow(
    title: Int,
    summary: String?,
    shape: RowShape,
    enabled: Boolean,
    source: MutableInteractionSource,
    modifier: Modifier,
    titleAlpha: Float = if (enabled) 1f else 0.62f,
    titleAccessoryGap: Dp? = null,
    accessory: @Composable RowScope.() -> Unit,
) {
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val localeDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth()
                .height(dimensionResource(R.dimen.list_item_min_height))
                .smartisanShadowBackground(
                    shape.background,
                    shape.shadow,
                    enabled,
                    pressed,
                    focused = focused,
                )
                .then(modifier)
                .padding(
                    start = dimensionResource(R.dimen.settings_row_content_margin_start),
                    end = dimensionResource(R.dimen.settings_row_accessory_margin_end),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f)
                    .padding(
                        end =
                            titleAccessoryGap
                                ?: dimensionResource(R.dimen.settings_row_title_accessory_gap)
                    )
                    .alpha(titleAlpha)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides localeDirection) {
                    BasicText(
                        stringResource(title),
                        Modifier.fillMaxWidth(),
                        style =
                            settingsTextStyle()
                                .copy(
                                    color =
                                        smartisanStateColor(
                                            R.color.setting_item_text_colorlist,
                                            enabled,
                                            pressed,
                                            focused = focused,
                                        )
                                ),
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (summary != null)
                        BasicText(
                            summary,
                            Modifier.fillMaxWidth(),
                            style =
                                settingsTextStyle(summary = true)
                                    .copy(
                                        color =
                                            smartisanStateColor(
                                                R.color.setting_item_summary_text_colorlist,
                                                enabled,
                                                pressed,
                                                focused = focused,
                                            )
                                    ),
                            maxLines = 1,
                            softWrap = false,
                        )
                }
            }
            accessory()
        }
    }
}

@Composable
private fun settingsTextStyle(summary: Boolean = false) =
    TextStyle(
        fontSize =
            smartisanTextSize(
                if (summary) R.dimen.settings_item_tips_text_size else R.dimen.primary_text_size
            ),
        color =
            colorResource(
                if (summary) R.color.setting_item_summary_text_color
                else R.color.setting_item_text_color
            ),
        platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

private enum class RowShape(val background: Int, val shadow: Int) {
    Single(R.drawable.group_list_item_bg_single, R.drawable.list_content_item_single_shadow),
    Top(R.drawable.group_list_item_bg_top, R.drawable.list_content_item_top_shadow),
    Middle(R.drawable.group_list_item_bg_mid, R.drawable.list_content_item_middle_shadow),
    Bottom(R.drawable.group_list_item_bg_bottom, R.drawable.list_content_item_bottom_shadow);

    companion object {
        fun at(index: Int, count: Int) =
            when {
                count == 1 -> Single
                index == 0 -> Top
                index == count - 1 -> Bottom
                else -> Middle
            }
    }
}

private fun AudioFxPreset.labelRes() =
    when (this) {
        AudioFxPreset.Original -> R.string.audio_fx_original
        AudioFxPreset.Bass -> R.string.audio_fx_bass
        AudioFxPreset.Clear -> R.string.audio_fx_clear
        AudioFxPreset.Vocal -> R.string.audio_fx_vocal
        AudioFxPreset.Rock -> R.string.audio_fx_rock
        AudioFxPreset.Custom -> R.string.audio_fx_custom
    }

private fun AudioFxPreset.summaryRes() =
    when (this) {
        AudioFxPreset.Original -> R.string.audio_fx_original_summary
        AudioFxPreset.Bass -> R.string.audio_fx_bass_summary
        AudioFxPreset.Clear -> R.string.audio_fx_clear_summary
        AudioFxPreset.Vocal -> R.string.audio_fx_vocal_summary
        AudioFxPreset.Rock -> R.string.audio_fx_rock_summary
        AudioFxPreset.Custom -> R.string.audio_fx_custom_summary
    }

@Composable
private fun AudioFxCurve(
    preset: AudioFxPreset,
    enabled: Boolean,
    customGains: List<Float>,
    onChange: (List<Float>) -> Unit,
    modifier: Modifier,
) {
    val labels = listOf("60", "230", "910", "4k", "14k")
    val measurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(
            color = colorResource(R.color.setting_item_summary_text_color),
            fontSize = 10.5.sp,
        )
    val labelLayouts = labels.map { measurer.measure(it, labelStyle) }
    val gridColor = colorResource(R.color.input_border)
    val handleBorder = colorResource(R.color.surface_card)
    val curveColor = if (enabled) Color(0xffdb3b3b) else Color(0xffa6a6a6)
    var values by
        remember(preset, customGains) {
            mutableStateOf(
                if (preset == AudioFxPreset.Custom) normalizeAudioFxGainDbPoints(customGains)
                else preset.equalizerGainDbPoints().toList()
            )
        }
    val currentValues by rememberUpdatedState(values)
    val callback by rememberUpdatedState(onChange)
    val editable = enabled && preset == AudioFxPreset.Custom
    val maxLabelWidth = labelLayouts.maxOf { it.size.width }.toFloat()
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(editable, maxLabelWidth) {
                if (!editable) return@pointerInput
                val inset = maxOf(maxLabelWidth / 2f, 5.6.dp.toPx()) + 2.dp.toPx()
                val top = 4.dp.toPx()
                val bottom = size.height - 24.dp.toPx()
                val graphHeight = bottom - top
                if (graphHeight <= 0 || size.width <= inset * 2f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = 22.dp.toPx()
                    if (
                        down.position.x !in (inset - slop)..(size.width - inset + slop) ||
                            down.position.y !in (top - slop)..(bottom + slop)
                    )
                        return@awaitEachGesture
                    down.consume()
                    val band =
                        (((down.position.x - inset) / (size.width - 2f * inset)) * 4)
                            .roundToInt()
                            .coerceIn(0, 4)
                    fun update(y: Float) {
                        val raw = ((top + bottom) / 2f - y) / (graphHeight / 2f) * AudioFxMaxGainDb
                        val gain =
                            (raw.coerceIn(AudioFxMinGainDb, AudioFxMaxGainDb) * 2).roundToInt() / 2f
                        if (gain != currentValues[band]) {
                            values = currentValues.toMutableList().also { it[band] = gain }
                            callback(values)
                        }
                    }
                    update(down.position.y)
                    do {
                        val change =
                            awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        update(change.position.y)
                        change.consume()
                    } while (change.pressed)
                }
            }
        ) {
            val inset = maxOf(maxLabelWidth / 2f, 5.6.dp.toPx()) + 2.dp.toPx()
            val left = inset
            val right = size.width - inset
            val top = 4.dp.toPx()
            val bottom = size.height - 24.dp.toPx()
            if (right <= left || bottom <= top) return@Canvas
            labels.indices.forEach { index ->
                val x = left + (right - left) * index / 4
                drawLine(gridColor, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                val label = labelLayouts[index]
                drawText(
                    label,
                    topLeft =
                        Offset(
                            x - label.size.width / 2f,
                            size.height - 7.dp.toPx() - label.firstBaseline,
                        ),
                )
            }
            (0..4).forEach { i ->
                val y = top + (bottom - top) * i / 4
                drawLine(gridColor, Offset(left, y), Offset(right, y), 1.dp.toPx())
            }
            drawLine(
                Color(0xffd8d8d8),
                Offset(left, (top + bottom) / 2),
                Offset(right, (top + bottom) / 2),
                1.2.dp.toPx(),
            )
            val points = values.mapIndexed { index, gain ->
                Offset(
                    left + (right - left) * index / 4,
                    (top + bottom) / 2 -
                        gain.coerceIn(AudioFxMinGainDb, AudioFxMaxGainDb) / AudioFxMaxGainDb *
                            (bottom - top) / 2,
                )
            }
            val path =
                Path().apply {
                    points.forEachIndexed { i, point ->
                        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
            drawPath(
                path,
                curveColor,
                style =
                    Stroke(
                        2.1.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
            )
            if (preset == AudioFxPreset.Custom)
                points.forEach { point ->
                    drawCircle(curveColor, 5.6.dp.toPx(), point)
                    drawCircle(handleBorder, 5.6.dp.toPx(), point, style = Stroke(1.4.dp.toPx()))
                }
        }
        if (editable)
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { index, label ->
                    Spacer(
                        Modifier.weight(1f).fillMaxHeight().semantics {
                            contentDescription = "$label Hz"
                            progressBarRangeInfo =
                                ProgressBarRangeInfo(
                                    values[index],
                                    AudioFxMinGainDb..AudioFxMaxGainDb,
                                )
                            setProgress { value ->
                                val gain =
                                    (value.coerceIn(AudioFxMinGainDb, AudioFxMaxGainDb) * 2)
                                        .roundToInt() / 2f
                                values = values.toMutableList().also { it[index] = gain }
                                callback(values)
                                true
                            }
                        }
                    )
                }
            }
    }
}

@Composable
internal fun ArtistSeparatorsDialog(
    initialSeparators: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var separators by remember(initialSeparators) { mutableStateOf(initialSeparators) }
    var input by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun addInput(): Set<String> {
        val parsed = parseArtistSeparatorInput(input)
        if (parsed.isNotEmpty()) {
            separators = separators + parsed
            input = ""
        }
        return separators
    }
    SmartisanModal(
        onDismiss,
        Modifier.widthIn(max = dimensionResource(R.dimen.revone_global_dialog_content_width))
            .fillMaxWidth(),
    ) {
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            delay(300)
            focus.requestFocus()
            keyboard?.show()
        }
        Column(
            Modifier.fillMaxWidth()
                .smartisanPainterBackground(
                    rememberSmartisanDrawablePainter(
                        R.drawable.revone_global_dialog_shape_background
                    )
                )
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.revone_dialog_button_height)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    stringResource(R.string.artist_separators),
                    style =
                        TextStyle(
                            color = colorResource(R.color.status_bar_color_dialog),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                        ),
                )
            }
            Column(
                Modifier.fillMaxWidth()
                    .smartisanPainterBackground(
                        rememberSmartisanDrawablePainter(
                            R.drawable.revone_global_dialog_message_background
                        )
                    )
                    .padding(horizontal = 18.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        stringResource(R.string.artist_separators_hint),
                        style =
                            settingsTextStyle(summary = true)
                                .copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().height(42.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (separators.isEmpty())
                        BasicText(
                            stringResource(R.string.not_set),
                            style = settingsTextStyle(summary = true).copy(fontSize = 15.sp),
                        )
                    separators.sorted().forEach { separator ->
                        Box(
                            Modifier.padding(end = 8.dp)
                                .height(36.dp)
                                .background(
                                    colorResource(R.color.surface_raised),
                                    RoundedCornerShape(5.dp),
                                )
                                .border(
                                    1.dp,
                                    colorResource(R.color.input_border),
                                    RoundedCornerShape(5.dp),
                                )
                                .clickable { separators = separators - separator }
                                .padding(start = 16.dp, end = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                "$separator  ×",
                                style =
                                    settingsTextStyle(summary = true)
                                        .copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp).height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f)
                            .height(40.dp)
                            .smartisanPainterBackground(
                                rememberSmartisanDrawablePainter(R.drawable.edit_text_bg)
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            input,
                            { input = it },
                            Modifier.weight(1f)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .focusRequester(focus),
                            singleLine = true,
                            textStyle =
                                TextStyle(
                                    color = colorResource(R.color.editor_text_color),
                                    fontSize = 15.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                            cursorBrush = SolidColor(colorResource(R.color.editor_text_color)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addInput() }),
                            decorationBox = { field ->
                                Box {
                                    if (input.isEmpty())
                                        BasicText(
                                            stringResource(R.string.artist_custom_separator_hint),
                                            style =
                                                TextStyle(
                                                    color =
                                                        colorResource(
                                                            R.color.editor_hint_text_color
                                                        ),
                                                    fontSize = 15.sp,
                                                    platformStyle =
                                                        PlatformTextStyle(
                                                            includeFontPadding = true
                                                        ),
                                                ),
                                        )
                                    field()
                                }
                            },
                        )
                        Image(
                            rememberSmartisanDrawablePainter(R.drawable.quick_icon_delete),
                            stringResource(R.string.delete),
                            Modifier.size(32.dp).clickable { input = "" },
                            contentScale = ContentScale.Inside,
                        )
                    }
                    Box(
                        Modifier.padding(start = 8.dp)
                            .size(64.dp, 40.dp)
                            .background(
                                colorResource(R.color.surface_raised_soft),
                                RoundedCornerShape(7.dp),
                            )
                            .border(1.dp, Color(0xffd7dce8), RoundedCornerShape(7.dp))
                            .clickable { addInput() },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            stringResource(R.string.add),
                            style =
                                TextStyle(
                                    color = colorResource(R.color.btn_text_color_blue),
                                    fontSize = 14.sp,
                                ),
                        )
                    }
                }
            }
            SmartisanDialogButton(
                stringResource(R.string.done),
                { onConfirm(addInput()) },
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.revone_dialog_button_height)),
                backgroundRes = R.drawable.revone_dialog_button_bg_selector,
                textColorRes = R.color.blue_btn_text_color_selector,
            )
        }
    }
}
