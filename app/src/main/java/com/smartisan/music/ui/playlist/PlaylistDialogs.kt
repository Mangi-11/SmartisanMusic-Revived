package com.smartisan.music.ui.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartisan.music.R
import com.smartisan.music.ui.components.*
import kotlinx.coroutines.delay

@Composable
internal fun PlaylistNameDialogOverlay(
    request: PlaylistNameDialogRequest?,
    onDismiss: () -> Unit,
    onConfirm: (PlaylistNameDialogRequest, String) -> Unit,
) {
    if (request == null) return
    val title =
        stringResource(
            if (request is PlaylistNameDialogRequest.Create) request.titleRes
            else R.string.playlist_rename_title
        )
    val confirmText =
        stringResource(
            if (request is PlaylistNameDialogRequest.Create) R.string.rename_continue
            else R.string.save
        )
    key(request) {
        var value by
            rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(
                    TextFieldValue(request.initialName, TextRange(0, request.initialName.length))
                )
            }
        val focus = remember { FocusRequester() }
        val enabled = value.text.isNotBlank()
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
                        title,
                        style =
                            TextStyle(
                                color = colorResource(R.color.status_bar_color_dialog),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                            ),
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .smartisanPainterBackground(
                            rememberSmartisanDrawablePainter(
                                R.drawable.revone_global_dialog_message_background
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .height(40.dp)
                            .smartisanPainterBackground(
                                rememberSmartisanDrawablePainter(R.drawable.edit_text_bg)
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value,
                            { value = it },
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
                            keyboardActions =
                                KeyboardActions(
                                    onDone = { if (enabled) onConfirm(request, value.text) }
                                ),
                        )
                        Image(
                            rememberSmartisanDrawablePainter(R.drawable.quick_icon_delete),
                            stringResource(R.string.delete),
                            Modifier.size(32.dp).clickable(
                                remember { MutableInteractionSource() },
                                null,
                            ) {
                                value = TextFieldValue()
                            },
                            contentScale = ContentScale.Inside,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .height(dimensionResource(R.dimen.revone_dialog_button_height))
                ) {
                    SmartisanDialogButton(
                        stringResource(android.R.string.cancel),
                        onDismiss,
                        Modifier.weight(1f).fillMaxHeight(),
                        backgroundRes = R.drawable.revone_dialog_button_left_bg_selector,
                        textColorRes = R.drawable.btn_text_color_selector,
                    )
                    Spacer(
                        Modifier.width(1.dp)
                            .fillMaxHeight()
                            .smartisanPainterBackground(
                                rememberSmartisanDrawablePainter(
                                    R.drawable.revone_button_dialog_vertical_divider
                                )
                            )
                    )
                    SmartisanDialogButton(
                        confirmText,
                        { onConfirm(request, value.text) },
                        Modifier.weight(1f).fillMaxHeight(),
                        enabled,
                        R.drawable.revone_dialog_button_right_bg_selector,
                        R.color.blue_btn_text_color_selector,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaylistDeleteDialog(
    request: PlaylistDeleteRequest?,
    onDismiss: () -> Unit,
    onConfirm: (PlaylistDeleteRequest) -> Unit,
) {
    if (request == null) return
    val title =
        when (request) {
            PlaylistDeleteRequest.RootSelected -> R.string.playlist_delete_confirm
            PlaylistDeleteRequest.DetailPlaylist -> R.string.playlist_delete_single_confirm
            PlaylistDeleteRequest.DetailTracks -> R.string.playlist_remove_song_confirm
        }
    SmartisanDeleteConfirmation(stringResource(title), onDismiss, { onConfirm(request) })
}
