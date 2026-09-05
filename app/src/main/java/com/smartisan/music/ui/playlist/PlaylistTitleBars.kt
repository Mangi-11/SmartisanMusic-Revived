package com.smartisan.music.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.smartisan.music.R
import com.smartisan.music.ui.components.*
import com.smartisan.music.ui.shell.titlebar.TitleBarTransition

@Composable
internal fun PlaylistTitleArea(
    target: PlaylistTarget?,
    detailTitle: String,
    rootEditMode: Boolean,
    rootSelectedCount: Int,
    detailEditMode: Boolean,
    modifier: Modifier = Modifier,
    predictiveBackProgress: Float? = null,
    predictiveBackExitConsumed: Boolean = false,
    onPredictiveBackExitConsumedReset: (() -> Unit)? = null,
    onRootEnterEdit: () -> Unit,
    onRootExitEdit: () -> Unit,
    onRootDeleteSelected: () -> Unit,
    onRootBack: (() -> Unit)?,
    onDetailBack: () -> Unit,
    onDetailEnterEdit: () -> Unit,
    onDetailExitEdit: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val titleTarget = target?.copy(title = detailTitle.ifBlank { target.title })
    val titleAreaHeight =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            dimensionResource(R.dimen.title_bar_height)
    val cancel = stringResource(R.string.cancel)
    val back = stringResource(R.string.back)
    val edit = stringResource(R.string.edit)
    val done = stringResource(R.string.done)
    val search = stringResource(R.string.tab_local_search)
    val delete = stringResource(R.string.delete)
    val bar: @Composable (PlaylistTarget?, Boolean, Int, Boolean) -> Unit =
        { item, rootEditing, selectedCount, detailEditing ->
            val left =
                when {
                    item == null && rootEditing ->
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_cancel_selector,
                            cancel,
                            onRootExitEdit,
                        )
                    item == null && onRootBack != null ->
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_back_selector,
                            back,
                            onRootBack,
                        )
                    item == null ->
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_multi_select_selector,
                            edit,
                            onRootEnterEdit,
                        )
                    detailEditing ->
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_cancel_selector,
                            cancel,
                            onDetailExitEdit,
                        )
                    else ->
                        SmartisanTitleBarAction(
                            R.drawable.standard_icon_back_selector,
                            back,
                            onDetailBack,
                        )
                }
            val right =
                when {
                    item == null && rootEditing ->
                        listOf(
                            SmartisanTitleBarAction(
                                R.drawable.titlebar_btn_delete_selector,
                                delete,
                                onRootDeleteSelected,
                                enabled = selectedCount > 0,
                            )
                        )
                    item == null && onRootBack != null ->
                        listOf(
                            SmartisanTitleBarAction(
                                R.drawable.standard_icon_multi_select_selector,
                                edit,
                                onRootEnterEdit,
                            ),
                            SmartisanTitleBarAction(
                                R.drawable.search_btn_selector,
                                search,
                                onSearchClick,
                            ),
                        )
                    item == null ->
                        listOf(
                            SmartisanTitleBarAction(
                                R.drawable.search_btn_selector,
                                search,
                                onSearchClick,
                            )
                        )
                    detailEditing ->
                        listOf(
                            SmartisanTitleBarAction(
                                R.drawable.standard_icon_hignlight_confirm_selector,
                                done,
                                onDetailExitEdit,
                            )
                        )
                    else ->
                        listOf(
                            SmartisanTitleBarAction(
                                R.drawable.standard_icon_multi_select_selector,
                                edit,
                                onDetailEnterEdit,
                            )
                        )
                }
            SmartisanTitleBar(
                item?.title ?: stringResource(R.string.tab_play_list),
                navigationIcon = left,
                actions = right,
                modifier = Modifier.fillMaxSize(),
            )
        }
    TitleBarTransition(
        titleTarget,
        modifier.fillMaxWidth().height(titleAreaHeight),
        label = "playlist title transition",
        predictiveBackProgress = predictiveBackProgress,
        predictiveBackExitConsumed = predictiveBackExitConsumed,
        onPredictiveBackExitConsumedReset = onPredictiveBackExitConsumedReset,
        primaryContent = { bar(null, rootEditMode, rootSelectedCount, false) },
        secondaryContent = { bar(it, false, 0, detailEditMode) },
    )
}

@Composable
internal fun PlaylistAddModeTitleArea(
    target: PlaylistTarget,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(colorResource(R.color.title_bar_background))) {
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
        Box(
            Modifier.fillMaxWidth().height(dimensionResource(R.dimen.status_bar_height)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                stringResource(R.string.add_track_to) + " \"${target.title.ellipsizeMiddle(8)}\"",
                style =
                    TextStyle(
                        color = colorResource(R.color.title_color),
                        fontSize = smartisanTextSize(R.dimen.text_size_act_title),
                        platformStyle = PlatformTextStyle(includeFontPadding = true),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SmartisanTitleBar(
            stringResource(R.string.name_tracks),
            includeStatusBar = false,
            action =
                SmartisanTitleBarAction(
                    R.drawable.standard_icon_hignlight_confirm_selector,
                    stringResource(R.string.done),
                    onConfirm,
                ),
        )
    }
}
