package com.smartisan.music.ui.shell.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.smartisan.music.R
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanClick
import com.smartisan.music.ui.components.smartisanStateColor
import com.smartisan.music.ui.navigation.MusicDestination

@Composable
internal fun SmartisanBottomTabItem(
    destination: MusicDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    inOverflow: Boolean = false,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed) 1.1f else if (selected) 1f else .9f,
            tween(
                if (pressed) 120 else 200,
                easing = com.smartisan.music.ui.components.SmartisanEaseInOut,
            ),
            label = "tabScale",
        )
    Column(
        modifier
            .semantics { this.selected = selected }
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                enabled = enabled,
                onClick = smartisanClick(onClick),
                onLongClick = onLongClick,
                onLongClickLabel =
                    if (onLongClick != null) stringResource(R.string.navigation_editor_open_action)
                    else null,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            rememberSmartisanDrawablePainter(
                if (inOverflow) destination.overflowIconRes else destination.bottomIconRes,
                enabled = enabled,
                pressed = pressed,
                selected = selected,
                checked = selected,
            ),
            contentDescription = null,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        Spacer(Modifier.height(dimensionResource(R.dimen.smartisan_switch_bar_drawablePadding)))
        BasicText(
            stringResource(destination.labelRes),
            Modifier.fillMaxWidth().graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            style =
                TextStyle(
                    color =
                        smartisanStateColor(
                            R.color.tab_bar_text_color,
                            pressed = pressed,
                            selected = selected,
                        ),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
