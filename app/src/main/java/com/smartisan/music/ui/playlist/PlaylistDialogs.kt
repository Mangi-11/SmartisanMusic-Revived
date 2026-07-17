package com.smartisan.music.ui.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.smartisan.music.R
import com.smartisan.music.data.playlist.UserPlaylistSummary
import com.smartisan.music.ui.components.SmartisanDialogCard
import com.smartisan.music.ui.components.SmartisanDialogInsetDivider
import com.smartisan.music.ui.components.SmartisanDialogTitleStyle

private val ActionSheetShape = RectangleShape
private val DialogTextFieldBorder = Color(0xFFE2E2E2)
private val DialogTextFieldBackground = Color(0xFFF7F8F9)
private val DialogPlaceholder = Color(0x66000000)
private val DialogPrimaryButtonColor = Color(0xFF5E88E8)
private val DialogPrimaryPressedButton = Color(0xFF4F77D5)
private val DialogPrimaryBorder = Color(0xFF4C73CF)
private val DialogSecondaryBorder = Color(0xFFDCDCDC)
private val DialogSecondaryPressedBackground = Color(0xFFEFEFEF)
private val DialogButtonText = Color(0x8F000000)
private val ActionSheetScrim = Color(0x73000000)
private val ActionSheetDivider = Color(0xFFEAEAEA)

private val PlaylistActionSheetTitleStyle = TextStyle(
    fontSize = 15.sp,
    fontWeight = FontWeight.SemiBold,
    color = Color(0x99000000),
)
private val DialogTextFieldStyle = TextStyle(
    fontSize = 16.sp,
    color = Color(0xDE000000),
)
private val PlaylistPickerTitleStyle = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium,
    color = Color(0xCC000000),
)
private val PlaylistPickerSubtitleStyle = TextStyle(
    fontSize = 13.sp,
    color = Color(0x73000000),
)
private val PlaylistActionLabelStyle = TextStyle(
    fontSize = 11.sp,
    color = Color(0x99000000),
)

@Composable
internal fun PlaylistNameDialog(
    title: String,
    initialValue: String,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(R.string.continue_action),
    selectAllOnOpen: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue, selectAllOnOpen) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = if (selectAllOnOpen && initialValue.isNotEmpty()) {
                    TextRange(0, initialValue.length)
                } else {
                    TextRange(initialValue.length)
                },
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    SmartisanDialogCard(
        onDismiss = onDismiss,
        modifier = modifier,
        width = 308.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = title,
                    style = SmartisanDialogTitleStyle,
                )
            }
            SmartisanDialogInsetDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 18.dp)
                    .background(DialogTextFieldBackground, RoundedCornerShape(6.dp))
                    .border(1.dp, DialogTextFieldBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = DialogTextFieldStyle,
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (value.text.isEmpty()) {
                            androidx.compose.material3.Text(
                                text = initialValue,
                                style = DialogTextFieldStyle,
                                color = DialogPlaceholder,
                            )
                        }
                        innerTextField()
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DialogTextButton(
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                DialogPrimaryButton(
                    text = confirmText,
                    modifier = Modifier.weight(1f),
                    onClick = { onConfirm(value.text) },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
internal fun PlaylistPickerDialog(
    playlists: List<UserPlaylistSummary>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
) {
    SmartisanDialogCard(
        onDismiss = onDismiss,
        modifier = modifier,
        width = 320.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.playlist_picker_title),
                    style = SmartisanDialogTitleStyle,
                )
            }
            SmartisanDialogInsetDivider()
            PlaylistPickerCreateRow(onClick = onCreateNewPlaylist)
            SmartisanDialogInsetDivider()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                itemsIndexed(
                    items = playlists,
                    key = { _, playlist -> playlist.id },
                ) { index, playlist ->
                    PlaylistPickerRow(
                        playlist = playlist,
                        onClick = { onPlaylistSelected(playlist.id) },
                    )
                    if (index < playlists.lastIndex) {
                        SmartisanDialogInsetDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistTrackActionDialog(
    modifier: Modifier = Modifier,
    thirdActionText: String,
    thirdActionIconRes: Int,
    thirdActionPressedIconRes: Int,
    onDismiss: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onThirdActionClick: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        shape = ActionSheetShape,
        containerColor = Color.White,
        tonalElevation = 0.dp,
        scrimColor = ActionSheetScrim,
        dragHandle = null,
    ) {
        PlaylistTrackActionSheetContent(
            thirdActionText = thirdActionText,
            thirdActionIconRes = thirdActionIconRes,
            thirdActionPressedIconRes = thirdActionPressedIconRes,
            onDismiss = onDismiss,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onAddToQueueClick = onAddToQueueClick,
            onThirdActionClick = onThirdActionClick,
        )
    }
}

@Composable
private fun PlaylistTrackActionSheetContent(
    thirdActionText: String,
    thirdActionIconRes: Int,
    thirdActionPressedIconRes: Int,
    onDismiss: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onThirdActionClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(51.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.more_select_titlebar_bg),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
            androidx.compose.material3.Text(
                text = stringResource(R.string.select_action),
                style = PlaylistActionSheetTitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center),
            )
            PlaylistActionSheetCancelButton(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                onClick = onDismiss,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ActionSheetDivider),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.local_more_select_btn_bg),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
            )
            Image(
                painter = painterResource(R.drawable.more_select_titlebar_bg_shadow),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistTrackActionButton(
                    iconRes = R.drawable.more_select_icon_addlist,
                    pressedIconRes = R.drawable.more_select_icon_addlist_down,
                    text = stringResource(R.string.add_to_playlist),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    onClick = onAddToPlaylistClick,
                )
                PlaylistTrackActionDivider()
                PlaylistTrackActionButton(
                    iconRes = R.drawable.more_select_icon_addplay,
                    pressedIconRes = R.drawable.more_select_icon_addplay_down,
                    text = stringResource(R.string.add_to_queue),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    onClick = onAddToQueueClick,
                )
                PlaylistTrackActionDivider()
                PlaylistTrackActionButton(
                    iconRes = thirdActionIconRes,
                    pressedIconRes = thirdActionPressedIconRes,
                    text = thirdActionText,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    onClick = onThirdActionClick,
                )
            }
        }
    }
}

@Composable
private fun PlaylistPickerCreateRow(
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (pressed) Color(0xFFF0F0F0) else Color.White)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.new_playlist),
            style = PlaylistPickerTitleStyle,
        )
    }
}

@Composable
private fun PlaylistTrackActionButton(
    iconRes: Int,
    pressedIconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(top = 11.dp, bottom = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(if (pressed) pressedIconRes else iconRes),
            contentDescription = null,
            modifier = Modifier.height(24.dp),
        )
        Spacer(modifier = Modifier.height(5.dp))
        androidx.compose.material3.Text(
            text = text,
            style = PlaylistActionLabelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistTrackActionDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(ActionSheetDivider),
    )
}

@Composable
private fun PlaylistActionSheetCancelButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .width(67.dp)
            .height(34.dp)
            .background(
                if (pressed) DialogSecondaryPressedBackground else Color.White,
                RoundedCornerShape(8.dp),
            )
            .border(1.dp, DialogSecondaryBorder, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.cancel),
            style = DialogTextFieldStyle.copy(
                fontSize = 14.sp,
                color = DialogButtonText,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: UserPlaylistSummary,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) Color(0xFFF0F0F0) else Color.White)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        androidx.compose.material3.Text(
            text = playlist.name,
            style = PlaylistPickerTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.material3.Text(
            text = pluralStringResource(
                R.plurals.playlist_song_count,
                playlist.songCount,
                playlist.songCount,
            ),
            style = PlaylistPickerSubtitleStyle,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun DialogTextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(38.dp)
            .background(
                if (pressed) DialogSecondaryPressedBackground else Color.White,
                RoundedCornerShape(6.dp),
            )
            .border(1.dp, DialogSecondaryBorder, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = DialogTextFieldStyle.copy(
                color = DialogButtonText,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun DialogPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(38.dp)
            .background(
                if (pressed) DialogPrimaryPressedButton else DialogPrimaryButtonColor,
                RoundedCornerShape(6.dp),
            )
            .border(1.dp, DialogPrimaryBorder, RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = DialogTextFieldStyle.copy(
                color = Color.White,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
