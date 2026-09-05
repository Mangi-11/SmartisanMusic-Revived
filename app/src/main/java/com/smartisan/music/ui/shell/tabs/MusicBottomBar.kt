package com.smartisan.music.ui.shell.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import com.smartisan.music.R
import com.smartisan.music.ui.components.rememberSmartisanDrawablePainter
import com.smartisan.music.ui.components.smartisanPainterBackground
import com.smartisan.music.ui.navigation.MusicDestination

@Composable
internal fun MusicBottomBar(
    currentDestination: MusicDestination,
    destinations: List<MusicDestination>,
    onDestinationSelected: (MusicDestination) -> Unit,
    onEditRequested: () -> Unit,
    modifier: Modifier = Modifier,
    topChromeVisible: Boolean = true,
) {
    Box(
        modifier
            .fillMaxWidth()
            .smartisanPainterBackground(
                rememberSmartisanDrawablePainter(R.drawable.sb_repeat_tabbar_bg)
            )
    ) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(dimensionResource(R.dimen.smartisan_tabswitch_tabbar_height))
        ) {
            destinations.forEach { destination ->
                SmartisanBottomTabItem(
                    destination,
                    destination == currentDestination,
                    onClick = {
                        if (destination != currentDestination) onDestinationSelected(destination)
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onLongClick = onEditRequested,
                )
            }
        }
        if (topChromeVisible) {
            val shadow = rememberSmartisanDrawablePainter(R.drawable.tab_bar_shadow)
            val shadowHeight = with(LocalDensity.current) { shadow.intrinsicSize.height.toDp() }
            Spacer(
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(shadowHeight)
                    .smartisanPainterBackground(shadow)
            )
            Spacer(
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.nav_divider_height))
                    .background(colorResource(R.color.nav_list_line))
            )
        }
    }
}
