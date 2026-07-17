package com.smartisan.music.ui.shell.titlebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.smartisan.music.R
import com.smartisan.music.ui.widgets.legacy.TitleBar

@Composable
internal fun LegacyPortSmartisanTitleBar(
    modifier: Modifier = Modifier,
    includeStatusBar: Boolean = true,
    showShadow: Boolean = false,
    update: (TitleBar) -> Unit,
) {
    val titleContentHeight = dimensionResource(R.dimen.title_bar_height)
    val shadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
    Column(
        modifier = modifier
            .then(if (showShadow) Modifier.zIndex(1f) else Modifier)
            .fillMaxWidth()
            .background(ComposeColor.White),
    ) {
        if (includeStatusBar) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleContentHeight),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TitleBar(context).apply {
                        setShadowVisible(false)
                    }
                },
                update = { titleBar ->
                    titleBar.setShadowVisible(false)
                    update(titleBar)
                },
            )
            if (showShadow) {
                LegacyPortTitleBarShadow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = shadowHeight)
                        .fillMaxWidth()
                        .height(shadowHeight),
                )
            }
        }
    }
}

@Composable
internal fun LegacyPortTitleBarShadow(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.view.View(context).apply {
                setBackgroundResource(R.drawable.title_bar_shadow)
            }
        },
    )
}
