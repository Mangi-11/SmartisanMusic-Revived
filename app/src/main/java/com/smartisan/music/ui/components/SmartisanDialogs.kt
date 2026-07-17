package com.smartisan.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

internal val SmartisanDialogShape = RoundedCornerShape(12.dp)
internal val SmartisanDialogBackground = Color.White
internal val SmartisanDialogDividerColor = Color(0xFFE6E6E6)
internal val SmartisanDialogTitleStyle = TextStyle(
    fontSize = 17.sp,
    fontWeight = FontWeight.SemiBold,
    color = Color(0xCC000000),
)
internal val SmartisanDialogBodyStyle = TextStyle(
    fontSize = 14.sp,
    color = Color(0x99000000),
    textAlign = TextAlign.Center,
)
internal val SmartisanDialogMessageStyle = SmartisanDialogBodyStyle.copy(
    lineHeight = 21.sp,
)
internal val SmartisanDialogActionStyle = TextStyle(
    fontSize = 16.sp,
    color = Color(0xCC000000),
    textAlign = TextAlign.Center,
)
internal val SmartisanDialogPrimaryActionStyle = SmartisanDialogActionStyle.copy(
    color = Color(0xFF5E88E8),
    fontWeight = FontWeight.Medium,
)
internal val SmartisanDialogSecondaryActionStyle = SmartisanDialogActionStyle.copy(
    color = Color(0x8F000000),
)

private val SmartisanDialogDefaultWidth = 278.dp
private val SmartisanDialogActionHeight = 52.dp
private val SmartisanDialogDividerInset = 24.dp

@Composable
internal fun SmartisanDialogCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = SmartisanDialogDefaultWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(200),
        label = "dialog_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150),
        label = "dialog_alpha",
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .width(width)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            shape = SmartisanDialogShape,
            color = SmartisanDialogBackground,
        ) {
            Column(content = content)
        }
    }
}

@Composable
internal fun SmartisanDialogInsetDivider(
    contentPadding: PaddingValues = PaddingValues(),
) {
    HorizontalDivider(
        modifier = Modifier
            .padding(contentPadding)
            .padding(horizontal = SmartisanDialogDividerInset),
        color = SmartisanDialogDividerColor,
    )
}

@Composable
internal fun SmartisanDialogActionRow(
    label: String,
    style: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SmartisanDialogActionHeight)
            .background(if (isPressed) Color(0xFFF5F5F5) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (trailing == null) {
            Text(
                text = label,
                style = style,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = style.copy(textAlign = TextAlign.Start),
                    modifier = Modifier.weight(1f),
                )
                trailing()
            }
        }
    }
}

@Composable
internal fun SmartisanConfirmDialog(
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = SmartisanDialogDefaultWidth,
    message: String? = null,
) {
    SmartisanDialogCard(
        onDismiss = onDismiss,
        modifier = modifier,
        width = width,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = SmartisanDialogTitleStyle,
                textAlign = TextAlign.Center,
            )
        }
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = SmartisanDialogMessageStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 18.dp),
            )
        } else {
            Box(modifier = Modifier.height(18.dp))
        }
        SmartisanDialogInsetDivider()
        SmartisanDialogActionRow(
            label = confirmText,
            style = SmartisanDialogPrimaryActionStyle,
            onClick = onConfirm,
        )
        SmartisanDialogInsetDivider()
        SmartisanDialogActionRow(
            label = dismissText,
            style = SmartisanDialogSecondaryActionStyle,
            onClick = onDismiss,
        )
    }
}
