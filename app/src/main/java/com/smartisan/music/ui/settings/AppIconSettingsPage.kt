package com.smartisan.music.ui.settings

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.smartisan.music.R
import com.smartisan.music.launcher.AppIcon
import com.smartisan.music.ui.components.SmartisanTitleBar
import com.smartisan.music.ui.components.SmartisanTitleBarAction
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.components.smartisanShadowBackground
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.components.smartisanTextSize

@Composable
internal fun AppIconSettingsPage(
    active: Boolean,
    selectedIcon: AppIcon,
    onClose: () -> Unit,
    onIconSelected: (AppIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the page position while the shell hides this tab, without retaining hidden input
    // targets.
    val scrollState = rememberScrollState()
    Column(modifier = modifier.fillMaxSize().background(colorResource(R.color.page_background))) {
        if (active) {
            SmartisanTitleBar(
                title = stringResource(R.string.app_icon),
                modifier = Modifier.fillMaxWidth(),
                navigationIcon =
                    SmartisanTitleBarAction(
                        iconRes = R.drawable.standard_icon_back_selector,
                        contentDescription = stringResource(R.string.back),
                        onClick = onClose,
                    ),
            )
            AppIconSettingsContent(
                selectedIcon = selectedIcon,
                scrollState = scrollState,
                onIconSelected = onIconSelected,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

internal fun AppIcon.labelRes(): Int {
    return when (this) {
        AppIcon.Original -> R.string.app_icon_original
        AppIcon.Modern -> R.string.app_icon_modern
    }
}

private fun AppIcon.summaryRes(): Int {
    return when (this) {
        AppIcon.Original -> R.string.app_icon_original_summary
        AppIcon.Modern -> R.string.app_icon_modern_summary
    }
}

private fun AppIcon.previewRes(): Int {
    return when (this) {
        AppIcon.Original -> R.mipmap.ic_launcher
        AppIcon.Modern -> R.mipmap.ic_launcher_modern
    }
}

@Composable
private fun AppIconSettingsContent(
    selectedIcon: AppIcon,
    scrollState: ScrollState,
    onIconSelected: (AppIcon) -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalMargin = dimensionResource(R.dimen.list_item_left_right_margin)
    val verticalGap = dimensionResource(R.dimen.list_item_vertical_gap)
    val tipsStyle =
        TextStyle(
            color = colorResource(R.color.setting_item_summary_text_color),
            fontSize = smartisanTextSize(R.dimen.settings_item_tips_text_size),
            fontFamily = FontFamily.SansSerif,
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        )
    Column(
        modifier =
            modifier
                .smartisanPainterBackground(
                    painter = rememberSmartisanDrawablePainter(R.drawable.account_background)
                )
                .verticalScroll(scrollState)
    ) {
        Spacer(Modifier.height(verticalGap))
        BasicText(
            text = stringResource(R.string.app_icon_choose),
            style = tipsStyle.copy(fontWeight = FontWeight.Bold),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = horizontalMargin + AppIconSettingsMetrics.TipsHorizontalPadding
                    )
                    .padding(bottom = AppIconSettingsMetrics.SectionTitleBottomPadding),
        )
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = horizontalMargin).selectableGroup()
        ) {
            AppIcon.entries.forEachIndexed { index, icon ->
                AppIconSettingsRow(
                    icon = icon,
                    selected = icon == selectedIcon,
                    shape =
                        when (index) {
                            0 -> AppIconRowShape.Top
                            AppIcon.entries.lastIndex -> AppIconRowShape.Bottom
                            else -> AppIconRowShape.Middle
                        },
                    onClick = {
                        if (icon != selectedIcon) onIconSelected(icon)
                    },
                )
            }
        }
        BasicText(
            text = stringResource(R.string.app_icon_refresh_hint),
            style = tipsStyle,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = horizontalMargin + AppIconSettingsMetrics.TipsHorizontalPadding
                    )
                    .padding(top = AppIconSettingsMetrics.HintTopPadding),
        )
        Spacer(Modifier.height(verticalGap))
    }
}

@Composable
private fun AppIconSettingsRow(
    icon: AppIcon,
    selected: Boolean,
    shape: AppIconRowShape,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val title = stringResource(icon.labelRes())
    val summary = stringResource(icon.summaryRes())
    val description = stringResource(R.string.app_icon_option_description, title, summary)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // RelativeLayout used physical LEFT/RIGHT rules; text still follows the outer locale.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(dimensionResource(R.dimen.list_item_min_height))
                    .smartisanShadowBackground(
                        backgroundRes = shape.backgroundRes,
                        shadowRes = shape.shadowRes,
                        pressed = pressed,
                        selected = selected,
                        focused = focused,
                    )
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = smartisanClick(onClick),
                    )
                    .semantics { contentDescription = description }
                    .padding(
                        start = AppIconSettingsMetrics.PreviewStartMargin,
                        end = AppIconSettingsMetrics.SelectedEndMargin,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = rememberSmartisanDrawablePainter(icon.previewRes()),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(AppIconSettingsMetrics.PreviewSize),
            )
            Column(
                modifier =
                    Modifier.weight(1f)
                        .padding(
                            start = AppIconSettingsMetrics.TextStartMargin,
                            end = AppIconSettingsMetrics.TextEndMargin,
                        )
                        .clearAndSetSemantics {}
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                    style =
                        TextStyle(
                            color =
                                smartisanStateColor(
                                    R.color.setting_item_text_colorlist,
                                    pressed = pressed,
                                    selected = selected,
                                    focused = focused,
                                ),
                            fontSize = smartisanTextSize(R.dimen.primary_text_size),
                            fontFamily = FontFamily.SansSerif,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            textDirection =
                                if (isRtl) TextDirection.ContentOrRtl
                                else TextDirection.ContentOrLtr,
                            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        ),
                )
                BasicText(
                    text = summary,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        TextStyle(
                            color =
                                smartisanStateColor(
                                    R.color.setting_item_summary_text_colorlist,
                                    pressed = pressed,
                                    selected = selected,
                                    focused = focused,
                                ),
                            fontSize = smartisanTextSize(R.dimen.settings_item_tips_text_size),
                            fontFamily = FontFamily.SansSerif,
                            platformStyle = PlatformTextStyle(includeFontPadding = true),
                            textDirection =
                                if (isRtl) TextDirection.ContentOrRtl
                                else TextDirection.ContentOrLtr,
                            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left,
                        ),
                )
            }
            // INVISIBLE in the View version reserved this width for both options.
            Box(modifier = Modifier.size(AppIconSettingsMetrics.SelectedSize)) {
                if (selected) {
                    Image(
                        painter =
                            rememberSmartisanDrawablePainter(R.drawable.selector_radio_choice),
                        contentDescription = null,
                        contentScale = ContentScale.Inside,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// These local metrics preserve the programmatic View layout used for this added settings page.
private object AppIconSettingsMetrics {
    val TipsHorizontalPadding = 18.dp
    val SectionTitleBottomPadding = 7.dp
    val HintTopPadding = 10.dp
    val PreviewSize = 40.dp
    val PreviewStartMargin = 12.dp
    val SelectedSize = 28.dp
    val SelectedEndMargin = 14.dp
    val TextStartMargin = 12.dp
    val TextEndMargin = 8.dp
}

private enum class AppIconRowShape(
    @DrawableRes val backgroundRes: Int,
    @DrawableRes val shadowRes: Int,
) {
    Top(R.drawable.group_list_item_bg_top, R.drawable.list_content_item_top_shadow),
    Middle(R.drawable.group_list_item_bg_mid, R.drawable.list_content_item_middle_shadow),
    Bottom(R.drawable.group_list_item_bg_bottom, R.drawable.list_content_item_bottom_shadow),
}

@Preview(name = "App icon · Light", widthDp = 390, heightDp = 640, locale = "zh")
@Preview(
    name = "App icon · Dark",
    widthDp = 390,
    heightDp = 640,
    locale = "zh",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "App icon · Large text", widthDp = 390, heightDp = 640, fontScale = 1.5f)
@Composable
private fun AppIconSettingsPagePreview() {
    AppIconSettingsPage(
        active = true,
        selectedIcon = AppIcon.Modern,
        onClose = {},
        onIconSelected = {},
    )
}
