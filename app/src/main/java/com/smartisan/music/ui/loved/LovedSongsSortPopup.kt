package com.smartisan.music.ui.loved

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.smartisan.music.R
import com.smartisan.music.ui.components.*

@Composable
internal fun LovedSongsSortPopup(
    sortMode: LovedSongsSortMode,
    onSortModeChanged: (LovedSongsSortMode) -> Unit,
    onDismiss: () -> Unit,
    anchorBounds: IntRect,
) {
    var dismissing by remember { mutableStateOf(false) }
    val entrance = remember { Animatable(0f) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(dismissing) {
        entrance.animateTo(
            if (dismissing) 0f else 1f,
            tween(if (dismissing) 100 else 150, easing = Easing { 1f - (1f - it) * (1f - it) }),
        )
        if (dismissing) latestDismiss()
    }
    val dismiss = { dismissing = true }
    val density = LocalDensity.current
    val localeDirection = LocalLayoutDirection.current
    val textDirection =
        if (localeDirection == LayoutDirection.Rtl) TextDirection.ContentOrRtl
        else TextDirection.ContentOrLtr
    val textAlign = if (localeDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
    val horizontalShadow = dimensionResource(R.dimen.popup_bg_left_right_shadow_width)
    val verticalShadow = dimensionResource(R.dimen.popup_bg_top_bottom_shadow_height)
    val width = dimensionResource(R.dimen.popup_list_menu_default_width)
    val minDistance = dimensionResource(R.dimen.menu_panel_bg_min_distance)
    val iconBounds = anchorBounds
    val position =
        remember(density, anchorBounds, horizontalShadow, width, minDistance) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset =
                    with(density) {
                        val x = iconBounds.left - 158.dp.roundToPx()
                        val rightOverflow =
                            (horizontalShadow.roundToPx() +
                                    x +
                                    width.roundToPx() +
                                    minDistance.roundToPx() - windowSize.width)
                                .coerceAtLeast(0)
                        IntOffset(x - rightOverflow, iconBounds.top)
                    }
            }
        }
    Popup(position, dismiss, PopupProperties(focusable = true, clippingEnabled = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                Modifier.width(width + horizontalShadow * 2)
                    .graphicsLayer {
                        alpha = entrance.value
                        scaleX = 0.9f + 0.1f * entrance.value
                        scaleY = scaleX
                        translationY = -size.height * 0.06f * (1f - entrance.value)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .smartisanPainterBackground(
                        rememberSmartisanDrawablePainter(R.drawable.popup_menu_bg_shadow)
                    )
                    .padding(horizontal = horizontalShadow, vertical = verticalShadow)
            ) {
                Column(
                    Modifier.width(width)
                        .clip(RoundedCornerShape(10.dp))
                        .smartisanPainterBackground(
                            rememberSmartisanDrawablePainter(R.drawable.pop_up_menu_bg)
                        )
                        .selectableGroup()
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(dimensionResource(R.dimen.popup_list_menu_title_height))
                            .padding(
                                start = dimensionResource(R.dimen.popup_list_title_left_margin)
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicText(
                            stringResource(R.string.sort_title),
                            Modifier.fillMaxWidth(),
                            style =
                                TextStyle(
                                    color = colorResource(R.color.sub_title_text_color),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDirection = textDirection,
                                    textAlign = textAlign,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                        )
                    }
                    Spacer(
                        Modifier.fillMaxWidth()
                            .height(dimensionResource(R.dimen.list_divider_height))
                            .background(colorResource(R.color.list_divider_color))
                    )
                    LovedSongsSortMode.entries.forEachIndexed { index, mode ->
                        val source = remember { MutableInteractionSource() }
                        val pressed by source.collectIsPressedAsState()
                        Row(
                            Modifier.fillMaxWidth()
                                .height(dimensionResource(R.dimen.popup_list_menu_item_height))
                                .smartisanPainterBackground(
                                    rememberSmartisanDrawablePainter(
                                        R.drawable.revone_menu_list_selector,
                                        pressed = pressed,
                                    )
                                )
                                .selectable(
                                    mode == sortMode,
                                    source,
                                    null,
                                    role = Role.RadioButton,
                                    onClick =
                                        smartisanClick {
                                            onSortModeChanged(mode)
                                            dismiss()
                                        },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                rememberSmartisanDrawablePainter(
                                    if (mode == LovedSongsSortMode.Time)
                                        R.drawable.icon_sort_by_time
                                    else R.drawable.icon_sort_by_name
                                ),
                                null,
                                Modifier.width(44.dp).fillMaxHeight(),
                                contentScale = ContentScale.Inside,
                            )
                            BasicText(
                                stringResource(
                                    if (mode == LovedSongsSortMode.Time) R.string.sort_by_saved_time
                                    else R.string.sort_by_name
                                ),
                                Modifier.weight(1f).padding(end = 6.dp),
                                style =
                                    TextStyle(
                                        color = colorResource(R.color.title_text_color),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDirection = textDirection,
                                        textAlign = textAlign,
                                        platformStyle =
                                            PlatformTextStyle(includeFontPadding = true),
                                    ),
                                maxLines = 1,
                            )
                            if (mode == sortMode)
                                Image(
                                    rememberSmartisanDrawablePainter(
                                        R.drawable.selector_radio_choice
                                    ),
                                    null,
                                    Modifier.padding(end = 6.dp).size(27.dp),
                                    contentScale = ContentScale.Inside,
                                )
                        }
                        if (index != LovedSongsSortMode.entries.lastIndex)
                            Image(
                                rememberSmartisanDrawablePainter(
                                    R.drawable.revone_smartisan_list_popup_menu_separator
                                ),
                                null,
                                Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                    }
                }
            }
        }
    }
}
