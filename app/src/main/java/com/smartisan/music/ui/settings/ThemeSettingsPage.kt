package com.smartisan.music.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import com.smartisan.music.data.settings.ThemeMode
import com.smartisan.music.ui.components.SmartisanTitleBar
import com.smartisan.music.ui.components.SmartisanTitleBarAction
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanShadowBackground
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.components.smartisanTextSize

@Composable
internal fun ThemeSettingsPage(
    active: Boolean,
    themeMode: ThemeMode,
    onClose: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier.fillMaxSize().background(colorResource(R.color.page_background))) {
        if (active) {
            SmartisanTitleBar(
                title = stringResource(R.string.theme_settings),
                navigationIcon =
                    SmartisanTitleBarAction(
                        R.drawable.standard_icon_back_selector,
                        stringResource(R.string.back),
                        onClose,
                    ),
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .smartisanPainterBackground(
                            painter =
                                rememberSmartisanDrawablePainter(R.drawable.account_background)
                        )
                        .verticalScroll(scrollState)
                        .padding(
                            horizontal = dimensionResource(R.dimen.list_item_left_right_margin),
                            vertical = dimensionResource(R.dimen.list_item_vertical_gap),
                        )
                        .selectableGroup()
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    ThemeChoiceRow(
                        mode = mode,
                        selected = mode == themeMode,
                        position = index,
                        onClick = { onThemeModeChange(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChoiceRow(
    mode: ThemeMode,
    selected: Boolean,
    position: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val (background, shadow) =
        when (position) {
            0 -> R.drawable.group_list_item_bg_top to R.drawable.list_content_item_top_shadow
            ThemeMode.entries.lastIndex ->
                R.drawable.group_list_item_bg_bottom to R.drawable.list_content_item_bottom_shadow
            else -> R.drawable.group_list_item_bg_mid to R.drawable.list_content_item_middle_shadow
        }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(dimensionResource(R.dimen.list_item_min_height))
                .smartisanShadowBackground(background, shadow, pressed = pressed, focused = focused)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = smartisanClick(onClick),
                )
    ) {
        BasicText(
            text = stringResource(mode.labelRes),
            modifier =
                Modifier.align(AbsoluteAlignment.CenterLeft)
                    .fillMaxWidth()
                    .absolutePadding(
                        left = ThemeChoiceTextStart,
                        right = ThemeChoiceMarkEnd + ThemeChoiceMarkSize + ThemeChoiceTextEnd,
                    ),
            style =
                TextStyle(
                    color =
                        smartisanStateColor(
                            R.color.setting_item_text_colorlist,
                            pressed = pressed,
                            focused = focused,
                        ),
                    fontSize = smartisanTextSize(R.dimen.primary_text_size),
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
        if (selected) {
            Image(
                painter = rememberSmartisanDrawablePainter(R.drawable.selector_radio_choice),
                contentDescription = null,
                contentScale = ContentScale.Inside,
                modifier =
                    Modifier.align(AbsoluteAlignment.CenterRight)
                        .absolutePadding(right = ThemeChoiceMarkEnd)
                        .size(ThemeChoiceMarkSize),
            )
        }
    }
}

// SettingsChoiceRow's measured content offsets; the radio image reserves space even
// when unselected. It did not duplicate the parent's pressed state.
private val ThemeChoiceTextStart = 18.dp
private val ThemeChoiceTextEnd = 10.dp
private val ThemeChoiceMarkEnd = 14.dp
private val ThemeChoiceMarkSize = 28.dp

@Preview(name = "Theme / light", widthDp = 360, heightDp = 300)
@Preview(
    name = "Theme / dark",
    widthDp = 360,
    heightDp = 300,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "Theme / large text", widthDp = 320, heightDp = 300, fontScale = 1.5f)
@Composable
private fun ThemeSettingsPreview() {
    ThemeSettingsPage(
        active = true,
        themeMode = ThemeMode.System,
        onClose = {},
        onThemeModeChange = {},
    )
}
