package com.smartisan.music.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.smartisan.music.R
import com.smartisan.music.ui.components.*

@Composable
internal fun SmartisanSongsSortHeader(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedNow by rememberUpdatedState(selected)
    val onSelectedNow by rememberUpdatedState(onSelected)
    Box(modifier.zIndex(1f)) {
        Row(
            Modifier.fillMaxWidth()
                .height(dimensionResource(R.dimen.secondary_bar_height))
                .background(colorResource(R.color.surface_card))
                .padding(horizontal = dimensionResource(R.dimen.button_group_left_right_padding)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                    R.string.sort_by_song_name,
                    R.string.sort_by_song_score,
                    R.string.sort_by_song_play_time,
                    R.string.sort_by_song_update_time,
                )
                .forEachIndexed { index, text ->
                    val activated = selected == index
                    val resource =
                        when (index) {
                            0 -> R.drawable.selector_small_btn_filter_left
                            3 -> R.drawable.selector_small_btn_filter_right
                            else -> R.drawable.selector_small_btn_filter_middle
                        }
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .smartisanPainterBackground(
                                rememberSmartisanDrawablePainter(resource, activated = activated)
                            )
                            .semantics { this.selected = activated }
                            .pointerInput(index) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    if (selectedNow != index) onSelectedNow(index)
                                }
                            }
                            .clickable(
                                remember { MutableInteractionSource() },
                                null,
                                role = Role.Tab,
                                onClick =
                                    smartisanClick {
                                        if (selectedNow != index) onSelectedNow(index)
                                    },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            stringResource(text),
                            style =
                                TextStyle(
                                    fontSize = 13.5.sp,
                                    color =
                                        smartisanStateColor(
                                            R.color.filter_button_text_color,
                                            activated = activated,
                                        ),
                                    shadow =
                                        Shadow(
                                            smartisanStateColor(
                                                R.color.filter_button_text_shadow_colors,
                                                activated = activated,
                                            ),
                                            Offset(0f, -2f),
                                            .1f,
                                        ),
                                    textAlign = TextAlign.Center,
                                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                                ),
                            maxLines = 1,
                        )
                    }
                }
        }
        val painter = rememberSmartisanDrawablePainter(R.drawable.smartisan_secondary_bar_shadow)
        val height =
            with(androidx.compose.ui.platform.LocalDensity.current) {
                painter.intrinsicSize.height.toDp()
            }
        Spacer(
            Modifier.align(Alignment.BottomCenter)
                .offset(y = height)
                .fillMaxWidth()
                .height(height)
                .smartisanPainterBackground(painter)
        )
    }
}
