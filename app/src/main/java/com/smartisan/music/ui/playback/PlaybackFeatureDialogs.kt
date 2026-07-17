package com.smartisan.music.ui.playback

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisan.music.R
import com.smartisan.music.playback.PlaybackSleepTimerState
import com.smartisan.music.ui.components.SmartisanDialogActionRow
import com.smartisan.music.ui.components.SmartisanDialogBodyStyle
import com.smartisan.music.ui.components.SmartisanDialogCard
import com.smartisan.music.ui.components.SmartisanDialogDividerColor
import com.smartisan.music.ui.components.SmartisanDialogSecondaryActionStyle
import com.smartisan.music.ui.components.SmartisanDialogTitleStyle
import android.graphics.Color as AndroidColor

private const val MinuteMs = 60_000L

internal val PlaybackSleepTimerOptions = listOf(
    SleepTimerOption(R.string.time_15m, 15L * MinuteMs),
    SleepTimerOption(R.string.time_30m, 30L * MinuteMs),
    SleepTimerOption(R.string.time_1h, 60L * MinuteMs),
    SleepTimerOption(R.string.time_1_5h, 90L * MinuteMs),
    SleepTimerOption(R.string.time_2h, 120L * MinuteMs),
    SleepTimerOption(R.string.time_no, 0L),
)

internal data class SleepTimerOption(
    @field:StringRes val labelRes: Int,
    val durationMs: Long,
)

@Composable
internal fun PlaybackSleepTimerDialog(
    state: PlaybackSleepTimerState,
    onDismiss: () -> Unit,
    onDurationSelected: (Long) -> Unit,
) {
    SmartisanDialogCard(onDismiss = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.setting_stop_time),
                style = SmartisanDialogTitleStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp),
            )
            if (state.isActive) {
                Text(
                    text = "${androidx.compose.ui.res.stringResource(R.string.remain_time)} ${formatSleepTimerRemaining(state.remainingMs)}",
                    style = SmartisanDialogBodyStyle,
                    modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = SmartisanDialogDividerColor,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val first = PlaybackSleepTimerOptions[row * 2]
                        val second = PlaybackSleepTimerOptions[row * 2 + 1]
                        SleepTimerGridItem(
                            label = androidx.compose.ui.res.stringResource(first.labelRes),
                            selected = state.isActive && state.durationMs == first.durationMs,
                            modifier = Modifier.weight(1f),
                            onClick = { onDurationSelected(first.durationMs) },
                        )
                        SleepTimerGridItem(
                            label = androidx.compose.ui.res.stringResource(second.labelRes),
                            selected = state.isActive && state.durationMs == second.durationMs,
                            modifier = Modifier.weight(1f),
                            onClick = { onDurationSelected(second.durationMs) },
                        )
                    }
                }
            }
            HorizontalDivider(color = SmartisanDialogDividerColor)
            SmartisanDialogActionRow(
                label = androidx.compose.ui.res.stringResource(R.string.cancel),
                style = SmartisanDialogSecondaryActionStyle,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
internal fun PlaybackConfirmDialog(
    title: String,
    message: String? = null,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val latestOnConfirm by rememberUpdatedState(onConfirm)
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    DisposableEffect(context, title, message, confirmText, dismissText) {
        val dialog = RevonePlaybackConfirmDialog(
            context = context,
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = {
                latestOnConfirm()
            },
            onDismiss = {
                latestOnDismiss()
            },
        )
        dialog.show()
        onDispose {
            dialog.dismissIfShowing()
        }
    }
}

private class RevonePlaybackConfirmDialog(
    private val context: Context,
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String,
    private val onConfirm: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private val dialog = Dialog(context, R.style.MmsDialogTheme)

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
                text = title
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

        if (!message.isNullOrBlank()) {
            val content = FrameLayout(context).apply {
                setBackgroundResource(R.drawable.revone_global_dialog_message_background)
                setPadding(context.dpPx(24), context.dpPx(18), context.dpPx(24), context.dpPx(18))
                minimumHeight = context.dpPx(76)
            }
            content.addView(
                TextView(context).apply {
                    text = message
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(R.color.setting_item_summary_text_color))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setLineSpacing(context.dpPx(2).toFloat(), 1f)
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            root.addView(
                content,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttons.addView(
            dialogButton(dismissText, R.drawable.btn_text_color_selector).apply {
                setBackgroundResource(R.drawable.revone_dialog_button_left_bg_selector)
                setOnClickListener {
                    dialog.dismiss()
                    onDismiss()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )
        buttons.addView(
            android.view.View(context).apply {
                setBackgroundResource(R.drawable.revone_button_dialog_vertical_divider)
            },
            LinearLayout.LayoutParams(context.dpPx(1), LinearLayout.LayoutParams.MATCH_PARENT),
        )
        buttons.addView(
            dialogButton(confirmText, R.color.blue_btn_text_color_selector).apply {
                setBackgroundResource(R.drawable.revone_dialog_button_right_bg_selector)
                setOnClickListener {
                    dialog.dismiss()
                    onConfirm()
                }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )
        root.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.revone_dialog_button_height),
            ),
        )
    }

    fun show() {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setDimAmount(0.54f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.revone_global_dialog_content_width), WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    fun dismissIfShowing() {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }

    private fun dialogButton(text: String, textColorSelector: Int): Button {
        return Button(context).apply {
            gravity = Gravity.CENTER
            this.text = text
            setTextColor(context.getColorStateList(textColorSelector))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }
}

@Composable
private fun SleepTimerGridItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor = when {
        selected -> Color(0xFF5E88E8)
        isPressed -> Color(0xFFE8E8E8)
        else -> Color(0xFFF5F5F5)
    }
    val contentColor = if (selected) Color.White else Color(0xCC000000)

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
            )
        }
    }
}

internal fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun Context.dpPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()
