package com.smartisan.music.ui.components

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.smartisan.music.R

/** Compose owns the content; the public dialog window preserves dismissal and IME boundaries. */
@Composable
internal fun SmartisanModal(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bottom: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.54f)
            window.setGravity(if (bottom) Gravity.BOTTOM else Gravity.CENTER)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            // The window survives composition removal for the platform's exit animation;
            // delaying the caller's confirmation would change the existing data/event order.
            if (bottom) window.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        Column(modifier, content = content)
    }
}

@Composable
internal fun SmartisanMenuTitleBar(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
) {
    Box(
        Modifier.fillMaxWidth()
            .height(dimensionResource(R.dimen.titlebar_height))
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(R.drawable.bottom_sheet_title_bar_bg)
            )
    ) {
        BasicText(
            title,
            Modifier.align(Alignment.Center)
                .padding(
                    horizontal =
                        dimensionResource(R.dimen.bar_margin_edge) +
                            dimensionResource(R.dimen.standard_icon_size) +
                            6.dp
                ),
            style =
                TextStyle(
                    color = colorResource(R.color.title_text_color),
                    fontSize = smartisanTextSize(R.dimen.semi_small_text_size),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onConfirm != null) {
            ModalIcon(
                R.drawable.standard_icon_cancel_selector,
                stringResource(R.string.cancel),
                onDismiss,
                Modifier.align(AbsoluteAlignment.CenterLeft),
            )
        }
        ModalIcon(
            if (onConfirm == null) R.drawable.standard_icon_cancel_selector
            else R.drawable.standard_icon_complete_selector,
            stringResource(if (onConfirm == null) R.string.cancel else R.string.done),
            onConfirm ?: onDismiss,
            Modifier.align(AbsoluteAlignment.CenterRight),
            enabled = onConfirm == null || confirmEnabled,
        )
    }
}

@Composable
private fun ModalIcon(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val size = dimensionResource(R.dimen.standard_icon_size)
    val base = LocalViewConfiguration.current
    val config =
        remember(base, size) {
            object : ViewConfiguration by base {
                override val minimumTouchTargetSize = DpSize(size, size)
            }
        }
    CompositionLocalProvider(LocalViewConfiguration provides config) {
        Image(
            rememberSmartisanDrawablePainter(icon, enabled = enabled, pressed = pressed),
            label,
            modifier
                .alpha(if (enabled) 1f else 0.35f)
                .padding(horizontal = dimensionResource(R.dimen.bar_margin_edge))
                .size(size)
                .clickable(interaction, null, enabled = enabled, onClick = smartisanClick(onClick)),
            contentScale = ContentScale.Inside,
        )
    }
}

@Composable
internal fun SmartisanDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundRes: Int = R.drawable.shrink_long_btn_red_selector,
    textColorRes: Int = android.R.color.white,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shrinkButton = backgroundRes == R.drawable.shrink_long_btn_red_selector
    val background =
        if (shrinkButton) {
            Modifier.smartisanShadowBackground(
                backgroundRes,
                R.drawable.shadow_button_shrink_shadow_selector,
                enabled = enabled,
                pressed = pressed,
            )
        } else {
            Modifier.smartisanPainterBackground(
                rememberSmartisanDrawablePainter(
                    backgroundRes,
                    enabled = enabled,
                    pressed = pressed,
                )
            )
        }
    Box(
        modifier
            .heightIn(min = if (shrinkButton) 48.dp else 44.dp)
            .then(background)
            .clickable(interaction, null, enabled = enabled, onClick = smartisanClick(onClick)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style =
                TextStyle(
                    color = smartisanStateColor(textColorRes, enabled = enabled, pressed = pressed),
                    fontSize =
                        if (shrinkButton) smartisanTextSize(R.dimen.semi_large_text_size)
                        else 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SmartisanDeleteConfirmation(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartisanModal(onDismiss, modifier.fillMaxWidth(), bottom = true) {
        SmartisanMenuTitleBar(title, onDismiss)
        Column(
            Modifier.fillMaxWidth()
                .smartisanPainterBackground(
                    rememberSmartisanDrawablePainter(R.drawable.menu_dialog_background)
                )
                .padding(horizontal = dimensionResource(R.dimen.menu_dialog_horizontal_distance))
                .padding(
                    top = dimensionResource(R.dimen.menu_dialog_btn_margin_view),
                    bottom = dimensionResource(R.dimen.menu_dialog_btn_margin_edge),
                )
        ) {
            SmartisanDialogButton(
                stringResource(R.string.dialog_delete_conform),
                onConfirm,
                Modifier.fillMaxWidth(),
            )
        }
    }
}
