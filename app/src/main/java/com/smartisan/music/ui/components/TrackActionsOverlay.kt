package com.smartisan.music.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisan.music.R

internal data class TrackActionItem(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:DrawableRes val pressedIconRes: Int = iconRes,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun TrackActionsOverlay(
    visible: Boolean,
    actions: List<TrackActionItem>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var retained by remember { mutableStateOf(actions) }
    LaunchedEffect(visible, actions) { if (visible) retained = actions }
    val displayed = if (visible) actions else retained
    SmartisanAnimatedSheet(
        visible,
        onDismissRequest,
        modifier,
        scrim = colorResource(R.color.transparent_black),
        hiddenPanelAlpha = 0.92f,
    ) {
        SmartisanMenuTitleBar(stringResource(R.string.select_action), onDismissRequest)
        val columns = displayed.size.coerceIn(1, 4)
        displayed.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { action -> ActionCell(action, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f).height(72.dp)) }
            }
        }
        Spacer(
            Modifier.fillMaxWidth()
                .height(with(LocalDensity.current) { 1.toDp() })
                .background(colorResource(R.color.bottom_line))
        )
    }
}

@Composable
private fun ActionCell(action: TrackActionItem, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .height(72.dp)
            .alpha(if (action.enabled) 1f else 0.35f)
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    R.drawable.menu_item_selector,
                    enabled = action.enabled,
                    pressed = pressed,
                )
            )
            .clickable(
                interaction,
                null,
                enabled = action.enabled,
                onClick = smartisanClick(action.onClick),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            rememberSmartisanDrawablePainter(
                if (pressed) action.pressedIconRes else action.iconRes,
                enabled = action.enabled,
            ),
            null,
        )
        BasicText(
            stringResource(action.labelRes),
            Modifier.padding(start = 7.dp, end = 7.dp, top = 1.dp),
            style =
                TextStyle(
                    color =
                        colorResource(
                            if (action.selected) R.color.btn_text_color_blue
                            else R.color.add_nav_text_color
                        ),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
        )
    }
}
