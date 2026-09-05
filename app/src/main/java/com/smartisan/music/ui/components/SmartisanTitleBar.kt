package com.smartisan.music.ui.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.smartisan.music.R

internal data class SmartisanTitleBarAction(
    @param:DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val checked: Boolean? = null,
    val modifier: Modifier = Modifier,
)

/** Resource-based title bar with physical left/right action groups and a symmetric center. */
@Composable
internal fun SmartisanTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: SmartisanTitleBarAction? = null,
    action: SmartisanTitleBarAction? = null,
    includeStatusBar: Boolean = true,
    showShadow: Boolean = true,
    navigationActions: List<SmartisanTitleBarAction> = emptyList(),
    actions: List<SmartisanTitleBarAction> = emptyList(),
    centerContent: (@Composable () -> Unit)? = null,
    contentHeight: androidx.compose.ui.unit.Dp = dimensionResource(R.dimen.title_bar_height),
) {
    val shadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
    val iconSize = dimensionResource(R.dimen.standard_icon_size)
    val edgeMargin = dimensionResource(R.dimen.bar_margin_edge)
    val leftActions = listOfNotNull(navigationIcon) + navigationActions
    val rightActions = listOfNotNull(action) + actions
    val gap = dimensionResource(R.dimen.title_bar_margin_view)
    val widestCount = maxOf(leftActions.size, rightActions.size)
    val titleInset =
        if (widestCount > 0) edgeMargin + iconSize * widestCount + gap * (widestCount - 1) else 0.dp
    val centerVisible =
        widestCount == 0 || titleInset + gap <= dimensionResource(R.dimen.title_bar_center_limite)
    Column(
        modifier
            .then(if (showShadow) Modifier.zIndex(1f) else Modifier)
            .fillMaxWidth()
            .background(colorResource(R.color.title_bar_background))
    ) {
        if (includeStatusBar) {
            Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
        }
        Box(Modifier.fillMaxWidth().height(contentHeight)) {
            if (centerVisible) {
                if (centerContent == null) {
                    SmartisanTitleText(title, titleInset, Modifier.matchParentSize())
                } else {
                    Box(
                        Modifier.matchParentSize().padding(horizontal = titleInset),
                        contentAlignment = Alignment.Center,
                    ) {
                        centerContent()
                    }
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    Modifier.align(AbsoluteAlignment.CenterLeft).absolutePadding(left = edgeMargin),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    leftActions.forEach { TitleBarIcon(it) }
                }
                Row(
                    Modifier.align(AbsoluteAlignment.CenterRight)
                        .absolutePadding(right = edgeMargin),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    rightActions.asReversed().forEach { TitleBarIcon(it) }
                }
            }
            if (showShadow) {
                SmartisanDrawableBackground(
                    R.drawable.title_bar_shadow,
                    Modifier.align(Alignment.BottomCenter)
                        .offset(y = shadowHeight)
                        .fillMaxWidth()
                        .height(shadowHeight),
                )
            }
        }
    }
}

@Composable
private fun TitleBarIcon(action: SmartisanTitleBarAction, modifier: Modifier = Modifier) {
    val iconSize = dimensionResource(R.dimen.standard_icon_size)
    val viewConfiguration = LocalViewConfiguration.current
    val iconViewConfiguration =
        remember(viewConfiguration, iconSize) {
            object : ViewConfiguration by viewConfiguration {
                override val minimumTouchTargetSize = DpSize(iconSize, iconSize)
            }
        }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by
        animateFloatAsState(
            targetValue = if (pressed) TitleBarPressedScale else 1f,
            animationSpec =
                spring(
                    dampingRatio = TitleBarDampingRatio,
                    stiffness = TitleBarStiffness,
                    visibilityThreshold = TitleBarVisibilityThreshold,
                ),
            label = "Smartisan title icon press",
        )
    // Preserve this shim button's 36dp hit bounds and transform input with the whole icon.
    CompositionLocalProvider(LocalViewConfiguration provides iconViewConfiguration) {
        Box(
            modifier
                .then(action.modifier)
                .size(iconSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    enabled = action.enabled,
                    onClick = smartisanClick(action.onClick),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter =
                    rememberSmartisanDrawablePainter(
                        action.iconRes,
                        enabled = action.enabled,
                        pressed = pressed,
                        focused = focused,
                        checked = action.checked == true,
                    ),
                contentDescription = action.contentDescription,
                contentScale = ContentScale.None,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

// TitleBar.TitleBarIconScaleTouchListener, retained from the calibrated 8.1.0 shim.
private const val TitleBarPressedScale = 1.33f
private const val TitleBarDampingRatio = 0.55f
private const val TitleBarStiffness = 800f
// DynamicAnimation 1.1.0: MIN_VISIBLE_CHANGE_SCALE (1/500) * THRESHOLD_MULTIPLIER (0.75).
private const val TitleBarVisibilityThreshold = 0.0015f

@Preview(name = "Title / light", widthDp = 360)
@Preview(name = "Title / dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Title / large text", widthDp = 320, fontScale = 1.5f)
@Composable
private fun SmartisanTitleBarPreview() {
    SmartisanTitleBar(
        title = stringResource(R.string.app_icon),
        navigationIcon =
            SmartisanTitleBarAction(
                R.drawable.standard_icon_back_selector,
                stringResource(R.string.back),
                onClick = {},
            ),
        includeStatusBar = false,
    )
}
